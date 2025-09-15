/*
 *  Copyright (c) 2022-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.web;

import java.net.InetAddress;
import java.time.Duration;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketOpen;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.api.exceptions.CloseException;
import org.eclipse.jetty.websocket.api.exceptions.WebSocketTimeoutException;
import org.twinlife.web.messages.ErrorMessage;
import org.twinlife.web.util.ClientAddressFinder;
import org.twinlife.web.util.Json;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A web client session that is connected through a WebSocket connection.
 *
 * The WebSocket connection is used to allow to web client to make WebRTC calls.
 * It maintains a list of active P2P sessions and is responsible for forwarding
 * the session-initiate/accept/update/transport/terminate through the Openfire
 * connection.
 */
@WebSocket
public class WebSocketClientSession {
    static final Logger Log = LogManager.getLogger(WebSocketClientSession.class);

    static final String MAGIC_PREFIX = "id-";

    @Nullable
    private ClientSession mClient;
    @NonNull
    private String mClientId;
    @Nullable
    private InetAddress mClientAddress;
    @NonNull
    private final ClientAddressFinder mClientAddressFinder;
    @Nullable
    private Session mSession;

    /**
     * Create a new client session
     *
     * @param clientAddressFinder Instance of {@link ClientAddressFinder} used to retrieve the IP address of client device
     */
    public WebSocketClientSession(@NonNull ClientAddressFinder clientAddressFinder) {
        mClientId = "new client";
        mClientAddressFinder = clientAddressFinder;
    }

    /**
     * WebSocketAdapter operations
     */

    @OnWebSocketOpen
    public void onOpen(@NonNull Session session) {
        Log.debug("onWebSocketConnect {}", session);

        session.setIdleTimeout(Duration.ofMinutes(5));

        mClientAddress = mClientAddressFinder.getClientAddressFromSession(session);
        mSession = session;
    }

    @OnWebSocketMessage
    public void onWebSocketText(@NonNull String message) {
        Log.debug("{} onWebSocketText {}", mClientId, message);

        try {
            final JsonNode jsonNode = Json.getObjectReader().readTree(message);
            final JsonNode msg = jsonNode.get("msg");
            if (msg == null || !msg.isTextual()) {
                Log.debug("Missing 'msg' in message {}", message);
                sendMessage(new ErrorMessage("Invalid message"));
                return;
            }

            // First message must be a session-request so that we configure the mClient instance.
            if (mClient == null) {
                if (!"session-request".equals(msg.asText())) {
                    sendMessage(new ErrorMessage("No client session"));
                    return;
                }

                String sessionId = Json.getString(jsonNode, "session-id");
                if (sessionId == null || !sessionId.startsWith(MAGIC_PREFIX)) {
                    sessionId = UUID.randomUUID().toString();
                }
                mClient = ProxyController.createClient(sessionId);
                mClientId = mClient.mClientId;

                mClient.setSession(this, mClientAddress);
                ProxyEvent.logEvent("connect", "clientId", mClientId,
                        "address", mClientAddress == null ? "?" : mClientAddress.getHostAddress(),
                        "session-id", sessionId);
            }
            mClient.onMessage(msg.asText(), jsonNode);

        } catch (Exception exception) {
            Log.error("Invalid message {}", message, exception);
            sendMessage(new ErrorMessage("Server internal error"));
        }
    }

    @OnWebSocketClose
    public void onWebSocketClose(Session session, int statusCode, String reason) {
        Log.debug("onWebSocketClose {} reason {}", statusCode, reason);

        final boolean released;
        if (mClient != null) {
            released = mClient.close(statusCode, reason);
            ProxyController.releaseClient(mClient, released);
        } else {
            released = false;
        }
        if (reason != null) {
            ProxyEvent.logEvent("disconnect", "clientId", mClientId,
                    "statusCode", Integer.toString(statusCode), "reason", reason,
                    "released", released ? "yes" : "no");
        } else {
            ProxyEvent.logEvent("disconnect", "clientId", mClientId,
                    "statusCode", Integer.toString(statusCode),
                    "released", released ? "yes" : "no");
        }

        if (session != null) {
            session.close();
        }
        mSession = null;
    }

    @OnWebSocketError
    public void onWebSocketError(Session session, Throwable cause) {
        Log.debug("onWebSocketError", cause);

        if (!isIgnored(cause)) {
            ProxyEvent.logEvent("disconnect", "clientId", mClientId,
                    "exception", cause.getMessage());
        }

        if (session != null) {
            Log.info("Error on websocket connection {}", mClientId);
            session.close();
        }
    }

    private static boolean isIgnored(@Nullable Throwable cause) {

        if (cause instanceof WebSocketTimeoutException) {
            return true;
        }
        if (cause instanceof CloseException) {
            return true;
        }
        return cause != null && cause.getMessage() != null;
    }

    /**
     * Send the message to the web socket connection as a JSON content.
     *
     * @param object the object to send.
     */
    boolean sendMessage(@NonNull Object object) {
        Log.debug("Send message {}", object);

        try {
            final String json = Json.getObjectWriter().writeValueAsString(object);
            Log.debug("{} send message {}", mClientId, json);
            if (mSession != null) {
                mSession.sendText(json, null);
            }
            return true;

        } catch (Exception exception) {
            Log.error("sendMessage failed", exception);
            return false;
        }
    }

    @Override
    public String toString() {
        return "ClientSession{" + "mClientId='" + mClientId + '\'' + '}';
    }
}