/*
 *  Copyright (c) 2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.web.messages;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"msg", "sessionId", "sdp", "updateType" })
public class SessionUpdateMessage {

    public static final String NAME = "session-update";

    public String msg;
    public String sessionId;
    public String sdp;
    public String updateType;

    public SessionUpdateMessage() {
        msg = NAME;
    }
}
