/*
 *  Copyright (c) 2022-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.web;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.api.Session.Listener;
import org.libwebsockets.api.ConnectStats;
import org.libwebsockets.api.ErrorStats;
import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.ConnectionStatus;
import org.twinlife.twinlife.SerializerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.Future;

/**
 * Signaling server connection.
 * <p>
 * This is the WebSocket connection to the signaling server.
 * </p>
 */
public class OpenfireConnection extends Connection implements Listener {
    static final Logger Log = LogManager.getLogger(OpenfireConnection.class);

    private final String mIdent;
    private final WebSocketClient mClient;
    private final URI mUrl;
    private final String mDomain;
    private Future<Session> mConnecting;
    @Nullable
    private volatile Session mSession;

    public OpenfireConnection(@NonNull String ident, @NonNull String domain,
                              @NonNull SerializerFactory serializerFactory) {
        super(serializerFactory);

        String name = domain;
        int pos = name.indexOf(':');
        if (pos > 0) {
            name = name.substring(0, pos);
        }
        pos = name.indexOf('.');
        if (pos > 0) {
            name = name.substring(pos + 1);
        }
        mIdent = ident;
        mDomain = name;
        mUrl = URI.create("wss://" + domain + "/twinlife/server");
        mClient = new WebSocketClient();
        try {
            mClient.start();
        } catch (Exception exception) {
            Log.error("Cannot start web socket client", exception);
        }
    }

    /**
     * Returns true if currently connected to the Openfire server.
     *
     * @return true if connected.
     */
    @Override
    public boolean isConnected() {

        return mSession != null;
    }

    /**
     * Get the server domain string.
     *
     * @return the server domain.
     */
    @Override
    @NonNull
    public String getDomain() {

        return mDomain;
    }

    /**
     * Create the connection to the Openfire server.
     *
     * @exception IOException raised if there is a connection error.
     */
    @Override
    public void connect() throws IOException {

        if (mConnecting != null) {
            mConnecting.cancel(true);
        }
        mSession = null;
        try {
            mConnecting = mClient.connect(this, mUrl);
            mSession = mConnecting.get();
            mConnecting = null;

        } catch (Exception exception) {
            Log.error("Connection to signaling server failed: {}", exception.getMessage());
            mConnecting = null;
            throw new IOException(exception);
        }
    }

    /**
     * Sends the specified packet to the server.
     *
     * @param packet the packet to send.
     * @exception IOException raised if there is a connection error.
     */
    @Override
    public void sendDataPacket(byte[] packet) throws IOException {
        Log.debug("{} sendDataPacket {}", mClient, packet.length);

        final Session session = mSession;
        if (session == null) {
            throw new IOException(mClient + " Openfire websocket is closed");
        }

        session.sendBinary(ByteBuffer.wrap(packet), null);
    }

    /**
     * Closes the connection.
     */
    @Override
    public void disconnect() {
        Log.debug("disconnect {}", mClient);

        final Session session = mSession;
        if (session != null) {
            session.close();
            mSession = null;
        }
    }

    /**
     * Get stats collected during establishment of the web socket connection.
     *
     * @return the connection stats or null if we don't have stats.
     */
    @Nullable
    @Override
    public ConnectStats getConnectStats() {

        return null;
    }

    /**
     * Get stats about errors and retries to connect to the server.
     *
     * @param reset when true clear the counters after getting their values.
     * @return the error stats or null if we don't know the errors.
     */
    @Nullable
    @Override
    public ErrorStats getErrorStats(boolean reset) {

        return null;
    }

    /**
     * Get the current connection status.
     *
     * @return the connection status.
     */
    @NonNull
    @Override
    public ConnectionStatus getConnectionStatus() {

        return isConnected() ? ConnectionStatus.CONNECTED : ConnectionStatus.CONNECTING;
    }

    /**
     * Destroy the connection object.
     */
    @Override
    public void destroy() {

    }

    @Override
    public String toString() {

        if (mSession == null) {
            return "WebSocket[NOT_CONNECTED]";
        }

        return "WebSocket[" + mSession + "]";
    }

    /**
     * Called when the web socket connection is closed to notify the connection
     * listener we lost the server connection.
     */
    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        Log.debug("Openfire websocket is closed");

        ProxyEvent.logEvent("openfire-disconnect", "proxy", mIdent);

        mSession = null;
        super.onClose();
    }

    @Override
    public void onWebSocketOpen(@NonNull Session session) {
        final String local = session.getLocalSocketAddress().toString();
        final String remote = session.getRemoteSocketAddress().toString();

        Log.debug("{} socket Connected local={} remote={}", mIdent, local, remote);

        ProxyEvent.logEvent("openfire-connect", "proxy", mIdent,
                "localAddress", local, "removeAddress", remote);
        mSession = session;
        session.demand();
        session.setIdleTimeout(Duration.ofMinutes(10));
    }

    /**
     * <p>A WebSocket BINARY message has been received.</p>
     *
     * @param payload the raw payload array received
     */
    @Override
    public void onWebSocketBinary(ByteBuffer payload, Callback callback) {
        Log.debug("signaling-packet size {}", payload.remaining());

        onBinaryMessageInternal(payload.array(), payload.arrayOffset(), payload.remaining());

        final Session session = mSession;
        if (session != null) {
            session.demand();
        }
    }

    @Override
    public void onWebSocketError(Throwable cause) {

        if (cause != null) {
            Log.error("{} web socket error", mIdent, cause);
            ProxyEvent.logEvent("openfire-error", "proxy", mIdent, "cause", cause.getMessage());
        }
    }

}
