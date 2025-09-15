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
 * List object IQ.
 * <p>
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"7d9baa6c-635e-4bda-b31a-a416322e4eec",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"ListObjectIQ",
 *  "namespace":"org.twinlife.schemas.repository",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"objectSchemaId", "type": "uuid"}
 *  ]
 * }
 * </pre>
 */
public class ListObjectIQ extends BinaryPacketIQ {

    static class ListObjectIQSerializer extends BinaryPacketIQSerializer {

        ListObjectIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, ListObjectIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            ListObjectIQ getObjectIQ = (ListObjectIQ) object;

            encoder.writeUUID(getObjectIQ.objectSchemaId);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            throw new SerializerException();
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new ListObjectIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    private final UUID objectSchemaId;

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" objectSchemaId=");
            stringBuilder.append(objectSchemaId);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("ListObjectIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    public ListObjectIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                        @NonNull UUID objectSchemaId) {
        super(serializer, requestId);

        this.objectSchemaId = objectSchemaId;
    }
}
