/*
 *  Copyright (c) 2022-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.web;

import androidx.annotation.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.twinlife.twinlife.AccountService.AuthenticationAuthority;
import org.twinlife.twinlife.RepositoryObjectFactory;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.Twinlife;
import org.twinlife.web.models.TwincodeFactoryPoolFactory;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Properties;

/**
 * Twinlife configuration to be used for the web application proxy.
 */
public class ProxyConfiguration extends Twinlife.TwinlifeConfiguration {
    static final Logger Log = LogManager.getLogger(ProxyConfiguration.class);

    static final String PARAM_PORT = "port";
    static final String PARAM_THREADS = "threads";
    static final String PARAM_SERVER = "server";
    static final String PARAM_SERVICE = "service";
    static final String PARAM_APPLICATION = "application";
    static final String PARAM_API_KEY = "apiKey";
    static final String PARAM_DATA_DIR = "datadir";
    static final String PARAM_APPLICATION_NAME = "applicationName";
    static final String PARAM_APPLICATION_COUNT = "applicationCount";
    static final String PARAM_ENCRYPTION_PASSWORD = "applicationPassword";
    static final String PARAM_ENCRYPTION_SALT = "applicationSalt";
    static final String PARAM_ENCRYPTION_ITER = "applicationIterCount";

    static final String PARAM_IP_LOCAL_ADDR = "ip.localAddresses";
    static final String PARAM_KAFKA_BOOTSTRAP_SERVERS = "kafka.bootstrapServers";
    static final String PARAM_KAFKA_CLIENT_ID = "kafka.clientId";
    static final String PARAM_KAFKA_TOPIC = "kafka.topic";
    static final String PARAM_KAFKA_TOPIC_PREFIX = "kafka.topicPrefix";
    static final String PARAM_KAFKA_SECURITY_PROTOCOL = "kafka.securityProtocol";
    static final String PARAM_KAFKA_QUEUE_LENGTH = "kafka.senderQueueLength";
    static final String PARAM_KAFKA_CLOSE_DELAY = "kafka.closeDelay";
    static final String PARAM_KAFKA_PROPERTIES = "kafka.properties";

    private static final String KAFKA_DEFAULT_TOPIC_NAME = "twinapp-logs";
    private static final String KAFKA_DEFAULT_CLIENT_ID = "TwinappProducer";

    public final int port;
    public final int threads;
    public final int appCount;
    public final String server;
    public final String dataDir;
    private final String kafkaBootstrapServers;
    private final int kafkaSenderQueueLength;
    private final int kafkaSenderCloseDelay;
    private final String kafkaSecurityProtocol;
    private final String kafkaPropertyFilename;
    private final String kafkaTopic;
    private final String kafkaTopicPrefix;
    private final String kafkaClientId;
    private final String localIpAddresses;
    private final SecretKey secretKey;

    public ProxyConfiguration(@NonNull Properties config) throws Exception {
        this.apiKey = config.getProperty(PARAM_API_KEY);
        this.serviceId = config.getProperty(PARAM_SERVICE);
        this.applicationId = config.getProperty(PARAM_APPLICATION);
        this.applicationName = config.getProperty(PARAM_APPLICATION_NAME, "webapp");
        this.certificateSerialNumber = "";
        this.port = Integer.parseInt(config.getProperty(PARAM_PORT, "8080"));
        this.threads = Integer.parseInt(config.getProperty(PARAM_THREADS, "1024"));
        this.appCount = Integer.parseInt(config.getProperty(PARAM_APPLICATION_COUNT, "1"));
        this.server = config.getProperty(PARAM_SERVER);
        this.dataDir = config.getProperty(PARAM_DATA_DIR);

        // Get Kafka producer parameters
        this.kafkaBootstrapServers = config.getProperty(PARAM_KAFKA_BOOTSTRAP_SERVERS, "");
        this.kafkaClientId = config.getProperty(PARAM_KAFKA_CLIENT_ID, KAFKA_DEFAULT_CLIENT_ID);
        this.kafkaSenderQueueLength = Integer.parseInt(config.getProperty(PARAM_KAFKA_QUEUE_LENGTH, "10000"));
        this.kafkaSenderCloseDelay = Integer.parseInt(config.getProperty(PARAM_KAFKA_CLOSE_DELAY, "2000"));
        this.kafkaSecurityProtocol = config.getProperty(PARAM_KAFKA_SECURITY_PROTOCOL);
        this.kafkaPropertyFilename = config.getProperty(PARAM_KAFKA_PROPERTIES, "");
        this.kafkaTopic = config.getProperty(PARAM_KAFKA_TOPIC, KAFKA_DEFAULT_TOPIC_NAME);
        this.kafkaTopicPrefix = config.getProperty(PARAM_KAFKA_TOPIC_PREFIX, "");

        // Get list of local IP addresses / subnets. The expected value is a space separated list of IP addresses
        // or subnets (like '193.93.124.0/24' )
        this.localIpAddresses = config.getProperty(PARAM_IP_LOCAL_ADDR, "");

        serializers = new Serializer[0];

        accountServiceConfiguration.defaultAuthenticationAuthority = AuthenticationAuthority.DEVICE;
        repositoryServiceConfiguration.serviceOn = true;
        twincodeFactoryServiceConfiguration.serviceOn = true;
        twincodeInboundServiceConfiguration.serviceOn = true;
        twincodeOutboundServiceConfiguration.serviceOn = true;
        imageServiceConfiguration.serviceOn = true;
        factories = new RepositoryObjectFactory[1];
        factories[0] = TwincodeFactoryPoolFactory.INSTANCE;

        // Build the encryption key to encrypt/decrypt the secure configuration.
        // For the webapp proxy, the secure configuration only contains the account
        // identifier and password used to connect to the signaling server.
        final String password = config.getProperty(PARAM_ENCRYPTION_PASSWORD);
        final String salt = config.getProperty(PARAM_ENCRYPTION_SALT);
        if (password == null || password.isEmpty()) {
            Log.error("Parameter '{}' is not defined or empty", PARAM_ENCRYPTION_PASSWORD);
            throw new IllegalStateException("missing " + PARAM_ENCRYPTION_PASSWORD);
        }
        if (salt == null || salt.isEmpty()) {
            Log.error("Parameter '{}' is not defined or empty", PARAM_ENCRYPTION_SALT);
            throw new IllegalStateException("missing " + PARAM_ENCRYPTION_SALT);
        }
        int iterationCount = Integer.parseInt(config.getProperty(PARAM_ENCRYPTION_ITER, "256000"));
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), iterationCount, 128);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        secretKey = factory.generateSecret(spec);
    }

    void setApplicationVersion(@NonNull String applicationVersion) {

        this.applicationVersion = applicationVersion;
    }

    SecretKey getSecretKey() {

        return secretKey;
    }

    /**
     * Get the list (comma separated names) of Kafka bootstrap server names/address
     */
    public String getKafkaBootstrapServers() {
        return kafkaBootstrapServers;
    }

    /**
     * Get the client identifier name list (comma separated names) of Kafka bootstrap server names/address
     * <p>
     * This name is useful to track the source of requests beyond just ip/port by allowing
     * a logical application name to be included in server-side request logging
     * </p>
     */
    public String getKafkaClientId() {
        return kafkaClientId;
    }

    public int getKafkaSenderQueueLength() {
        return kafkaSenderQueueLength;
    }

    public int getKafkaSenderCloseDelay() {
        return kafkaSenderCloseDelay;
    }

    public String getKafkaSecurityProtocol() {
        return kafkaSecurityProtocol;
    }

    public String getKafkaPropertyFilename() {
        return kafkaPropertyFilename;
    }

    public String getKafkaTopic() {
        return kafkaTopic;
    }

    public String getKafkaTopicPrefix() {
        return kafkaTopicPrefix;
    }

    public String getLocalIpAddresses() {
        return localIpAddresses;
    }
}
