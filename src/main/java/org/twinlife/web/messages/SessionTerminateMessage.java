/*
 *  Copyright (c) 2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.web.messages;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"msg", "sessionId", "reason" })
public class SessionTerminateMessage {

    public static final String NAME = "session-terminate";

    public String msg;
    public String sessionId;
    public String reason;

    public SessionTerminateMessage() {
        msg = NAME;
    }

    public SessionTerminateMessage(String sessionId, String reason) {
        this.msg = NAME;
        this.sessionId = sessionId;
        this.reason = reason;
    }
}
