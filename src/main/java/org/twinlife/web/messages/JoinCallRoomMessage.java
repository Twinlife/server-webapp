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

import java.util.List;

@JsonPropertyOrder({"msg", "callRoomId", "sessionId", "memberId", "members", "maxMemberCount" })
public class JoinCallRoomMessage {

    public static final String NAME = "join-callroom";

    public String msg;
    public String callRoomId;
    public String sessionId;
    public String memberId;
    public List<MemberInfo> members;
    public int maxMemberCount;

    public JoinCallRoomMessage() {
        msg = NAME;
    }
}
