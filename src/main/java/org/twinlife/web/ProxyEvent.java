/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.web;

import androidx.annotation.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Log important events for the web proxy service.
 */
public class ProxyEvent {
    static final Logger Log = LogManager.getLogger(ProxyEvent.class);

    /**
     * Emit a log with the event and attributes to record some important event in the
     * execution of the web app proxy.
     *
     * @param eventId the event id (should be unique)
     * @param attributes optional list of name/value pairs
     */
    public static void logEvent(@NonNull String eventId, String... attributes) {

        final StringBuilder params = new StringBuilder();
        if (attributes != null) {
            for (int i = 0; i + 1 < attributes.length; i += 2) {
                params.append(' ');
                params.append(attributes[i]);
                params.append('=');
                params.append(attributes[i + 1]);
            }
        }

        Log.info("{}{}", eventId, params);
    }
}
