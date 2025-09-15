/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */
package org.twinlife.web.messages;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"msg", "sessionId", "twincodeOutboundId", "callRoomId", "mode", "maxMemberCount"})
public class InviteCallRoomMessage {

    public static final String NAME = "invite-call-room";

    public final String msg = NAME;
    public String sessionId;
    public String twincodeOutboundId;
    public String callRoomId;
    public int mode;
    public int maxMemberCount;
}