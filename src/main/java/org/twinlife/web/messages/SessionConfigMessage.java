/*
 *  Copyright (c) 2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.web.messages;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.twinlife.twinlife.TurnServer;

import java.util.List;

@JsonPropertyOrder({"msg", "turnServers", "maxSendFrameSize", "maxSendFrameRate", "maxReceivedFrameSize", "maxReceivedFrameRat" })
public class SessionConfigMessage {

    public static final String NAME = "session-config";

    public String msg;
    public int maxSendFrameSize;
    public int maxSendFrameRate;
    public int maxReceivedFrameSize;
    public int maxReceivedFrameRate;
    public TurnServer[] turnServers;

    public SessionConfigMessage() {
        msg = NAME;
    }
}
