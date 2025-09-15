/*
 *  Copyright (c) 2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.web.messages;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"msg", "sessionId", "candidates" })
public class TransportInfoMessage {

    public static final String NAME = "transport-info";

    public String msg;
    public String sessionId;
    public List<CandidateInfo> candidates;

    public TransportInfoMessage() {
        msg = NAME;
    }
}
