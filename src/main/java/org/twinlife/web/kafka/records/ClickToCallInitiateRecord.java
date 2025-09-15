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

public class ClickToCallInitiateRecord extends ClickToCallRecord {
    static final UUID SCHEMA_ID = UUID.fromString("666c938a-8427-4ded-a191-b270be9482e4");
    static final int SCHEMA_VERSION = 1;

    public static final ClickToCallInitiateRecordSerializer SERIALIZER = new ClickToCallInitiateRecordSerializer();
    
    private static class ClickToCallInitiateRecordSerializer
            extends ClickToCallRecordSerializer<ClickToCallInitiateRecord> {
        public ClickToCallInitiateRecordSerializer() {
            super(SCHEMA_ID, SCHEMA_VERSION, ClickToCallInitiateRecord.class);
        }
    }

    /**
     * Constructs a new ClickToCallInitiateRecord.
     *
     * @param sessionId P2P session identifier of this C2C call
     * @param twincodeId Caller twincode identifier
     * @param ipAddr Local IP address of the connection
     */
    public ClickToCallInitiateRecord(@Nonnull UUID sessionId, @Nonnull UUID twincodeId, @Nonnull InetAddress ipAddr) {
        super(sessionId, twincodeId, ipAddr);
        this.mTimestamp = System.currentTimeMillis();
        this.mSessionId = sessionId;
        this.mTwincodeId = twincodeId;
        this.mIpAddr = ipAddr;
    }
    
    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("ClickToCallInitiateRecord:\n");
        appendTo(stringBuilder);

        return stringBuilder.toString();
    }
}
