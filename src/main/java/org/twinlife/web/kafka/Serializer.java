/*
 *  Copyright (c) 2024-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Laurent Latil (Laurent.Latil@twin.life)
 */

package org.twinlife.web.kafka;

import java.util.UUID;

import javax.annotation.Nonnull;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;

/**
 * Base class of serializer implementation
 *
 * @param <T>
 *            Type of record handled by this serializer
 */
public abstract class Serializer<T> {

    @Nonnull
    protected final UUID schemaId;

    /** The most recent version of content encoding supported by this serializer */
    protected final int lastSupportedVersion;

    @Nonnull
    protected final Class<? extends T> clazz;

    public Serializer(@Nonnull UUID schemaId, int lastSupportedVersion, @Nonnull Class<? extends T> clazz) {
        this.schemaId = schemaId;
        this.lastSupportedVersion = lastSupportedVersion;
        this.clazz = clazz;
    }

    /**
     * Serialize an object
     *
     * @param encoder used to encode the serialized content
     * @param object object to serialize
     * @param version schema version to serialize
     * @throws SerializerException
     */
    abstract public void serialize(@Nonnull Encoder encoder, @Nonnull T object, int version) throws SerializerException;

    /**
     * Deserialize a content into an object.
     * <p>
     * The object instance has already been created by it
     * (see {@link Serialization#deserialize(Decoder) }
     * </p>
     *
     * @param decoder decoder used to read the serialized content
     * @param object object instance filled by the deserialization process
     * @throws SerializerException
     */
    abstract public void deserialize(@Nonnull Decoder decoder, @Nonnull T object, int version)
            throws SerializerException;

    /**
     * Get the {@link Class} of the entities managed by this serializer
     * @return Class instance
     */
    @Nonnull
    public Class<? extends T> getEntityClass() {
        return clazz;
    }

    @Nonnull
    public final UUID getSchemaId() {
        return schemaId;
    }

    public boolean isSchemaVersionSupported(int version) {
        return version <= lastSupportedVersion;
    }

    public int getCurrentSchemaVersion() {
        return lastSupportedVersion;
    }

    @Override
    public String toString() {
        return "Serializer [ Serialized clazz=" + clazz.getSimpleName() + ", schemaId=" + schemaId + ", lastSupportedVersion=" + lastSupportedVersion + "]";
    }
}