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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * List object response IQ.
 * <p>
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"76b7a7e2-cd6d-40da-b556-bcbf7eb56da4",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnListObjectIQ",
 *  "namespace":"org.twinlife.schemas.repository",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"objectId", "type": "uuid"}
 *  ]
 * }
 * </pre>
 */
public class OnListObjectIQ extends BinaryPacketIQ {

    static class OnListObjectIQSerializer extends BinaryPacketIQSerializer {

        OnListObjectIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnListObjectIQ.class);
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

            int count = decoder.readInt();
            List<UUID> objectIds = new ArrayList<>(count);
            while (count > 0) {
                count--;
                objectIds.add(decoder.readUUID());
            }

            return new OnListObjectIQ(this, serviceRequestIQ.getRequestId(), objectIds);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnListObjectIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    private final List<UUID> objectIds;

    @NonNull
    public List<UUID> getObjectIds() {

        return objectIds;
    }

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" objectIds=");
            stringBuilder.append(objectIds);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnListObjectIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    public OnListObjectIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                          @NonNull List<UUID> objectIds) {
        super(serializer, requestId);

        this.objectIds = objectIds;
    }
}
