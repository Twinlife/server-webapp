/*
 *  Copyright (c) 2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.web.messages;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"audio", "video", "videoBell", "data", "version" })
public class SessionOffer {

    public boolean audio;
    public boolean video;
    public boolean videoBell;
    public boolean data;
    public String version;
}
