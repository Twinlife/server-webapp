/*
 *  Copyright (c) 2024-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Laurent Latil (Laurent.Latil@twin.life)
 */

package org.twinlife.web.kafka.records;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.web.kafka.Serializer;
import org.twinlife.web.util.TimestampUtil;

public abstract class ClickToCallRecord implements TimestampedRecord {

    protected static class ClickToCallRecordSerializer<T extends ClickToCallRecord> extends Serializer<T> {
        protected ClickToCallRecordSerializer(UUID schemaId, int maxSupportedVersion, Class<? extends T> clazz) {
            super(schemaId, maxSupportedVersion, clazz);
        }

        @Override
        public void deserialize(@Nonnull Decoder decoder, @Nonnull T record, int version) throws SerializerException {
            record.mTimestamp = decoder.readLong();
            record.mSessionId = decoder.readOptionalUUID();
            record.mTwincodeId = decoder.readUUID();

            try {
                record.mIpAddr = InetAddress.getByName(decoder.readString());
            } catch (UnknownHostException e) {
                record.mIpAddr = null;
            }
        }

        @Override
        public void serialize(@Nonnull Encoder encoder, @Nonnull T record, int version) throws SerializerException {
            encoder.writeLong(record.mTimestamp);
            encoder.writeOptionalUUID(record.mSessionId);
            encoder.writeUUID(record.mTwincodeId);
            encoder.writeString(record.mIpAddr.getHostAddress());
        }
    }

    protected long mTimestamp;

    /** P2P session identifier of this C2C call (possibly <code>null</code> if unknown) */
    protected UUID mSessionId;

    /** Caller twincode id */
    protected UUID mTwincodeId;

    /** Local IP address */
    protected InetAddress mIpAddr;

    /**
     * Default ctor is required for deserialization (to create dynamically a new instance)
     */
    protected ClickToCallRecord() {
    }

    /**
     * Constructs a new ClickToCallRecord. This ctor is only used from derived classes.
     *
     * @param sessionId P2P session identifier of this C2C call (possibly <code>null</code> if unknown)
     * @param twincodeId Caller twincode identifier
     * @param ipAddr Local IP address of the connection
     */
    protected ClickToCallRecord(@Nullable UUID sessionId, @Nonnull UUID twincodeId, @Nonnull InetAddress ipAddr) {
        this.mTimestamp = System.currentTimeMillis();
        this.mSessionId = sessionId;
        this.mTwincodeId = twincodeId;
        this.mIpAddr = ipAddr;
    }

    protected void appendTo(StringBuilder stringBuilder) {
        stringBuilder.append(" timestamp=");
        stringBuilder.append("" + mTimestamp + "(" + TimestampUtil.formatTimestamp(mTimestamp) + ")");
        stringBuilder.append("\n");
        stringBuilder.append(" sessionId=");
        stringBuilder.append(mSessionId);
        stringBuilder.append("\n");
        stringBuilder.append(" twincodeId=");
        stringBuilder.append(mTwincodeId);
        stringBuilder.append("\n");
        stringBuilder.append(" ipAddr=");
        stringBuilder.append(mIpAddr.getHostAddress());
        stringBuilder.append("\n");
    }

    //
    // Override Object methods
    //

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("ClickToCallRecord:\n");
        appendTo(stringBuilder);

        return stringBuilder.toString();
    }

    public @Nonnull UUID getSessionId() {
        return mSessionId;
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public void setTimestamp(long timestamp) {
        this.mTimestamp = timestamp;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mIpAddr == null) ? 0 : mIpAddr.hashCode());
        result = prime * result + ((mSessionId == null) ? 0 : mSessionId.hashCode());
        result = prime * result + (int) (mTimestamp ^ (mTimestamp >>> 32));
        result = prime * result + ((mTwincodeId == null) ? 0 : mTwincodeId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ClickToCallRecord other = (ClickToCallRecord) obj;
        if (mIpAddr == null) {
            if (other.mIpAddr != null)
                return false;
        } else if (!mIpAddr.equals(other.mIpAddr))
            return false;
        if (mSessionId == null) {
            if (other.mSessionId != null)
                return false;
        } else if (!mSessionId.equals(other.mSessionId))
            return false;
        if (mTimestamp != other.mTimestamp)
            return false;
        if (mTwincodeId == null) {
            if (other.mTwincodeId != null)
                return false;
        } else if (!mTwincodeId.equals(other.mTwincodeId))
            return false;
        return true;
    }

}
