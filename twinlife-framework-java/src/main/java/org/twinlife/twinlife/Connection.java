/*
 *  Copyright (c) 2021-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.libwebsockets.api.ConnectStats;
import org.libwebsockets.api.ErrorStats;
import org.twinlife.twinlife.util.BinaryCompactDecoder;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.SchemaKey;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class Connection {
    private static final String LOG_TAG = "Connection";
    private static final boolean DEBUG = false;

    @NonNull
    private final SerializerFactory mSerializerFactory;
    @NonNull
    protected final Map<SchemaKey, Pair<Serializer, BinaryPacketListener>> mBinaryListeners = new HashMap<>();
    @Nullable
    protected ConnectionListener mConnectionListener;

    /**
     * Create a new Connection to the Openfire server.
     *
     */
    protected Connection(@NonNull SerializerFactory serializerFactory) {

        mSerializerFactory = serializerFactory;
    }

    /**
     * Returns true if currently connected to the Openfire server.
     *
     * @return true if connected.
     */
    public abstract boolean isConnected();

    /**
     * Create the connection to the Openfire server.
     *
     * @exception IOException raised if there is a connection error.
     */
    public abstract void connect() throws IOException;

    /**
     * Sends the specified packet to the server.
     *
     * @param packet the packet to send.
     * @exception IOException raised if there is a connection error.
     */
    public abstract void sendDataPacket(byte[] packet) throws IOException;

    /**
     * Closes the connection.
     */
    public abstract void disconnect();

    /**
     * Get stats collected during establishment of the web socket connection.
     *
     * @return the connection stats or null if we don't have stats.
     */
    @Nullable
    public abstract ConnectStats getConnectStats();

    /**
     * Get stats about errors and retries to connect to the server.
     *
     * @param reset when true clear the counters after getting their values.
     * @return the error stats or null if we don't know the errors.
     */
    @Nullable
    public abstract ErrorStats getErrorStats(boolean reset);

    /**
     * Destroy the connection object.
     */
    public abstract void destroy();

    /**
     * Get the server domain string.
     *
     * @return the server domain.
     */
    @NonNull
    public abstract String getDomain();

    /**
     * Get the current connection status.
     *
     * @return the connection status.
     */
    @NonNull
    public abstract ConnectionStatus getConnectionStatus();

    /**
     * Sets the connection listener that will be notified when the connection is closed.
     *
     * @param connectionListener a connection listener.
     */
    public void setConnectionListener(ConnectionListener connectionListener) {

        mConnectionListener = connectionListener;
    }

    /**
     * Registers a packet listener with this connection. A packet filter
     * determines which packets will be delivered to the listener. If the same
     * packet listener is added again with a different filter, only the new
     * filter will be used.
     *
     * @param packetListener the packet listener to notify of new received packets.
     */
    public void addPacketListener(@NonNull Serializer serializer, @NonNull BinaryPacketListener packetListener) {

        final SchemaKey key = new SchemaKey(serializer.schemaId, serializer.schemaVersion);

        mBinaryListeners.put(key, new Pair<>(serializer, packetListener));
    }

    /**
     * Called when the web socket connection is closed to notify the connection
     * listener we lost the server connection.
     */
    protected void onClose() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onClose");
        }

        if (mConnectionListener != null) {
            mConnectionListener.onDisconnect();
        }
    }

    protected void onBinaryMessageInternal(@NonNull byte[] data, int offset, int len) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onBinaryMessageInternal: data=" + data);
        }

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data, offset, len);
            BinaryDecoder binaryDecoder = new BinaryCompactDecoder(inputStream);
            UUID schemaId = binaryDecoder.readUUID();
            int version = binaryDecoder.readInt();
            SchemaKey key = new SchemaKey(schemaId, version);
            Pair<Serializer, BinaryPacketListener> listener = mBinaryListeners.get(key);
            if (listener != null) {
                BinaryPacketIQ iq = (BinaryPacketIQ) listener.first.deserialize(mSerializerFactory, binaryDecoder);
                listener.second.processPacket(iq);
            }

        } catch (Exception ex) {
            Log.e(LOG_TAG, "Internal error " + ex.getMessage(), ex);

        }
    }
}
