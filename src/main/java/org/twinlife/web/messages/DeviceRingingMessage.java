/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.web.messages;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"msg", "sessionId" })
public class DeviceRingingMessage {

    public static final String NAME = "device-ringing";

    public String msg;
    public String sessionId;

    public DeviceRingingMessage() {
        msg = NAME;
    }

    public DeviceRingingMessage(String sessionId) {
        this.msg = NAME;
        this.sessionId = sessionId;
    }
}
