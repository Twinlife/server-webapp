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
import javax.annotation.Nullable;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;

public class ClickToCallJoinRoomRecord extends ClickToCallRecord {
    private static final UUID SCHEMA_ID = UUID.fromString("2a6c9367-3dca-478d-9551-87402536d41e");
    private static final int SCHEMA_VERSION = 1;

    public static final ClickToCallJoinRoomRecordSerializer SERIALIZER = new ClickToCallJoinRoomRecordSerializer();
    
    protected static class ClickToCallJoinRoomRecordSerializer
            extends ClickToCallRecordSerializer<ClickToCallJoinRoomRecord> {
        public ClickToCallJoinRoomRecordSerializer() {
            super(SCHEMA_ID, SCHEMA_VERSION, ClickToCallJoinRoomRecord.class);
        }

        @Override
        public void deserialize(@Nonnull Decoder decoder, @Nonnull ClickToCallJoinRoomRecord record, int version)
                throws SerializerException {
            super.deserialize(decoder, record, version);
            record.mCallRoomId = decoder.readUUID();
        }

        @Override
        public void serialize(@Nonnull Encoder encoder, @Nonnull ClickToCallJoinRoomRecord record, int version)
                throws SerializerException {
            super.serialize(encoder, record, version);
            encoder.writeUUID(record.mCallRoomId);
        }
    }

    private UUID mCallRoomId;

    /**
     * Constructs a new ClickToCallJoinRoomRecord.
     *
     * @param sessionId P2P session identifier of this C2C call (possibly <code>null</code> if unknown)
     * @param twincodeId Caller twincode identifier
     * @param ipAddr Local IP address of the connection
     * @param callRoomId Call room identifier
     */
    public ClickToCallJoinRoomRecord(@Nullable UUID sessionId, @Nonnull UUID twincodeId, @Nonnull InetAddress ipAddr,
            UUID callRoomId) {
        super(sessionId, twincodeId, ipAddr);
        mCallRoomId = callRoomId;
    }

    public UUID getCallRoomId() {
        return mCallRoomId;
    }

    protected void appendTo(StringBuilder stringBuilder) {
        super.appendTo(stringBuilder);

        stringBuilder.append(" callRoomId=");
        stringBuilder.append(mCallRoomId);
        stringBuilder.append("\n");
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("ClickToCallJoinRoomRecord:\n");
        appendTo(stringBuilder);

        return stringBuilder.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((mCallRoomId == null) ? 0 : mCallRoomId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        ClickToCallJoinRoomRecord other = (ClickToCallJoinRoomRecord) obj;
        if (mCallRoomId == null) {
            if (other.mCallRoomId != null)
                return false;
        } else if (!mCallRoomId.equals(other.mCallRoomId))
            return false;
        return true;
    }
}
