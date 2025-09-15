/*
 *  Copyright (c) 2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.web.messages;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.UUID;

@JsonPropertyOrder({"msg", "to", "sessionId", "status" })
public class SessionInitiateResponseMessage {

    public static final String NAME = "session-initiate-response";

    public String msg;
    public String to;
    public String sessionId;
    public String status;

    public SessionInitiateResponseMessage() {
        msg = NAME;
    }

    public SessionInitiateResponseMessage(String to, UUID sessionId) {
        msg = NAME;
        this.to = to;
        this.sessionId = sessionId.toString();
        this.status = "success";
    }

    public SessionInitiateResponseMessage(String to, String error) {
        msg = NAME;
        this.to = to;
        this.sessionId = null;
        this.status = error;
    }
}
