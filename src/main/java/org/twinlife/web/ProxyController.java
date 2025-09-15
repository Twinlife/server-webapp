/*
 *  Copyright (c) 2022-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Laurent Latil (Laurent.Latil@twin.life)
 */

package org.twinlife.web;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.twinlife.twinlife.PropertiesConfigurationServiceImpl;

import com.j256.simplejmx.common.JmxAttributeMethod;
import com.j256.simplejmx.common.JmxResource;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.twinlife.web.kafka.RecordSender;
import org.twinlife.web.kafka.records.ClickToCallRecord;

/**
 * Proxy controller to manage connections to the Openfire server.
 *
 * <p>
 * The current implementation uses only a single {@link ProxyApplication} but
 * it is intended to provide and use a pool of ProxyApplications.
 * </p>
 */
@JmxResource(domainName = "org.twinlife", description = "Main proxy controller managing connections to Openfire server")
public final class ProxyController {
    private static final int MAX_APP_COUNT = 1000;
    static final long CLEANER_PERIOD = 150000; // 150s
    static final long MAX_CLIENT_IDLE_DELAY = 2 * CLEANER_PERIOD;

    private static final Logger Log = LogManager.getLogger(ProxyController.class);

    private static ProxyController instance;

    private final ProxyApplication[] mProxyApplication;
    private final AtomicInteger mProxyIndex = new AtomicInteger();
    @Nullable
    private final RecordSender<String, ClickToCallRecord > mKafkaRecordSender;
    private final ConcurrentHashMap<String, ClientSession> mClients = new ConcurrentHashMap<>();
    @SuppressWarnings("unchecked")
    private final ConcurrentHashMap<String, ClientSession>[] mCheckExpired = new ConcurrentHashMap[3];
    private final ScheduledExecutorService mCleanerExecutor = Executors.newSingleThreadScheduledExecutor();
    @Nullable
    private ScheduledFuture<?> mExpiredCleaner;
    private volatile int mCheckExpiredListIndex;

    /**
     * Initialize the unique instance proxy controller.
     *
     * @param kafkaRecordSender Record sender instance to use to publish Kafka records (<code>null</code> if Kafka support
     *                          not enabled)
     * @throws IllegalArgumentException If some configuration values are invalid
     * @throws IllegalStateException If the unique instance proxy controller has already been initialized
     * @see ProxyController#getInstance()
     */
    public static synchronized void initialize(@NonNull ProxyConfiguration proxyConfiguration,
                                               @Nullable RecordSender<String, ClickToCallRecord> kafkaRecordSender) {
        if (instance != null) {
            throw new IllegalStateException("Proxy controller have already been initialized");
        }
        instance = new ProxyController(proxyConfiguration, kafkaRecordSender);
    }

    /**
     * Get the proxy application to make operations on the Openfire server.
     *
     * @return the proxy application or null if we have resource issues.
     */
    @NonNull
    public static ProxyApplication getProxyApplication() {
        ProxyController ctrl = ProxyController.getInstance();

        // Round robin dispatch to one of the available ProxyApplication instance.
        while (true) {
            final int counter = ctrl.mProxyIndex.incrementAndGet();
            if (counter <= ctrl.mProxyApplication.length) {
                return ctrl.mProxyApplication[counter - 1];
            }
            ctrl.mProxyIndex.compareAndSet(counter, 0);
        }
    }

    /**
     * Get the {@link ProxyController} unique instance
     *
     * @return ProxyController instance
     */
    public static synchronized ProxyController getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Proxy controller has not been initialized yet");
        }
        return instance;
    }

    /**
     * Create or get the client session associated with the session id.
     * If the client session is already known, its instance is returned.
     * Otherwise, we create and register it.  The client session id
     * must start with `id-` and be long enough and generated randomly
     * (the typescript generates 85 characters using the Base64 alphabet)
     *
     * @param sessionId the client session id.
     * @return the new client id.
     */
    @NonNull
    public static ClientSession createClient(@NonNull String sessionId) {
        Log.info("Create client session {}", sessionId);

        final ProxyController controller = getInstance();
        ClientSession session = controller.mClients.get(sessionId);
        if (session == null) {
            session = new ClientSession(sessionId, controller.mKafkaRecordSender);
            controller.mClients.put(sessionId, session);
        } else {
            // This session is known, remove it from the check expiration lists.
            for (int i = 0; i < 3; i++) {
                controller.mCheckExpired[i].remove(sessionId);
            }
        }
        return session;
    }

    /**
     * Release the client session after WebSocket close.  When release is set, we can
     * forget this client session.  Otherwise, we must keep in case the browser reconnects.
     * Meanwhile, it is put in the check expiration list that will handle its removal
     * in 2xMAX_CLIENT_IDLE_DELAY (=10mn).
     *
     * @param session the client session.
     * @param release true if we can forget that client immediately.
     */
    public static void releaseClient(@NonNull ClientSession session, boolean release) {
        Log.info("Release client session {} release {}", session, release);

        final ProxyController controller = getInstance();
        if (release) {
            controller.mClients.remove(session.getSessionId());
        } else {
            // This web client is probably just disconnected and may try to reconnect.
            // Put it in the current expiration list.
            controller.mCheckExpired[controller.mCheckExpiredListIndex].put(session.getSessionId(), session);
        }
    }

    //
    // JMX attributes
    //

    @JmxAttributeMethod(description = "Total count of clients managed by this controller since startup")
    public long getCreatedClientCount() {
        return Arrays.stream(mProxyApplication).map(ProxyApplication::getCreatedClientCount).reduce(0L, (total, v) -> total + v);
    }

    @JmxAttributeMethod(description = "Number of active rooms currently managed by this controller")
    public long getCallRoomNumber() {
        return Arrays.stream(mProxyApplication).map(ProxyApplication::getActiveCallRoomCount).reduce(0, (total, v) -> total + v);
    }

    @JmxAttributeMethod(description = "Number of active P2P sessions currently managed by this controller")
    public long getActiveSessionsNumber() {
        return Arrays.stream(mProxyApplication).map(ProxyApplication::getActiveSessionCount).reduce(0, (total, v) -> total + v);
    }

    @JmxAttributeMethod(description = "Number of twincodes available in all pools")
    public long getTwincodePoolCountNumber() {
        return Arrays.stream(mProxyApplication).map(ProxyApplication::getTwincodePoolCount).reduce(0, (total, v) -> total + v);
    }

    @JmxAttributeMethod(description = "Number of proxy application instances managed by this controller")
    public int getProxyApplicationNumber() {
        return mProxyApplication.length;
    }

    /**
     * Constructs a new {@link ProxyController} instance
     * <p>
     * Note: This ctor is private to prevent building multiple proxy controllers
     * </p>
     *
     * @param proxyConfiguration Configuration parameters
     * @param kafkaRecordSender Record sender instance to use to publish Kafka records (<code>null</code> if Kafka support
     *                          not enabled)
     */
    private ProxyController(@NonNull ProxyConfiguration proxyConfiguration,
                            @Nullable RecordSender<String, ClickToCallRecord> kafkaRecordSender) {
        Log.info("Starting proxy webapp client");

        mKafkaRecordSender = kafkaRecordSender;
        mCheckExpired[0] = new ConcurrentHashMap<>();
        mCheckExpired[1] = new ConcurrentHashMap<>();
        mCheckExpired[2] = new ConcurrentHashMap<>();
        mCheckExpiredListIndex = 0;
        mExpiredCleaner = mCleanerExecutor.scheduleAtFixedRate(this::cleanExpiredSession,
                CLEANER_PERIOD, CLEANER_PERIOD, TimeUnit.MILLISECONDS);

        final File rootDir = new File(proxyConfiguration.dataDir);
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            final String msg = String.format("Cannot create data directory %s", rootDir);
            Log.error("msg");
            throw new IllegalArgumentException(msg);
        }

        // Get the application version from the webapp.properties file in the JAR.
        PropertiesConfigurationServiceImpl s = new PropertiesConfigurationServiceImpl("webapp", rootDir, proxyConfiguration.getSecretKey());
        proxyConfiguration.setApplicationVersion(s.getApplicationVersion());

        if (proxyConfiguration.appCount <= 0 || proxyConfiguration.appCount > MAX_APP_COUNT) {
            final String msg = String.format("Invalid configuration %s: value %d is out of range 1..%d",
                    ProxyConfiguration.PARAM_APPLICATION_COUNT, proxyConfiguration.appCount, MAX_APP_COUNT);
            Log.error(msg);
            throw new IllegalArgumentException(msg);
        }

        Log.info("Creating {} proxy applications", proxyConfiguration.appCount);
        mProxyApplication = new ProxyApplication[proxyConfiguration.appCount];
        for (int i = 0; i < proxyConfiguration.appCount; i++) {
            final File dir = new File(rootDir, "client-" + (i+1));
            mProxyApplication[i] = new ProxyApplication(proxyConfiguration, dir);
        }
    }

    private void cleanExpiredSession() {
        Log.debug("Clean expired sessions");

        final int toClean = (mCheckExpiredListIndex + 1) % 3;
        final ConcurrentHashMap<String, ClientSession> cleanMap = mCheckExpired[toClean];
        final long count = cleanMap.size();
        if (count > 0) {
            long cleanCount = 0;
            for (ConcurrentHashMap.Entry<String, ClientSession> item : cleanMap.entrySet()) {
                final String sessionId = item.getKey();
                final ClientSession session = item.getValue();
                if (session.isExpired()) {
                    mClients.remove(sessionId);
                    session.dispose();
                    cleanCount++;
                } else {
                    Log.warn("Session {} has not expired and is in the expiration list!", sessionId);
                }
            }
            cleanMap.clear();
            if (cleanCount > 0) {
                Log.info("Cleaned {} client sessions", cleanCount);
            }
        }

        // The current terminated list is now the list we have just cleaned.
        mCheckExpiredListIndex = toClean;
    }
}
