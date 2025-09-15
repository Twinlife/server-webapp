/*
 *  Copyright (c) 2022-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.List;
import java.util.UUID;

/**
 * Create object IQ.
 * <p>
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"cc1de051-04c9-49c2-827d-2d8c8545ff41",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"CreateObjectIQ",
 *  "namespace":"org.twinlife.schemas.repository",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"createOptions", "type": "int"}
 *     {"name":"objectSchemaId", "type": "uuid"}
 *     {"name":"objectSchemaVersion", "type": "int"}
 *     {"name":"objectKey", "type": [null, "uuid"]}
 *     {"name":"data", "type": "string"}
 *     {"name":"exclusiveContents", [
 *      {"name":"name", "type": "string"}
 *     ]}
 *  ]
 * }
 * </pre>
 */
public class CreateObjectIQ extends BinaryPacketIQ {

    static class CreateObjectIQSerializer extends BinaryPacketIQSerializer {

        CreateObjectIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, CreateObjectIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            CreateObjectIQ createObjectIQ = (CreateObjectIQ) object;

            encoder.writeInt(createObjectIQ.createOptions);
            encoder.writeUUID(createObjectIQ.objectSchemaId);
            encoder.writeInt(createObjectIQ.objectSchemaVersion);
            encoder.writeOptionalUUID(createObjectIQ.objectKey);
            encoder.writeString(createObjectIQ.objectData);
            if (createObjectIQ.exclusiveContents == null) {
                encoder.writeInt(0);
            } else {
                encoder.writeInt(createObjectIQ.exclusiveContents.size());
                for (String c : createObjectIQ.exclusiveContents) {
                    encoder.writeString(c);
                }
            }
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

        return new CreateObjectIQSerializer(schemaId, schemaVersion);
    }

    private final int createOptions;
    @NonNull
    private final UUID objectSchemaId;
    private final int objectSchemaVersion;
    @Nullable
    private final UUID objectKey;
    @NonNull
    private final String objectData;
    @Nullable
    private final List<String> exclusiveContents;

    public static final int PRIVATE = 0x01;
    public static final int PUBLIC = 0x02;
    public static final int EXCLUSIVE = 0x04;
    public static final int IMMUTABLE = 0x08;

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" createOptions=");
            stringBuilder.append(createOptions);
            stringBuilder.append(" schemaId=");
            stringBuilder.append(objectSchemaId);
            stringBuilder.append(" schemaVer=");
            stringBuilder.append(objectSchemaVersion);
            stringBuilder.append(" objectKey=");
            stringBuilder.append(objectKey);
            stringBuilder.append(" objectData=");
            stringBuilder.append(objectData);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("CreateObjectIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    public CreateObjectIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                          int createOptions, @NonNull UUID objectSchemaId, int objectSchemaVersion,
                          @Nullable UUID objectKey, @NonNull String objectData,
                          @Nullable List<String> exclusiveContents) {
        super(serializer, requestId);

        this.createOptions = createOptions;
        this.objectSchemaId = objectSchemaId;
        this.objectSchemaVersion = objectSchemaVersion;
        this.objectKey = objectKey;
        this.objectData = objectData;
        this.exclusiveContents = exclusiveContents;
    }
}
