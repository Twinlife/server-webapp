/*
 *  Copyright (c) 2013-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import androidx.annotation.Nullable;

import org.twinlife.twinlife.AccountService.AccountServiceConfiguration;
import org.twinlife.twinlife.BaseService.ErrorCode;

import java.io.File;

@SuppressWarnings("unused")
public interface Twinlife {

    String VERSION = BuildConfig.VERSION_NAME;

    int MAX_AVATAR_HEIGHT = 256;
    int MAX_AVATAR_WIDTH = 256;

    // Common directory names used in 'files' directory.
    String CONVERSATIONS_DIR = "conversations";
    String LOCAL_IMAGES_DIR = "pictures";
    String TMP_DIR = "tmp";
    String OLD_TMP_DIR = "images"; // Legacy tmp directory: images are removed when application was restarted.

    @SuppressWarnings("WeakerAccess")
    class TwinlifeConfiguration {

        protected String serviceId;
        protected String applicationId;
        protected String applicationName;
        protected String applicationVersion;
        protected String certificateSerialNumber;
        protected Serializer[] serializers = new Serializer[0];
        protected String apiKey;
        protected RepositoryObjectFactory<?>[] factories;

        protected AccountService.AccountServiceConfiguration accountServiceConfiguration;
        protected ManagementService.ManagementServiceConfiguration managementServiceConfiguration;
        protected RepositoryService.RepositoryServiceConfiguration repositoryServiceConfiguration;
        protected TwincodeFactoryService.TwincodeFactoryServiceConfiguration twincodeFactoryServiceConfiguration;
        protected TwincodeInboundService.TwincodeInboundServiceConfiguration twincodeInboundServiceConfiguration;
        protected TwincodeOutboundService.TwincodeOutboundServiceConfiguration twincodeOutboundServiceConfiguration;
        protected ImageService.ImageServiceConfiguration imageServiceConfiguration;
        protected PeerCallService.PeerCallServiceConfiguration peerCallServiceConfiguration;
        // protected CryptoService.CryptoServiceServiceConfiguration cryptoServiceConfiguration;

        protected TwinlifeConfiguration() {

            accountServiceConfiguration = new AccountServiceConfiguration();
            managementServiceConfiguration = new ManagementService.ManagementServiceConfiguration();
            repositoryServiceConfiguration = new RepositoryService.RepositoryServiceConfiguration();
            twincodeFactoryServiceConfiguration = new TwincodeFactoryService.TwincodeFactoryServiceConfiguration();
            twincodeInboundServiceConfiguration = new TwincodeInboundService.TwincodeInboundServiceConfiguration();
            twincodeOutboundServiceConfiguration = new TwincodeOutboundService.TwincodeOutboundServiceConfiguration();
            imageServiceConfiguration = new ImageService.ImageServiceConfiguration();
            peerCallServiceConfiguration = new PeerCallService.PeerCallServiceConfiguration();
            // cryptoServiceConfiguration = new CryptoService.CryptoServiceServiceConfiguration();
            apiKey = BuildConfig.API_KEY;
        }
    }

    interface ServiceFactory {

        BaseServiceImpl<?> createServices(TwinlifeImpl twinlife, Connection connection);
    }

    boolean isConfigured();

    boolean isDatabaseUpgraded();

    @NonNull
    ErrorCode configure(@NonNull TwinlifeConfiguration twinlifeConfiguration,
                        @NonNull Connection connection);

    @NonNull
    ErrorCode getDatabaseStatus();

    void stop();

    @NonNull
    AccountService getAccountService();

    @Nullable
    ManagementService getManagementService();

    @NonNull
    RepositoryService getRepositoryService();

    @NonNull
    TwincodeFactoryService getTwincodeFactoryService();

    @NonNull
    TwincodeInboundService getTwincodeInboundService();

    @NonNull
    TwincodeOutboundService getTwincodeOutboundService();

    @NonNull
    ConfigurationService getConfigurationService();

    @NonNull
    ImageService getImageService();

    @NonNull
    PeerCallService getPeerCallService();

    @NonNull
    SerializerFactory getSerializerFactory();

    @NonNull
    JobService getJobService();

    @NonNull
    File getFilesDir();

    @Nullable
    File getCacheDir();

    @Nullable
    PackageInfo getPackageInfo();
}
