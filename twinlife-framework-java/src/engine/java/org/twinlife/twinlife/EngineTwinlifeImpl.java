/*
 *  Copyright (c) 2019-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.content.Context;
import androidx.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Engine twinlife implementation.
 */
public abstract class EngineTwinlifeImpl extends TwinlifeImpl implements Runnable {
    private static final String LOG_TAG = "EngineTwinlifeImpl";
    private static final boolean INFO = false;
    private static final boolean DEBUG = false;

    private static final String RESOURCE = "engine";
    private static final int MIN_CONNECTED_TIMEOUT = 64; // s
    private static final int MAX_CONNECTED_TIMEOUT = 1024; // s
    private static final int NO_RECONNECTION_TIMEOUT = 0; // ms
    private static final int MIN_RECONNECTION_TIMEOUT = 1000; // ms
    private static final int MAX_RECONNECTION_TIMEOUT = 8000; // ms

    @NonNull
    private final File mFilesDir;
    @NonNull
    private final File mCacheDir;

    private final ConfigurationService mConfigurationService;

    private final ReentrantLock mConnectedLock = new ReentrantLock();
    private final Condition mConnectedCondition = mConnectedLock.newCondition();
    private volatile int mConnectedTimeout = MIN_CONNECTED_TIMEOUT;
    private final ReentrantLock mReconnectionLock = new ReentrantLock();
    private final Condition mReconnectionCondition = mReconnectionLock.newCondition();
    private volatile int mReconnectionTimeout = NO_RECONNECTION_TIMEOUT;
    @NonNull
    private final ImageTools mImageTools;
    private final JobService mJobServiceImpl;

    public EngineTwinlifeImpl(Context context, ConfigurationService configurationService,
                              @NonNull TwinlifeContextImpl twinlifeContext,
                              @NonNull File filesDir, @NonNull File cacheDir,
                              @NonNull ImageTools imageTools) {
        super(context, twinlifeContext.mTwinlifeExecutor);

        if (DEBUG) {
            Log.d(LOG_TAG, "EngineTwinlifeImpl");
        }

        mWebRtcReady = true;
        mConfigurationService = configurationService;
        mFilesDir = filesDir;
        mCacheDir = cacheDir;
        mImageTools = imageTools;
        mJobServiceImpl = twinlifeContext.getJobService();
    }

    @Override
    @NonNull
    public JobService getJobService() {

        return mJobServiceImpl;
    }

    @Override
    @NonNull
    public ConfigurationService getConfigurationService() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConfigurationService");
        }

        return mConfigurationService;
    }

    //
    // Override Service methods
    //

    @Override
    @NonNull
    public File getFilesDir() {

        return mFilesDir;
    }

    @Override
    @NonNull
    public File getCacheDir() {

        return mCacheDir;
    }

    @NonNull
    @Override
    public String getResource() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getResource");
        }

        return RESOURCE + getDeviceIdentifier();
    }

    @Override
    @NonNull
    public PackageInfo getPackageInfo() {
        PackageInfo packageInfo = new PackageInfo();

        packageInfo.installerName = "twinme-installer";
        packageInfo.packageName = "twinme-engine";
        packageInfo.versionCode = BuildConfig.VERSION_NAME;
        packageInfo.versionName = BuildConfig.VERSION_NAME;
        return packageInfo;
    }

    public void onCreate() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreate");
        }

        super.onCreate();

        mRunning = true;
    }

    @Override
    public void connect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "connect");
        }

        if (isConfigured() && !isConnected()) {
            mReconnectionTimeout = NO_RECONNECTION_TIMEOUT;
            mConnectedTimeout = MIN_CONNECTED_TIMEOUT;

            mConnectedLock.lock();
            mConnectedCondition.signalAll();
            mConnectedLock.unlock();

            mReconnectionLock.lock();
            mReconnectionCondition.signalAll();
            mReconnectionLock.unlock();
        }
    }

    //
    // Protected Methods
    //

    @Override
    protected String getFingerprint(String serial) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getFingerprint");
        }

        return "engine-fingerprint";
    }

    @Override
    public void onDisconnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDisconnect");
        }

        super.onDisconnect();

        mConnectedLock.lock();
        mConnectedCondition.signalAll();
        mConnectedLock.unlock();

        mReconnectionLock.lock();
        mReconnectionCondition.signalAll();
        mReconnectionLock.unlock();
    }

    @Override
    protected void start() {
        if (INFO) {
            Log.i(LOG_TAG, "start");
        }

        Thread twinlifeThread = new Thread(this);
        twinlifeThread.setName("twinlife-connect");
        twinlifeThread.start();
    }

    public void stop() {
        if (INFO) {
            Log.i(LOG_TAG, "stop");
        }

        super.stop();

        // mJobServiceImpl.destroy();
    }

    @Override
    public void run() {
        if (DEBUG) {
            Log.d(LOG_TAG, "start");
        }

        if (!isConfigured()) {
            if (DEBUG) {
                Log.d(LOG_TAG, "run not configured");
            }

            return;
        }

        onCreate();

        mRunning = true;
        final Random random = new Random();
        while (mRunning) {
            if (INFO) {
                Log.i(LOG_TAG, "wait for connected network...");
            }

            if (!isConnected()) {
                if (INFO) {
                    Log.i(LOG_TAG, "connect...");
                }

                final int timeout = connectInternal();
                if (timeout == 0) {
                    if (INFO) {
                        Log.i(LOG_TAG, "connected");
                    }

                    mConnectedTimeout = MIN_CONNECTED_TIMEOUT;
                    mReconnectionTimeout = random.nextInt(MIN_RECONNECTION_TIMEOUT);
                } else {
                    mReconnectionTimeout = random.nextInt(MAX_RECONNECTION_TIMEOUT) + timeout;
                }
            }
            if (isConnected()) {
                if (INFO) {
                    Log.i(LOG_TAG, "still connected");
                }

                //noinspection EmptyCatchBlock
                try {
                    mConnectedLock.lock();
                    if (!mConnectedCondition.await(mConnectedTimeout, TimeUnit.SECONDS)) {
                        mConnectedTimeout *= 2;
                        if (mConnectedTimeout > MAX_CONNECTED_TIMEOUT) {
                            mConnectedTimeout = MAX_CONNECTED_TIMEOUT;
                        }
                    }
                } catch (InterruptedException exception) {
                } finally {
                    mConnectedLock.unlock();
                }
            } else {
                if (INFO) {
                    Log.i(LOG_TAG, "wait before reconnecting " + mReconnectionTimeout);
                }

                if (mReconnectionTimeout == NO_RECONNECTION_TIMEOUT) {
                    mReconnectionTimeout = MIN_RECONNECTION_TIMEOUT + random.nextInt(MAX_RECONNECTION_TIMEOUT);
                } else {
                    //noinspection EmptyCatchBlock
                    try {
                        mReconnectionLock.lock();
                        if (!mReconnectionCondition.await(mReconnectionTimeout, TimeUnit.MILLISECONDS)) {
                            mReconnectionTimeout = MIN_RECONNECTION_TIMEOUT + random.nextInt(MAX_RECONNECTION_TIMEOUT);
                        }
                    } catch (InterruptedException exception) {
                    } finally {
                        mReconnectionLock.unlock();
                    }
                }
            }
        }
    }

    @NonNull
    @Override
    public ImageTools getImageTools() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getImageTools");
        }

        return mImageTools;
    }

    @Override
    protected void openDatabase() {

    }

    @Override
    protected void removeDatabase() {

    }

    @Override
    protected void closeDatabase() {

    }

    @Override
    @NonNull
    public BaseService.ErrorCode getDatabaseStatus() {

        return BaseService.ErrorCode.SERVICE_UNAVAILABLE;
    }
}
