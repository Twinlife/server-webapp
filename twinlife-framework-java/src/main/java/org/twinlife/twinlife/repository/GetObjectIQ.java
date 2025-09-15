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
 * Get object IQ or Delete object IQ
 * <p>
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"6dc2169c-1ec8-4c4a-9842-ab26b8484813",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"GetObjectIQ",
 *  "namespace":"org.twinlife.schemas.repository",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"objectSchemaId", "type": "uuid"}
 *     {"name":"objectId", "type": "uuid"}
 *  ]
 * }
 * </pre>
 */
public class GetObjectIQ extends BinaryPacketIQ {

    static class GetObjectIQSerializer extends BinaryPacketIQSerializer {

        GetObjectIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, GetObjectIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            GetObjectIQ getObjectIQ = (GetObjectIQ) object;

            encoder.writeUUID(getObjectIQ.objectSchemaId);
            encoder.writeUUID(getObjectIQ.objectId);
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

        return new GetObjectIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    private final UUID objectSchemaId;
    @NonNull
    private final UUID objectId;

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" objectSchemaId=");
            stringBuilder.append(objectSchemaId);
            stringBuilder.append(" objectId=");
            stringBuilder.append(objectId);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("GetObjectIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    public GetObjectIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                       @NonNull UUID objectSchemaId, @NonNull UUID objectId) {
        super(serializer, requestId);

        this.objectSchemaId = objectSchemaId;
        this.objectId = objectId;
    }
}
