/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.web.messages;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.twinlife.twinlife.PeerCallService;

@JsonPropertyOrder({"msg", "sessionId", "memberId", "members", "maxMemberCount" })
public class MemberJoinCallMessage {

    public static final String NAME = "member-join";

    public String msg;
    public String sessionId;
    public String memberId;
    public PeerCallService.MemberStatus status;

    public MemberJoinCallMessage() {
        msg = NAME;
    }
}
