/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Laurent Latil (Laurent.Latil@twin.life)
 */
package org.twinlife.web.kafka;

import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.web.kafka.records.ClickToCallRecord;

public class KafkaRecordSerializer implements Serializer<ClickToCallRecord> {
    private static final Logger Log = LoggerFactory.getLogger(KafkaRecordSerializer.class);

    private final Serialization<ClickToCallRecord> serialization;

    public KafkaRecordSerializer() {
        this(new RecordSerialization());
    }

    public KafkaRecordSerializer(Serialization<ClickToCallRecord> serialization) {
            this.serialization = serialization;
    }

    @Override
    public void close() {
    }

    @Override
    public byte[] serialize(String topic, ClickToCallRecord instance) {
            try {
                    return serialization.serialize(instance);
            } catch (SerializerException e) {
                    final String msg = "Error in serialization of instance " + instance;
                    Log.error(msg, e);
            }
            return null;
    }
}
