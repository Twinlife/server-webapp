/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Laurent Latil (Laurent.Latil@twin.life)
 */

package org.twinlife.web.kafka;

import org.twinlife.web.kafka.records.ClickToCallAcceptRecord;
import org.twinlife.web.kafka.records.ClickToCallInitiateRecord;
import org.twinlife.web.kafka.records.ClickToCallJoinRoomRecord;
import org.twinlife.web.kafka.records.ClickToCallRecord;

public class RecordSerialization extends Serialization<ClickToCallRecord> {

    public RecordSerialization() {
        // Register all record serializers
        addSerializer(ClickToCallInitiateRecord.SERIALIZER);
        addSerializer(ClickToCallAcceptRecord.SERIALIZER);
        addSerializer(ClickToCallJoinRoomRecord.SERIALIZER);
    }
}
