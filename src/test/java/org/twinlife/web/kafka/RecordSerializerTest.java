/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Laurent Latil (Laurent.Latil@twin.life)
 */

package org.twinlife.web.kafka;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.twinlife.web.kafka.records.ClickToCallAcceptRecord;
import org.twinlife.web.kafka.records.ClickToCallInitiateRecord;
import org.twinlife.web.kafka.records.ClickToCallJoinRoomRecord;
import org.twinlife.web.kafka.records.ClickToCallRecord;

public class RecordSerializerTest {

    private static final String SERIALIZED_CONTENT_DIR_PROPERTY = "tests.serializer.content.dir";
    private static final String SERIALIZED_CONTENT_SUFFIX = ".serialized.bin";

    private static final String TOPIC_NAME = "topic";
        
    private static final UUID TWINCODE_ID = UUID.fromString("d284d5c4-10c0-44ef-9a4a-fa05d58ebc4d");
    private static final UUID P2P_SESSION_ID = UUID.fromString("641f9d2a-3807-40d3-9f6e-dbea05fd1c4d");
    private static final UUID CALL_ROOM_ID = UUID.fromString("bfc8e745-ab82-4903-bb80-eb8bce10bb41");

    private static final String IPADDR_V4="192.168.1.1";

    private InetAddress InetAddrV4;
    
    private File serializedContentDir;
    private KafkaRecordSerializer recordSerializer;

    @Before
    public void setUp() throws Exception {
        final String outDir = System.getProperty(SERIALIZED_CONTENT_DIR_PROPERTY);
        org.junit.Assume.assumeTrue("Test skipped as serialized content output dir is not defined. Set "
                + SERIALIZED_CONTENT_DIR_PROPERTY + " property", outDir != null);

        serializedContentDir = new File(outDir);
        recordSerializer = new KafkaRecordSerializer();
        
        InetAddrV4 = InetAddress.getByName(IPADDR_V4);
    }

    @Test
    public void testClickToCallInitiateRecordSerialization() throws IOException {
        final ClickToCallInitiateRecord record = new ClickToCallInitiateRecord(P2P_SESSION_ID, TWINCODE_ID, InetAddrV4 );
        serializeRecord(record);
    }

    @Test
    public void testClickToCallAcceptRecordSerialization() throws IOException {
        final ClickToCallAcceptRecord record = new ClickToCallAcceptRecord(P2P_SESSION_ID, TWINCODE_ID, InetAddrV4 );
        serializeRecord(record);
    }
    
    @Test
    public void testClickToCallJoinRoomRecordSerialization() throws IOException {
        final ClickToCallJoinRoomRecord record = new ClickToCallJoinRoomRecord(P2P_SESSION_ID, TWINCODE_ID, InetAddrV4,
                CALL_ROOM_ID);
        serializeRecord(record);
    }
    
    @Test
    public void testClickToCallJoinRoomRecordSerializationWithNullSession() throws IOException {
        final ClickToCallJoinRoomRecord record = new ClickToCallJoinRoomRecord(null, TWINCODE_ID, InetAddrV4,
                CALL_ROOM_ID);
        serializeRecord(record, "UnknownSession");
    }

    private void serializeRecord(ClickToCallRecord record) throws IOException {
        serializeRecord(record, null);
    }

    private void serializeRecord(ClickToCallRecord record, String serializationNameSuffix) throws IOException {
        final byte[] content = recordSerializer.serialize(TOPIC_NAME, record);
        final File outputFile = new File(serializedContentDir, getSerializedContentFileName(record.getClass(), serializationNameSuffix));

        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            outputStream.write(content);
        }
    }

    private static String getSerializedContentFileName(Class<?> clazz, String suffix) {
        return clazz.getSimpleName() + ((suffix!=null)? "-"+suffix: "") + SERIALIZED_CONTENT_SUFFIX;
    }
}
