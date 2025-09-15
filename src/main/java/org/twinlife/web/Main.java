/*
 *  Copyright (c) 2022-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import javax.management.JMException;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.eclipse.jetty.io.IdleTimeout;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.Scheduler;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.glassfish.jersey.servlet.ServletContainer;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.twinlife.web.kafka.KafkaRecordSerializer;
import org.twinlife.web.kafka.RecordSender;
import org.twinlife.web.kafka.records.ClickToCallRecord;
import org.twinlife.web.util.ClientAddressFinder;

import com.j256.simplejmx.common.JmxResource;
import com.j256.simplejmx.server.JmxServer;

public class Main {
    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_FAILURE = 1;
    private static final int EXIT_CODE_INVALID_ARGS = 2;
    private static final int EXIT_CODE_BIND_ERROR = 3;

    private static final Logger Log = LogManager.getLogger(Main.class);

    private static final int DEFAULT_MAX_THREADS = 1000;

    @Option(name = "--help", aliases = "-h", help = true, usage = "Print usage help")
    private boolean printHelp = false;

    @Option(name = "--config", aliases = "-c", required = true, usage = "Path to the configuration file")
    private File configFile;

    private final Properties mSettings = new Properties();

    private void printUsage(CmdLineParser parser) {
        System.out.println("\nUsage: proxy [options...]\n");

        // Display available options
        parser.printUsage(System.out);
    }

    private boolean parseOptions(String[] args) {
        CmdLineParser parser = new CmdLineParser(this);

        try {
            // parse the arguments.
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            return false;
        }

        if (printHelp) {
            printUsage(parser);
            return false;
        }

        if (!configFile.canRead()) {
            Log.error("Configuration file {} cannot be read", configFile.getAbsolutePath());
            return false;
        }

        try (InputStream file = new FileInputStream(configFile)) {
            mSettings.load(file);

        } catch (IOException ex) {
            Log.error("Cannot load {}: {}",configFile, ex.getMessage());
        }

        return true;
    }

    private static String createKafkaTopicName(ProxyConfiguration proxyConfiguration) {
        final String baseName = proxyConfiguration.getKafkaTopic();
        final String prefix = proxyConfiguration.getKafkaTopicPrefix();

        final String name = prefix.isBlank() ? baseName : prefix + "-" + baseName;

        Log.info("Publish records on topic '{}'", name);
        return name;
    }

    /**
     * Creates a 'record sender' to publish records on Kafka bus.
     *
     * @return The RecordSender instance or <code>null</code> if Kafka support is not enabled in config.
     */
    private RecordSender<String, ClickToCallRecord> createRecordSender(ProxyConfiguration proxyConfiguration) {
        final String bootstrapServers = proxyConfiguration.getKafkaBootstrapServers();
        if (bootstrapServers.isEmpty()) {
            return null;
        }

        final int senderQueueLength = proxyConfiguration.getKafkaSenderQueueLength();
        final int closeDelay = proxyConfiguration.getKafkaSenderCloseDelay();

        final Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.CLIENT_ID_CONFIG, proxyConfiguration.getKafkaClientId());

        final String securityProtocol = proxyConfiguration.getKafkaSecurityProtocol();

        if (!securityProtocol.isEmpty()) {
            // Check that the specified security protocol is actually supported
            if (SecurityProtocol.names().contains(securityProtocol)) {
                properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);
            } else {
                final String err = String.format("Security protocol '%s' invalid in kafka configuration",
                        securityProtocol);

                Log.error(err);
                throw new RuntimeException(err);
            }
        }

        // Include the additional properties possibly defined in the 'properties' file
        final String propertiesFileOption = proxyConfiguration.getKafkaPropertyFilename();

        if (!propertiesFileOption.isBlank()) {
            try (InputStream fis = new FileInputStream(propertiesFileOption)) {
                properties.load(fis);
            } catch (IOException e) {
                final String err = String.format("Cannot read 'properties' file '%s': %s",
                        propertiesFileOption, e.getMessage());

                System.err.println(err);
                Log.error(err);
                System.exit(EXIT_CODE_INVALID_ARGS);
            }
        }

        final KafkaRecordSerializer serializer = new KafkaRecordSerializer();
        final KafkaProducer<String, ClickToCallRecord> kafkaProducer = new KafkaProducer<>(properties,
                new StringSerializer(), serializer);
        Log.info("Kafka producer uses following properties:");
        Log.info(properties.toString());

        Log.debug("Kafka record sender created. Queue length: {}, close delay (milliseconds): {}", senderQueueLength,
                closeDelay);
        return new RecordSender<>(kafkaProducer, createKafkaTopicName(proxyConfiguration),
                senderQueueLength, closeDelay);
    }

    /**
     * Create {@link JmxServer} instance and registers the class annotated with {@link JmxResource}
     *
     * @return The created instance (or <code>null</code> if JMX support is disabled)
     */
    private JmxServer createJmxServer() {
        final JmxServer jmxServer = new JmxServer(ManagementFactory.getPlatformMBeanServer());

        try {
            jmxServer.register(ProxyController.getInstance());
        } catch (JMException e) {
            Log.error("Cannot create the JMX server instance", e);
            throw new RuntimeException(e);
        }
        return jmxServer;
    }

    private int run() {
        int exitCode = EXIT_CODE_SUCCESS;

        // Max threads
        int maxThreads = DEFAULT_MAX_THREADS;

        final ProxyConfiguration proxyConfiguration;
        try {
            proxyConfiguration = new ProxyConfiguration(mSettings);

        } catch (Exception ex) {
            Log.error("Fatal error when reading the configuration file: {}", ex.getMessage());
            Log.error("Exception:", ex);
            return EXIT_CODE_FAILURE;
        }
        final RecordSender<String, ClickToCallRecord> kafkaRecordSender = createRecordSender(proxyConfiguration);
        try {
            ProxyController.initialize(proxyConfiguration, kafkaRecordSender);
        } catch (IllegalArgumentException e) {
            Log.error(e.getMessage());
            return EXIT_CODE_FAILURE;
        }

        final JmxServer jmxServer = createJmxServer();

        QueuedThreadPool queuedThreadPool = new QueuedThreadPool(maxThreads);
        Server server = new Server(queuedThreadPool);

        HttpConfiguration configuration = new HttpConfiguration();
        configuration.setSendDateHeader(false);
        configuration.setSendServerVersion(false);

        ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(configuration));

        final String host = "";

        connector.setHost(host);

        server.addConnector(connector);// Add a custom error page.
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        ServletHolder restServlet = context.addServlet(ServletContainer.class, "/rest/*");
        restServlet.setInitOrder(1);
        restServlet.setAsyncSupported(true);
        restServlet.setInitParameter("jersey.config.server.provider.packages",
                                "org.twinlife.web.rest;org.codehaus.jackson.jaxrs");
        restServlet.setInitParameter("jersey.config.server.logging.verbosity", "PAYLOAD_TEXT");
        restServlet.setInitParameter("jersey.config.server.trace", "ALL");
        restServlet.setInitParameter("jersey.config.server.provider.classnames",
                "org.glassfish.jersey.jackson.JacksonFeature;"
                        + "org.glassfish.jersey.media.multipart.MultiPartFeature;"
                        + "org.glassfish.jersey.logging.LoggingFeature");
        restServlet.setInitParameter("plop.javax.ws.rs.Application",
                "org.twinlife.web.rest.Application");

        // Create the single instance of ClientAddressFinder configured with the list of local IP addresses from config
        final ClientAddressFinder addressFinder = new ClientAddressFinder(proxyConfiguration.getLocalIpAddresses());

        // Initialize javax.websocket layer
        JettyWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) ->
        {
            // This lambda will be called at the appropriate place in the
            // ServletContext initialization phase where you can initialize
            // and configure  your websocket container.

            // Configure defaults for container
            wsContainer.setMaxTextMessageSize(65535);

            // Add WebSocket endpoint to javax.websocket layer
            wsContainer.addMapping("/p2p/*", (req, resp) -> {
                Log.debug("upgrade request {}", req);

                return new WebSocketClientSession(addressFinder);
            });
        });
        Scheduler scheduler = new ScheduledExecutorScheduler();
        IdleTimeout idleTimeout;

        try {
            scheduler.start();

            idleTimeout = new IdleTimeout(scheduler) {
                @Override
                protected void onIdleExpired(TimeoutException timeout) {
                    Log.info("queuedThreadPool isLowOnThreads={}, busyThreads={}/{} connectedEndPoints={}",
                            queuedThreadPool.isLowOnThreads(), queuedThreadPool.getBusyThreads(),
                            queuedThreadPool.getMaxThreads(), connector.getConnectedEndPoints().size());
                }

                @Override
                public boolean isOpen() {
                    return true;
                }
            };

            // 5 min
            idleTimeout.setIdleTimeout(5 * 60 * 1000);

            ErrorHandler errorHandler = new ErrorHandler() {

            };
            server.setErrorHandler(errorHandler);

            server.start();
            Log.info("Proxy started on TCP port {}", connector.getPort());
        } catch (java.net.BindException e) {
            Log.error("Cannot bind to network IP address / port specified in configuration", e);
            exitCode = EXIT_CODE_BIND_ERROR;
        } catch (Exception e) {
            Log.error("Error during proxy execution", e);
            exitCode = EXIT_CODE_FAILURE;
        }

        try {
            server.join();
        } catch (InterruptedException e) {
            Log.warn("Main thread interrupted. Stop the proxy service");
        }

        try {
            jmxServer.close();
        } catch (IOException e) {
            // Cannot really do better than report the error
            Log.error(e);
        }
        return exitCode;
    }

    private void start(String[] args) {
        if (!parseOptions(args)) {
            System.exit(EXIT_CODE_INVALID_ARGS);
            return;
        }

        int exitCode = run();
        System.exit(exitCode);
    }

    public static void main(String[] args) throws Exception {
        final Main server = new Main();

        Log.info("Start web server");
        server.start(args);
    }
}
