/*
 *  Copyright (c) 2022-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.repository;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Create object response IQ.
 * <p>
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"fde9aa2f-c0e3-437a-a1d1-0121e72e43bd",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnCreateObjectIQ",
 *  "namespace":"org.twinlife.schemas.repository",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"objectId", "type": "uuid"}
 *     {"name":"creationDate", "type": "long"}
 *  ]
 * }
 * </pre>
 */
public class OnCreateObjectIQ extends BinaryPacketIQ {

    static class OnCreateObjectIQSerializer extends BinaryPacketIQSerializer {

        OnCreateObjectIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnCreateObjectIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            throw new SerializerException();
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID objectId = decoder.readUUID();
            long creationDate = decoder.readLong();

            return new OnCreateObjectIQ(this, serviceRequestIQ.getRequestId(), objectId, creationDate);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnCreateObjectIQSerializer(schemaId, schemaVersion);
    }

    private final long creationDate;
    @NonNull
    private final UUID objectId;

    @NonNull
    public UUID getObjectId() {

        return objectId;
    }

    public long getCreationDate() {

        return creationDate;
    }

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" objectId=");
            stringBuilder.append(objectId);
            stringBuilder.append(" creationDate=");
            stringBuilder.append(creationDate);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnCreateObjectIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    public OnCreateObjectIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                            @NonNull UUID objectId, long creationDate) {
        super(serializer, requestId);

        this.objectId = objectId;
        this.creationDate = creationDate;
    }
}
