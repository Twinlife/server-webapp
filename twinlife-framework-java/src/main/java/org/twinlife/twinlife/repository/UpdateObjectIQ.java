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
 * Update object IQ.
 * <p>
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"3bfed52d-0173-4f0d-bfd9-f5d63454ca59",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"UpdateObjectIQ",
 *  "namespace":"org.twinlife.schemas.repository",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"updateOptions", "type": "int"}
 *     {"name":"objectId", "type": "uuid"}
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
public class UpdateObjectIQ extends BinaryPacketIQ {

    static class UpdateObjectIQSerializer extends BinaryPacketIQSerializer {

        UpdateObjectIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, UpdateObjectIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            UpdateObjectIQ updateObjectIQ = (UpdateObjectIQ) object;

            encoder.writeInt(updateObjectIQ.updateOptions);
            encoder.writeUUID(updateObjectIQ.objectId);
            encoder.writeUUID(updateObjectIQ.objectSchemaId);
            encoder.writeInt(updateObjectIQ.objectSchemaVersion);
            encoder.writeOptionalUUID(updateObjectIQ.objectKey);
            encoder.writeString(updateObjectIQ.objectData);
            if (updateObjectIQ.exclusiveContents == null) {
                encoder.writeInt(0);
            } else {
                encoder.writeInt(updateObjectIQ.exclusiveContents.size());
                for (String c : updateObjectIQ.exclusiveContents) {
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

        return new UpdateObjectIQSerializer(schemaId, schemaVersion);
    }

    private final int updateOptions;
    @NonNull
    private final UUID objectId;
    @NonNull
    private final UUID objectSchemaId;
    private final int objectSchemaVersion;
    @Nullable
    private final UUID objectKey;
    @NonNull
    private final String objectData;
    @Nullable
    private final List<String> exclusiveContents;

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" objectId=");
            stringBuilder.append(objectId);
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
            stringBuilder.append("UpdateObjectIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    public UpdateObjectIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                          int updateOptions, @NonNull UUID objectId,
                          @NonNull UUID objectSchemaId, int objectSchemaVersion,
                          @Nullable UUID objectKey, @NonNull String objectData,
                          @Nullable List<String> exclusiveContents) {
        super(serializer, requestId);

        this.updateOptions = updateOptions;
        this.objectId = objectId;
        this.objectSchemaId = objectSchemaId;
        this.objectSchemaVersion = objectSchemaVersion;
        this.objectKey = objectKey;
        this.objectData = objectData;
        this.exclusiveContents = exclusiveContents;
    }
}
