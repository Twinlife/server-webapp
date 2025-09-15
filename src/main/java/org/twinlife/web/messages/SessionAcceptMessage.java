/*
 *  Copyright (c) 2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.web.messages;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"msg", "sessionId", "sdp", "offer", "offerToReceive", "maxFrameSize", "maxFrameRate" })
public class SessionAcceptMessage {

    public static final String NAME = "session-accept";

    public String msg;
    public String sessionId;
    public String sdp;
    public SessionOffer offer;
    public SessionOfferToReceive offerToReceive;
    public int maxFrameSize;
    public int maxFrameRate;

    public SessionAcceptMessage() {
        msg = NAME;
    }
}
