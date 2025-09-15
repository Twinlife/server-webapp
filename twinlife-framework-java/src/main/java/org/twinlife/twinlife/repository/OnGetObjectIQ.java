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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Get object response IQ.
 * <p>
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"5fdf06d0-513f-4858-b416-73721f2ce309",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnGetObjectIQ",
 *  "namespace":"org.twinlife.schemas.repository",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"creationDate", "type": "long"}
 *     {"name":"modificationDate", "type": "long"}
 *     {"name":"objectSchemaId", "type": "uuid"}
 *     {"name":"objectSchemaVersion", "type": "int"}
 *     {"name":"objectFlags", "type": "int"}
 *     {"name":"objectKey", "type": [null, "uuid"]}
 *     {"name":"data", "type": "string"}
 *     {"name":"exclusiveContents", [
 *      {"name":"name", "type": "string"}
 *     ]}
 *  ]
 * }
 * </pre>
 */
public class OnGetObjectIQ extends BinaryPacketIQ {

    static class OnGetObjectIQSerializer extends BinaryPacketIQSerializer {

        OnGetObjectIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnGetObjectIQ.class);
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

            long creationDate = decoder.readLong();
            long modificationDate = decoder.readLong();
            UUID objectSchemaId = decoder.readUUID();
            int objectSchemaVersion = decoder.readInt();
            int objectFlags = decoder.readInt();
            UUID objectKey = decoder.readOptionalUUID();
            String objectData = decoder.readString();
            int count = decoder.readInt();
            List<String> exclusiveContents = null;
            if (count > 0) {
                exclusiveContents = new ArrayList<>(count);
                while (count > 0) {
                    count--;
                    exclusiveContents.add(decoder.readString());
                }
            }

            return new OnGetObjectIQ(this, serviceRequestIQ.getRequestId(), creationDate, modificationDate,
                    objectSchemaId, objectSchemaVersion, objectFlags, objectKey, objectData, exclusiveContents);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnGetObjectIQSerializer(schemaId, schemaVersion);
    }

    private final long creationDate;
    private final long modificationDate;
    @NonNull
    private final UUID objectSchemaId;
    private final int objectSchemaVersion;
    @Nullable
    private final UUID objectKey;
    @NonNull
    private final String objectData;
    @Nullable
    private final List<String> exclusiveContents;
    private final int objectFlags;

    public long getCreationDate() {

        return creationDate;
    }

    public int getObjectFlags() {

        return objectFlags;
    }

    @NonNull
    public UUID getObjectSchemaId() {

        return objectSchemaId;
    }

    public int getObjectSchemaVersion() {

        return objectSchemaVersion;
    }

    @Nullable
    public UUID getObjectKey() {

        return objectKey;
    }

    @NonNull
    public String getObjectData() {

        return objectData;
    }

    @Nullable
    public List<String> getExclusiveContents() {

        return exclusiveContents;
    }

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" creationDate=");
            stringBuilder.append(creationDate);
            stringBuilder.append(" modificationDate=");
            stringBuilder.append(modificationDate);
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
            stringBuilder.append("OnGetObjectIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    public OnGetObjectIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                         long creationDate, long modificationDate,
                         @NonNull UUID objectSchemaId, int objectSchemaVersion, int objectFlags,
                         @Nullable UUID objectKey, @NonNull String objectData,
                         @Nullable List<String> exclusiveContents) {
        super(serializer, requestId);

        this.creationDate = creationDate;
        this.modificationDate = modificationDate;
        this.objectSchemaId = objectSchemaId;
        this.objectSchemaVersion = objectSchemaVersion;
        this.objectFlags = objectFlags;
        this.objectKey = objectKey;
        this.objectData = objectData;
        this.exclusiveContents = exclusiveContents;
    }
}
