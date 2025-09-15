/*
 *  Copyright (c) 2024-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Laurent Latil (Laurent.Latil@twin.life)
 */

package org.twinlife.web.kafka;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.util.BinaryEncoder;

public class Serialization<T> {
    private final SerializerFactory serializers = new SerializerFactory();

    /**
     * Register a new record serializer
     * @param serializer serializer implementation
     */
    public void addSerializer(@Nonnull Serializer<? extends T> serializer) {
        serializers.addSerializer(serializer);
    }

    /**
     * Serialize the given object through the specified {@link Encoder}.
     * @param encoder
     *            Encoder used to serialize the object
     * @param obj
     *            Object to serialize
     * @param version
     * Schema version to use for encoding (0 selects the most recent version supported)           
     */
    public void serialize(@Nonnull Encoder encoder, T obj, int version) throws SerializerException {
        @SuppressWarnings("unchecked")
        final Class<T> clazz = (Class<T>) obj.getClass();
        final Serializer<T> serializer = serializers.fromEntityClass(clazz);

        if (serializer == null) {
            throw new SerializerException("No serializer available for class " + clazz.getName());
        }

        if (version > 0) {
            if (!serializer.isSchemaVersionSupported(version)) {
                throw new SerializerException(
                        "Version " + version + " not supported by serializer " + serializer.getClass().getName());
            }
        } else {
            version = serializer.getCurrentSchemaVersion();
        }

        // Write the schema information through encoder
        encoder.writeUUID(serializer.getSchemaId());
        encoder.writeInt(version);

        // Use the serializer implementation to actually write the object content
        serializer.serialize(encoder, obj, version);
    }

    /**
     * Serialize the given object using a {@link BinaryEncoder} encoding.
     * <p>
     * This method is a helper front-end to the more generic {@link #serialize(Encoder, Object)}
     * </p>
     *
     * @param obj Object to serialize
     * @return A byte array containing the binary serialized content
     *
     * @throws SerializerException
     */
    public byte[] serialize(T o, int version) throws SerializerException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(32);
        final Encoder encoder = new BinaryEncoder(outputStream);

        serialize(encoder, o, version);
        return outputStream.toByteArray();
    }

    public final byte[] serialize(T o) throws SerializerException {
        return serialize(o, 0);
    }

    private static class SerializerFactory {
        private static final Logger Log = LoggerFactory.getLogger(SerializerFactory.class);

        /** Map a class to its corresponding Serializer instance (used for serialization)*/
        private final Map<Class<?>, Serializer<?>> mClassToSerializers = new ConcurrentHashMap<>();

        /** Map a schema identifier to the corresponding Serializer instance (used for deserialization)*/
        private final Map<UUID, Serializer<?>> mSchemaIdToSerializers = new ConcurrentHashMap<>();

        /**
         * Register a new {@link Serializer}
         *
         * @param serializer New serializer implementation
         */
        public void addSerializer(@Nonnull Serializer<?> serializer) {
            Log.debug("addSerializer serializer={}", serializer);

            Serializer<?> existing = mClassToSerializers.put(serializer.getEntityClass(), serializer);
            if (existing != null) {
                throw new IllegalStateException(
                        "Existing serializer for entity class " + serializer.getEntityClass().getCanonicalName());
            }

            existing = mSchemaIdToSerializers.put(serializer.getSchemaId(), serializer);
            if (existing != null) {
                throw new IllegalStateException("Existing serializer for schema id " + serializer.getSchemaId());
            }
        }

        /**
         * Get a serializer instance for a given Java class
         *
         * @param <T> type of class represented by the clazz Class object
         * @param clazz Java class
         * @return serializer instance to encode any instance of the clazz class,
         *         or <code>null</code> if none available
         */
        @Nullable
        public <T> Serializer<T> fromEntityClass(@Nonnull Class<T> clazz) {
            Log.debug("getSerializer class={}", clazz);
            final Serializer<?> genericSerializer = mClassToSerializers.get(clazz);

            if (genericSerializer != null && genericSerializer.getEntityClass() != clazz) {
                Log.warn("Serializer implementation for class {} does not match required {} class",
                        genericSerializer.clazz.getName(), clazz.getName());
                return null;
            }

            @SuppressWarnings("unchecked")
            final Serializer<T> typedSerializer = (Serializer<T>) genericSerializer;
            return typedSerializer;
        }

        /**
         * Get a serializer instance from schema identifier & version
         *
         * @param schemaId schema identifier
         * @param schemaVersion schema version
         *
         * @return serializer instance to encode any instance of the clazz class,
         *         or <code>null</code> if none available
         */
        @Nullable
        public Serializer<?> fromSchema(@Nonnull UUID schemaId) {
            return mSchemaIdToSerializers.get(schemaId);
        }
    }
}
