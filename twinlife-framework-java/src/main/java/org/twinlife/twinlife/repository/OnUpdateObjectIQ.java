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
 * Update object response IQ.
 * <p>
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"0890ec66-0560-4b41-8e65-227119d0b008",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnUpdateObjectIQ",
 *  "namespace":"org.twinlife.schemas.repository",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"modificationDate", "type": "long"}
 *  ]
 * }
 * </pre>
 */
public class OnUpdateObjectIQ extends BinaryPacketIQ {

    static class OnUpdateObjectIQSerializer extends BinaryPacketIQSerializer {

        OnUpdateObjectIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnUpdateObjectIQ.class);
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

            long modificationDate = decoder.readLong();

            return new OnUpdateObjectIQ(this, serviceRequestIQ.getRequestId(), modificationDate);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnUpdateObjectIQSerializer(schemaId, schemaVersion);
    }

    private final long modificationDate;

    public long getModificationDate() {

        return modificationDate;
    }

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" modificationDate=");
            stringBuilder.append(modificationDate);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnUpdateObjectIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    public OnUpdateObjectIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                            long modificationDate) {
        super(serializer, requestId);

        this.modificationDate = modificationDate;
    }
}
