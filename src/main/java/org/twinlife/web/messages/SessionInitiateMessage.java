/*
 *  Copyright (c) 2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.web.messages;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"msg", "to", "sessionId", "sdp", "offer", "offerToReceive", "maxFrameSize", "maxFrameRate" })
public class SessionInitiateMessage {

    public static final String NAME = "session-initiate";

    public String msg;
    public String sessionId;
    public String to;
    public String sdp;
    public SessionOffer offer;
    public SessionOfferToReceive offerToReceive;
    public int maxFrameSize;
    public int maxFrameRate;

    public SessionInitiateMessage() {
        msg = NAME;
    }
}
