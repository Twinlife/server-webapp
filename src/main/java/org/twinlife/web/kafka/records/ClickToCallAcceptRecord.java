/*
 *  Copyright (c) 2024-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Laurent Latil (Laurent.Latil@twin.life)
 */

package org.twinlife.web.kafka.records;

import java.net.InetAddress;
import java.util.UUID;

import javax.annotation.Nonnull;

public class ClickToCallAcceptRecord extends ClickToCallRecord {
    static final UUID SCHEMA_ID = UUID.fromString("9ce838eb-9ed5-4919-85b7-717f54a239a4");
    static final int SCHEMA_VERSION = 1;

    public static final ClickToCallAcceptRecordSerializer SERIALIZER = new ClickToCallAcceptRecordSerializer();
    
    private static class ClickToCallAcceptRecordSerializer
            extends ClickToCallRecordSerializer<ClickToCallAcceptRecord> {
        public ClickToCallAcceptRecordSerializer() {
            super(SCHEMA_ID, SCHEMA_VERSION, ClickToCallAcceptRecord.class);
        }
    }

    /**
     * Constructs a new ClickToCallAcceptRecord.
     *
     * @param sessionId P2P session identifier of this C2C call
     * @param twincodeId Caller twincode identifier
     * @param ipAddr Local IP address of the connection
     */
    public ClickToCallAcceptRecord(@Nonnull UUID sessionId, @Nonnull UUID twincodeId, @Nonnull InetAddress ipAddr) {
        super(sessionId, twincodeId, ipAddr);
        this.mTimestamp = System.currentTimeMillis();
        this.mSessionId = sessionId;
        this.mTwincodeId = twincodeId;
        this.mIpAddr = ipAddr;
    }
    
    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("ClickToCallAcceptRecord:\n");
        appendTo(stringBuilder);

        return stringBuilder.toString();
    }
}
