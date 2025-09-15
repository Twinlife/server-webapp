/*
 *  Copyright (c) 2021-2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.web.rest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.internal.FormDataParamInjectionFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.logging.LoggingFeature;

/**
 * Root resource (exposed at "myresource" path)
 */
public class Application extends ResourceConfig {
    static final Logger Log = LogManager.getLogger(Application.class);

    public Application() {
        super(JacksonFeature.class, MultiPartFeature.class, LoggingFeature.class,
                FormDataParamInjectionFeature.class);

        Log.error("Proxy web application created");
    }
}
