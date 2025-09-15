/*
 *  Copyright (c) 2019-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.util.Log;
import androidx.annotation.NonNull;

import javax.crypto.SecretKey;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration service that uses Java property files to store the configuration.
 */
public class PropertiesConfigurationServiceImpl implements ConfigurationService {
    private static final String LOG_TAG = "PropertiesConfigurationServiceImpl";
    private static final boolean DEBUG = false;

    private final Map<String, PropertiesConfigurationImpl> mConfigurations;
    @NonNull
    private final File mRootDirectory;
    private final String mApplicationVersion;
    private final SecretKey mEncryptionKey;

    public PropertiesConfigurationServiceImpl(@NonNull String name, @NonNull File directory,
                                              @NonNull SecretKey encryptionKey) {

        mConfigurations = new HashMap<>();
        mRootDirectory = directory;
        mEncryptionKey = encryptionKey;
        if (!mRootDirectory.exists()) {
            if (!mRootDirectory.mkdirs()) {
                Log.e(LOG_TAG, "Cannot create config directory: " + directory);
            }
        }

        Properties props = new Properties();

        String propFile = name + ".properties";
        try (InputStream propStream = PropertiesConfigurationServiceImpl.class.getClassLoader().getResourceAsStream(propFile)) {
            props.load(propStream);
        } catch (IOException e) {
            Log.e("Could not load properties from {}", propFile, e);
        }

        mApplicationVersion = props.getProperty("application.version", "");
    }

    public String getApplicationVersion() {

        return mApplicationVersion;
    }

    public String getName() {

        File parent = mRootDirectory.getParentFile();
        return parent.getName();
    }

    @Override
    public Configuration getConfiguration(String name) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConfiguration");
        }

        PropertiesConfigurationImpl configuration;
        synchronized (mConfigurations) {
            configuration = mConfigurations.get(name);
            if (configuration == null) {
                configuration = new PropertiesConfigurationImpl(new File(mRootDirectory, name + ".properties"));
                mConfigurations.put(name, configuration);
            }
        }
        return configuration;
    }

    @Override
    public Configuration getConfiguration(@NonNull ConfigIdentifier config) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConfiguration");
        }

        return getConfiguration(config.getConfigName());
    }

    @Override
    public void deleteConfiguration(Configuration configuration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteConfiguration configuration=" + configuration);
        }

        PropertiesConfigurationImpl propertiesConfigurationImpl = (PropertiesConfigurationImpl)configuration;
        propertiesConfigurationImpl.delete();
    }

    @Override
    @NonNull
    public SecuredConfiguration getSecuredConfiguration(@NonNull String name) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getSecuredConfiguration");
        }

        return new FileSecuredConfigurationImpl(mEncryptionKey, new File(mRootDirectory, name));
    }

    @Override
    public void saveSecuredConfiguration(SecuredConfiguration configuration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "saveSecuredConfiguration");
        }

        FileSecuredConfigurationImpl fileSecuredConfiguration = (FileSecuredConfigurationImpl) configuration;
        fileSecuredConfiguration.save();
    }

    @Override
    public void eraseAllSecuredConfiguration() {

    }
}
