/*
 *  Copyright (c) 2022-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 *   Laurent Latil (Laurent.Latil@twin.life)
 */

package org.twinlife.web;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.Configuration;
import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.EngineTwinlifeImpl;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.ManagementService;
import org.twinlife.twinlife.PushNotificationContent;
import org.twinlife.twinlife.Offer;
import org.twinlife.twinlife.OfferToReceive;
import org.twinlife.twinlife.PeerCallService;
import org.twinlife.twinlife.PeerSignalingListener;
import org.twinlife.twinlife.PropertiesConfigurationServiceImpl;
import org.twinlife.twinlife.Sdp;
import org.twinlife.twinlife.SdpType;
import org.twinlife.twinlife.TerminateReason;
import org.twinlife.twinlife.TransportCandidateList;
import org.twinlife.twinlife.TwincodeFactory;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwinlifeContext;
import org.twinlife.twinlife.TwinlifeContextImpl;
import org.twinlife.twinlife.job.EngineJobServiceImpl;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.web.executors.CreateTwincodeExecutor;
import org.twinlife.web.executors.GetTwincodeFactoryPools;
import org.twinlife.web.models.TwincodeFactoryPool;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Web application proxy.
 *
 * Each instance of `ProxyApplication` has a dedicated connection to the Openfire server with
 * a dedicated proxy account.  It exposes the main entry points to for the Web proxy to either
 * serve REST APIs or handle the ClientSession to make WebRTC calls.
 */
public class ProxyApplication implements PeerSignalingListener, PeerCallService.ServiceObserver {
    static final Logger Log = LogManager.getLogger(ProxyApplication.class);

    private final EngineJobServiceImpl mJobServiceImpl;
    private final PropertiesConfigurationServiceImpl mConfigurationService;
    private final TwinlifeContextImpl mTwinlifeContext;
    private final EngineTwinlifeImpl mTwinlifeImpl;
    private final Map<UUID, List<ClientSession>> mActiveSessions;
    private final Map<UUID, ClientSession> mTwincodeInboundSessions;
    private final Map<UUID, List<ClientSession>> mActiveCallRooms;
    private final Map<Long, ClientSession> mActiveRequests;
    private final List<TwincodeFactory> mTwincodePool;
    private final List<TwincodeFactoryPool> mTwincodeFactoryPools;
    private final AtomicLong mClientId;
    private final String mProxyIdent;

    private class TwinlifeContextObserver extends TwinlifeContext.DefaultObserver {
        @Override
        public void onTwinlifeReady() {
            ProxyApplication.this.onTwinlifeReady();
        }

        @Override
        public void onFatalError(ErrorCode errorCode) {
            Log.error("{} fatal error {}", mProxyIdent, errorCode);
        }
    }

    public ProxyApplication(@NonNull ProxyConfiguration configuration, @NonNull File root) {

        if (!root.exists() && !root.mkdirs()) {
            Log.error("Cannot create directory {}", root);
        }

        mProxyIdent = root.getName();
        Log.info("{} proxy started in {}", mProxyIdent, root);

        mClientId = new AtomicLong();
        mActiveSessions = new ConcurrentHashMap<>();
        mActiveRequests = new ConcurrentHashMap<>();
        mTwincodeInboundSessions = new ConcurrentHashMap<>();
        mActiveCallRooms = new HashMap<>();
        mJobServiceImpl = new EngineJobServiceImpl();
        mTwincodePool = new ArrayList<>();
        mTwincodeFactoryPools = new ArrayList<>();
        mConfigurationService = new PropertiesConfigurationServiceImpl("webapp", new File(root, "config"), configuration.getSecretKey());
        Context context = new Context() {
            public File getDatabasePath(String name) {
                return new File(root, name);
            }
        };

        mTwinlifeContext = new TwinlifeContextImpl(configuration, mJobServiceImpl, mConfigurationService);
        mTwinlifeContext.setObserver(new TwinlifeContextObserver());
        File cacheDir = new File(root, "cache");
        File filesDir = new File(root, "files");
        mTwinlifeImpl = new EngineTwinlifeImpl(context, mConfigurationService, mTwinlifeContext, filesDir, cacheDir,
                new DefaultImageTools()) {
            protected Connection getConnection() {
                return new OpenfireConnection(mProxyIdent, configuration.server, getSerializerFactory());
            }
        };
        mTwinlifeContext.onServiceConnected(mTwinlifeImpl);

        ProxyEvent.logEvent("start", "root", root.getPath());
    }

    /**
     * Allocate a unique identifier for the web app proxy client.
     *
     * @return the proxy client identifier.
     */
    @NonNull
    String allocateIdentifier() {

        long id = mClientId.incrementAndGet();
        return mProxyIdent + "_" + id;
    }

    public void getTwincode(@NonNull UUID twincodeOutboundId,
                            @NonNull Consumer<TwincodeOutbound> complete) {
        Log.debug("getTwincode {}", twincodeOutboundId);

        final TwincodeOutboundService twincodeOutboundService = mTwinlifeImpl.getTwincodeOutboundService();
        twincodeOutboundService.getTwincode(twincodeOutboundId, 0, complete);
    }

    public void getImage(@NonNull UUID imageId,
                         @NonNull Consumer<Bitmap> complete) {

        final ImageService imageService = mTwinlifeImpl.getImageService();
        imageService.getImage(imageId, ImageService.Kind.THUMBNAIL, complete);
    }

    /**
     * Allocate a twincode for a call that a client wants to initiate.
     *
     * @param complete
     */
    public void allocateCallTwincodeFactory(@NonNull ClientSession client,
                                            @NonNull Consumer<TwincodeFactory> complete) {
        Log.debug("{} allocateCallTwincode", mProxyIdent);

        TwincodeFactory twincodeFactory;
        synchronized (mTwincodePool) {
            if (!mTwincodePool.isEmpty()) {
                twincodeFactory = mTwincodePool.remove(mTwincodePool.size() - 1);
            } else {
                twincodeFactory = null;
            }
        }

        if (twincodeFactory != null) {
            final UUID twincodeInboundId = twincodeFactory.getTwincodeInbound().getId();
            mTwincodeInboundSessions.put(twincodeInboundId, client);
            complete.onGet(ErrorCode.SUCCESS, twincodeFactory);
            return;
        }

        // Pick a twincode factory pool object.
        TwincodeFactoryPool pool = null;
        synchronized (mTwincodeFactoryPools) {
            for (TwincodeFactoryPool factoryPool : mTwincodeFactoryPools) {
                if (pool == null || factoryPool.isSmaller(pool)) {
                    pool = factoryPool;
                }
            }
        }
        if (pool == null) {
            Log.error("{} No twincode factory pool configured yet", mProxyIdent);
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        new CreateTwincodeExecutor(mTwinlifeContext, pool, (ErrorCode errorCode, TwincodeFactory factory) -> {
            if (errorCode == ErrorCode.SUCCESS && factory != null) {
                final UUID twincodeInboundId = factory.getTwincodeInbound().getId();
                mTwincodeInboundSessions.put(twincodeInboundId, client);
            }
            complete.onGet(errorCode, factory);
        });
    }

    /**
     * Release a twincode factory that was obtained with allocateCallTwincodeFactory.
     *
     * @param twincodeFactory the twincode factory to put back in the pool.
     */
    public void releaseCallTwincode(@NonNull TwincodeFactory twincodeFactory) {
        Log.debug("{} releaseCallTwincode factory={}", mProxyIdent, twincodeFactory);

        final UUID twincodeInboundId = twincodeFactory.getTwincodeInbound().getId();
        mTwincodeInboundSessions.remove(twincodeInboundId);

        synchronized (mTwincodePool) {
            mTwincodePool.add(twincodeFactory);
        }
    }

    /**
     * Get the configuration to establish a P2P WebRTC connection.
     *
     * @return the configuration information or null.
     */
    @Nullable
    public Configuration getConfiguration() {
        Log.debug("{} getConfiguration", mProxyIdent);

        final ManagementService managementService = mTwinlifeImpl.getManagementService();
        if (managementService == null) {

            return null;
        }

        return managementService.getConfiguration();
    }

    @Nullable
    public String getPeerId(@NonNull UUID peerTwincodeOutboundId, @NonNull UUID twincodeOutboundId) {

        final TwincodeOutboundService twincodeOutboundService = mTwinlifeImpl.getTwincodeOutboundService();
        return twincodeOutboundService.getPeerId(peerTwincodeOutboundId, twincodeOutboundId);
    }

    /**
     * Send the session-initiate to start a P2P connection with the peer.
     *
     * @param sessionId the P2P session id.
     * @param to the peer identification string.
     * @param sdp the SDP to send.
     * @param offer the offer.
     * @param offerToReceive the offer to receive.
     * @param maxReceivedFrameSize the max receive frame size that we accept.
     * @param maxReceivedFrameRate the max receive frame rate that we accept.
     * @param notificationContent information for the push notification.
     * @param onComplete the completion handler executed when the server sends us its response.
     */
    public void sessionInitiate(@NonNull ClientSession client, @NonNull UUID sessionId, @NonNull String to,
                                @NonNull Sdp sdp, @NonNull Offer offer, @NonNull OfferToReceive offerToReceive,
                                int maxReceivedFrameSize, int maxReceivedFrameRate,
                                @NonNull PushNotificationContent notificationContent,
                                @NonNull Consumer<Long> onComplete) {

        addActiveSession(sessionId, client);

        UUID callRoomId = null;

        final int sep = to.indexOf('@');
        final String domain = to.substring(sep + 1);
        int pos = domain.indexOf('.');
        if (pos > 0 && domain.startsWith(".callroom", pos)) {
            callRoomId = Utils.UUIDFromString(domain.substring(0, pos));
        }

        if (callRoomId != null) {
            List<ClientSession> callRoomSessions = mActiveCallRooms.get(callRoomId);
            if (callRoomSessions != null) {
                for (ClientSession callRoomSession : callRoomSessions) {
                    if (to.equals(callRoomSession.getCallRoomMemberId())) {
                        addActiveSession(sessionId, callRoomSession);
                        ErrorCode res = callRoomSession.onSessionInitiate(sessionId, client.getCallRoomMemberId(), sdp, offer,
                                offerToReceive, maxReceivedFrameSize, maxReceivedFrameRate);
                        onComplete.onGet(res, null);

                        final Consumer<Long> originalOnComplete = onComplete;

                        // We don't want to send session-initiate-response twice, except if an error occurred on Openfire.
                        onComplete = (status, object) -> {
                            if (status == ErrorCode.SUCCESS) {
                                Log.debug("{} received SUCCESS ack for local session-initiate", mProxyIdent);
                                return;
                            }
                            originalOnComplete.onGet(status, object);
                        };
                    }
                }
            }
        }

        final PeerCallService callService = mTwinlifeImpl.getPeerCallService();
        callService.sessionInitiate(sessionId, client.getCallRoomMemberId(), to, sdp, offer,
                offerToReceive, maxReceivedFrameSize, maxReceivedFrameRate, notificationContent, onComplete);
    }

    /**
     * Send the session-accept to accept an incoming P2P connection with the peer.
     *
     * @param sessionId the P2P session id.
     * @param to the peer identification string.
     * @param sdpAnswer the SDP to send.
     * @param offer the offer.
     * @param offerToReceive the offer to receive.
     * @param maxReceivedFrameSize the max receive frame size that we accept.
     * @param maxReceivedFrameRate the max receive frame rate that we accept.
     * @param onComplete the completion handler executed when the server sends us its response.
     */
    public void sessionAccept(@NonNull UUID sessionId, @Nullable String from, @NonNull String to, @NonNull Sdp sdpAnswer,
                              @NonNull Offer offer, @NonNull OfferToReceive offerToReceive,
                              int maxReceivedFrameSize, int maxReceivedFrameRate,
                              @NonNull Consumer<Long> onComplete) {
        ClientSession localPeer = getLocalPeer(sessionId, to);

        if (localPeer != null) {
            ErrorCode res = localPeer.onSessionAccept(sessionId, sdpAnswer, offer, offerToReceive, maxReceivedFrameSize, maxReceivedFrameRate);
            onComplete.onGet(res, null);
        } else {
            final PeerCallService callService = mTwinlifeImpl.getPeerCallService();
            callService.sessionAccept(sessionId, from, to, sdpAnswer, offer, offerToReceive,
                    maxReceivedFrameSize, maxReceivedFrameRate, onComplete);
        }
    }

    /**
     * Send the transport info for the P2P session to the peer.
     *
     * @param sessionId the P2P session id.
     * @param to the peer identification string.
     * @param candidates the list of candidates.
     * @param onComplete the completion handler executed when the server sends us its response.
     */
    public void transportInfo(@NonNull UUID sessionId, @NonNull String to,
                              @NonNull TransportCandidateList candidates,
                              @NonNull Consumer<Long> onComplete) {
        ClientSession localPeer = getLocalPeer(sessionId, to);
        final long requestId = mTwinlifeImpl.newRequestId();
        Sdp sdp = candidates.buildSdp(requestId);

        if (localPeer != null) {
            ErrorCode res = localPeer.onTransportInfo(sessionId, sdp);
            onComplete.onGet(res, null);
        } else {
            final PeerCallService callService = mTwinlifeImpl.getPeerCallService();
            callService.transportInfo(requestId, sessionId, to, sdp, onComplete);
        }
    }

    /**
     * Send the session-update to ask for a renegotiation with the peer.
     *
     * @param sessionId the P2P session id.
     * @param to the peer identification string.
     * @param sdp the sdp to send.
     * @param type the update type to indicate whether this is an offer or answer.
     * @param onComplete the completion handler executed when the server sends us its response.
     */
    public void sessionUpdate(@NonNull UUID sessionId, @NonNull String to, @NonNull Sdp sdp, @NonNull SdpType type,
                              @NonNull Consumer<Long> onComplete) {
        ClientSession localPeer = getLocalPeer(sessionId, to);

        if (localPeer != null) {
            ErrorCode res = localPeer.onSessionUpdate(sessionId, type, sdp);
            onComplete.onGet(res, null);
        } else {
            final PeerCallService callService = mTwinlifeImpl.getPeerCallService();
            callService.sessionUpdate(sessionId, to, sdp, type, onComplete);
        }
    }

    /**
     * Send the session-terminate to the peer to close the P2P connection.
     *
     * Note: we don't need any onComplete listener because the server never returns an error.
     *
     * @param sessionId the P2P session id.
     * @param to the peer identification string.
     * @param reason the reason for the termination.
     */
    public void sessionTerminate(@NonNull UUID sessionId, @NonNull String to, @NonNull TerminateReason reason) {
        ClientSession localPeer = getLocalPeer(sessionId, to);

        if (localPeer != null) {
            localPeer.onSessionTerminate(sessionId, reason);
        }

        //Send the SessionTerminateIQ even when the peer is local, because Openfire has some cleanup to do.
        final PeerCallService callService = mTwinlifeImpl.getPeerCallService();
        callService.sessionTerminate(sessionId, to, reason, (ErrorCode errorCode, Long requestId) -> {
            if (errorCode != ErrorCode.TWINLIFE_OFFLINE) {
                mActiveSessions.remove(sessionId);
            }
        });
    }

    /**
     * Join the call room after having received an invitation through `onInviteCallRoom`.
     * The `twincodeOut` must be owned by the current user and represents the current user in the call room.
     *
     * @param callRoomId the call room to join.
     * @param twincodeOut the member twincode.
     * @param p2pSession the optional P2P session that we have with the given twincode.
     */
    public void joinCallRoom(@NonNull UUID callRoomId, @NonNull UUID twincodeOut, @Nullable UUID p2pSession) {
        final PeerCallService callService = mTwinlifeImpl.getPeerCallService();
        long requestId = mTwinlifeImpl.newRequestId();
        if (p2pSession != null) {
            ClientSession session = getActiveSession(p2pSession);
            if (session != null) {
                mActiveRequests.put(requestId, session);
            }
        }
        callService.joinCallRoom(requestId, callRoomId, twincodeOut,
                Collections.singletonList(new Pair<>(p2pSession, null)));
    }

    /**
     * Leave the call room.
     *
     * @param callRoomId the call room to leave.
     * @param memberId   the member id to remove.
     */
    public void leaveCallRoom(@NonNull UUID callRoomId, @Nullable String memberId) {
        if (memberId != null) {
            final PeerCallService callService = mTwinlifeImpl.getPeerCallService();
            callService.leaveCallRoom(mTwinlifeImpl.newRequestId(), callRoomId, memberId);
        }
        synchronized (mActiveCallRooms) {
            List<ClientSession> sessions = mActiveCallRooms.get(callRoomId);
            if (sessions != null) {
                for (ClientSession session : sessions) {
                    if (session.getCallRoomMemberId() != null && session.getCallRoomMemberId().equals(memberId)) {
                        sessions.remove(session);
                        break;
                    }
                }
                if (sessions.isEmpty()) {
                    mActiveCallRooms.remove(callRoomId);
                }
            }
        }
    }

    public void inviteCallRoom(@NonNull UUID callRoomId, @NonNull UUID twincodeOut, @Nullable UUID p2pSession) {
        final PeerCallService callService = mTwinlifeImpl.getPeerCallService();
        callService.inviteCallRoom(mTwinlifeImpl.newRequestId(), callRoomId, twincodeOut, p2pSession);
    }

    /**
     * PeerSignalingListener
     */

    /**
     * Called when a session-initiate IQ is received.
     *
     * @param sessionId the P2P session id.
     * @param from the target identification string.
     * @param to the source/originator identification string.
     * @param sdp the sdp content (clear text | compressed | encrypted).
     * @param offer the offer.
     * @param offerToReceive the offer to receive.
     * @param maxReceivedFrameSize the max received frame size.
     * @param maxReceivedFrameRate the max received frame rate.
     * @return SUCCESS, NO_PERMISSION, ITEM_NOT_FOUND if the session id is not known, or null if the peer is local.
     */
    @Override
    @Nullable
    public ErrorCode onSessionInitiate(@NonNull UUID sessionId, @NonNull String from, @NonNull String to,
                                       @NonNull Sdp sdp, @NonNull Offer offer,
                                       @NonNull OfferToReceive offerToReceive,
                                       int maxReceivedFrameSize, int maxReceivedFrameRate) {
        Log.debug("{} Received session-initiate {} from {} to {}", mProxyIdent, sessionId, from, to);

        if (isLocalSession(sessionId)) {
            return null;
        }

        final int sep = from.indexOf('@');
        final String domain = from.substring(sep + 1);
        if (domain.startsWith("inbound.twincode.twinlife")) {
            final UUID twincodeInboundId = Utils.UUIDFromString(from.substring(0, sep));
            if (twincodeInboundId == null) {

                return ErrorCode.ITEM_NOT_FOUND;
            }

            final ClientSession clientSession = mTwincodeInboundSessions.get(twincodeInboundId);
            if (clientSession == null) {

                return ErrorCode.ITEM_NOT_FOUND;
            }

            addActiveSession(sessionId, clientSession);
            return clientSession.onSessionInitiate(sessionId, from, sdp, offer, offerToReceive,
                    maxReceivedFrameSize, maxReceivedFrameRate);
        }

        int pos = domain.indexOf('.');
        if (pos > 0 && domain.startsWith(".callroom", pos)) {
            final UUID callroomId = Utils.UUIDFromString(domain.substring(0, pos));
            if (callroomId == null) {

                return ErrorCode.ITEM_NOT_FOUND;
            }

            ClientSession session = null;

            synchronized (mActiveCallRooms) {
                final List<ClientSession> clientSessions = mActiveCallRooms.get(callroomId);
                if (clientSessions == null || clientSessions.isEmpty()) {
                    Log.debug("sessionInitiate: CallRoom {} not found", callroomId);
                    return ErrorCode.ITEM_NOT_FOUND;
                }

                if (clientSessions.size() == 1) {
                    Log.debug("sessionInitiate: CallRoom {} has only 1 client", callroomId);

                    session = clientSessions.get(0);
                } else {
                    Log.debug("sessionInitiate: CallRoom {} has {} clients", callroomId, clientSessions.size());
                    for (ClientSession s : clientSessions) {
                        Log.debug("sessionInitiate: checking client {} with memberId: {}", s.mClientId, s.getCallRoomMemberId());
                        if (to.equals(s.getCallRoomMemberId())) {
                            Log.debug("sessionInitiate: client {} found", s.mClientId);
                            session = s;
                            break;
                        }
                    }
                }
            }


            if (session != null) {
                Log.debug("Got session initiate with from: {}, sending it to memberId: {}", from, session.getCallRoomMemberId());
                addActiveSession(sessionId, session);
                return session.onSessionInitiate(sessionId, from, sdp, offer, offerToReceive,
                        maxReceivedFrameSize, maxReceivedFrameRate);
            }

        }

        // The WebProxy cannot receive an incoming call.
        Log.error("{} Dropping session-initiate {} from {} to {}", mProxyIdent, sessionId, from, to);
        return ErrorCode.ITEM_NOT_FOUND;
    }

    /**
     * Called when a session-accept IQ is received.
     *
     * @param sessionId the P2P session id.
     * @param sdp the sdp content (clear text | compressed | encrypted).
     * @param offer the offer.
     * @param offerToReceive the offer to receive.
     * @param maxReceivedFrameSize the max received frame size.
     * @param maxReceivedFrameRate the max received frame rate.
     * @return SUCCESS, NO_PERMISSION, ITEM_NOT_FOUND if the session id is not known.
     */
    @Override
    @NonNull
    public ErrorCode onSessionAccept(@NonNull UUID sessionId, @NonNull String to, @NonNull Sdp sdp, @NonNull Offer offer,
                                     @NonNull OfferToReceive offerToReceive, int maxReceivedFrameSize,
                                     int maxReceivedFrameRate) {
        Log.debug("{} onSessionAccept sessionId={} sdp={} offer={}", mProxyIdent, sessionId, sdp, offer);

        if (isLocalSession(sessionId)) {
            return ErrorCode.SUCCESS;
        }

        final ClientSession clientSession = getActiveSession(sessionId, to);
        if (clientSession == null) {

            Log.warn("{} No active client for accepted session {}", mProxyIdent, sessionId);
            return ErrorCode.ITEM_NOT_FOUND;
        }

        // Forward the session-accept to the connected client.
        final ErrorCode result = clientSession.onSessionAccept(sessionId, sdp, offer,
                offerToReceive, maxReceivedFrameSize, maxReceivedFrameRate);
        if (result == ErrorCode.ITEM_NOT_FOUND) {
            mActiveSessions.remove(sessionId);
            Log.warn("{} Session {} was dropped from ClientSession", mProxyIdent, sessionId);
        }
        return result;
    }

    /**
     * Called when a session-update IQ is received.
     *
     * @param sessionId the P2P session id.
     * @param updateType whether this is an offer or an answer.
     * @param sdp the sdp content (clear text | compressed | encrypted).
     * @return SUCCESS or ITEM_NOT_FOUND if the session id is not known.
     */
    @Override
    @NonNull
    public ErrorCode onSessionUpdate(@NonNull UUID sessionId, @NonNull SdpType updateType, @NonNull Sdp sdp) {
        Log.debug("{} onSessionUpdate sessionId={} updateType={} sdp={}", mClientId, sessionId, updateType, sdp);

        if (isLocalSession(sessionId)) {
            return ErrorCode.SUCCESS;
        }

        final ClientSession clientSession = getActiveSession(sessionId);
        if (clientSession == null) {

            Log.warn("{} No active client for update session {}", mProxyIdent, sessionId);
            return ErrorCode.ITEM_NOT_FOUND;
        }

        // Forward the session-update to the connected client.
        final ErrorCode result = clientSession.onSessionUpdate(sessionId, updateType, sdp);
        if (result == ErrorCode.ITEM_NOT_FOUND) {
            mActiveSessions.remove(sessionId);
            Log.warn("{} Session {} was dropped from ClientSession", mProxyIdent, sessionId);
        }
        return result;
    }

    /**
     * Called when a transport-info IQ is received with a list of candidates.
     *
     * @param sessionId the P2P session id.
     * @param sdp the list of candidates.
     * @return SUCCESS or ITEM_NOT_FOUND if the session id is not known.
     */
    @Override
    @NonNull
    public ErrorCode onTransportInfo(@NonNull UUID sessionId, @NonNull Sdp sdp) {
        Log.debug("{} onTransportInfo sessionId={} candidates={}", mClientId, sessionId, sdp);

        if (isLocalSession(sessionId)) {
            return ErrorCode.SUCCESS;
        }

        final ClientSession clientSession = getActiveSession(sessionId);
        if (clientSession == null) {

            Log.warn("{} No active client for transport-info {}", mProxyIdent, sessionId);
            return ErrorCode.ITEM_NOT_FOUND;
        }

        // Forward the session-update to the connected client.
        final ErrorCode result = clientSession.onTransportInfo(sessionId, sdp);
        if (result == ErrorCode.ITEM_NOT_FOUND) {
            mActiveSessions.remove(sessionId);
            Log.warn("{} Session {} was dropped from ClientSession", mProxyIdent, sessionId);
        }
        return result;
    }

    /**
     * Called when a session-terminate IQ is received for the given P2P session.
     *
     * @param sessionId the P2P session id.
     * @param reason the terminate reason.
     */
    @Override
    public void onSessionTerminate(@NonNull UUID sessionId, @NonNull TerminateReason reason) {
        Log.debug("{} onSessionTerminate sessionId={} reason={}", mProxyIdent, sessionId, reason);

        if (isLocalSession(sessionId)) {
            // This is the server's response, we've already dealt with the local peer, we just need to clean up.
            mActiveSessions.remove(sessionId);
            return;
        }

        ClientSession clientSession = getActiveSession(sessionId);
        if (clientSession == null) {
            Log.debug("{} No active client for terminated session {}", mProxyIdent, sessionId);
            return;
        }

        // Forward the session-terminate to the connected client.
        clientSession.onSessionTerminate(sessionId, reason);
        mActiveSessions.remove(sessionId);
    }

    @Override
    public void onDeviceRinging(@NonNull UUID sessionId) {
        Log.debug("{} onDeviceRinging sessionId={}", mProxyIdent, sessionId);

        if (isLocalSession(sessionId)) {
            return;
        }

        ClientSession session = getActiveSession(sessionId);
        if (session == null) {
            Log.debug("{} No active client for ringing session {}", mProxyIdent, sessionId);
            return;
        }
        // Forward the device-ringing to the connected client.
        session.onDeviceRinging(sessionId);
    }

    @Override
    public void onCreateCallRoom(long requestId, @NonNull UUID callRoomId, @NonNull String memberId,
                                 int maxMemberCount) {
        Log.error("{} onCreateCallRoom {} with callRoom {} should never be called",
                mProxyIdent, requestId, callRoomId);
    }

    @Override
    public void onJoinCallRoom(long requestId, @NonNull UUID callRoomId, @NonNull String memberId,
                               @NonNull List<PeerCallService.MemberInfo> members) {
        Log.debug("{} onJoinCallRoom {} with callRoom {} and memberId {}",
                mProxyIdent, requestId, callRoomId, memberId);

        ClientSession clientSession = mActiveRequests.get(requestId);
        if (clientSession == null) {
            Log.error("No active request found with requestId: {}", requestId);
            return;
        }
        clientSession.onJoinCallRoom(callRoomId, memberId, members, 0);
    }

    @Override
    public void onLeaveCallRoom(long requestId, @NonNull UUID callRoomId) {
        Log.debug("{} onLeaveCallRoom {} with callRoom {}", mProxyIdent, requestId, callRoomId);
        synchronized (mActiveCallRooms) {
            if(mActiveCallRooms.get(callRoomId) != null && mActiveCallRooms.get(callRoomId).isEmpty()) {
                mActiveCallRooms.remove(callRoomId);
            }
        }
    }

    @Override
    public void onDestroyCallRoom(long requestId, @NonNull UUID callRoomId) {
        Log.debug("{} onDestroyCallRoom {} with callRoom {} should never be called",
                mProxyIdent, requestId, callRoomId);
    }

    @Override
    public void onInviteCallRoom(@NonNull UUID callRoomId, @NonNull UUID twincodeInboundId,
                                 @Nullable UUID p2pSession, int maxCount) {
        Log.debug("{} onInviteCallRoom {} for in-twincode {} and session {}",
                mProxyIdent, callRoomId, twincodeInboundId, p2pSession);

        final ClientSession clientSession = mTwincodeInboundSessions.get(twincodeInboundId);
        if (clientSession == null) {

            return;
        }

        if (clientSession.onInviteCallRoom(callRoomId, p2pSession, maxCount)) {
            Log.debug("{} onInviteCallRoom: adding ClientSession {} to CallRoom {}", mProxyIdent, clientSession, callRoomId);
            synchronized (mActiveCallRooms) {
                mActiveCallRooms.computeIfAbsent(callRoomId, id -> new ArrayList<>()).add(clientSession);
            }
        }
    }

    @Override
    public void onMemberJoinCallRoom(@NonNull UUID callRoomId, @NonNull String memberId, @Nullable UUID p2pSession,
                                     @NonNull PeerCallService.MemberStatus status) {
        Log.debug("{} onMemberJoinCallRoom {} for member {} and session {} status {}",
                mProxyIdent, callRoomId, memberId, p2pSession, status);

        final List<ClientSession> clientSessions;
        synchronized (mActiveCallRooms) {
            if (mActiveCallRooms.get(callRoomId) == null) {
                return;
            }
            clientSessions = List.copyOf(mActiveCallRooms.get(callRoomId));
        }

        for (ClientSession clientSession : clientSessions) {
            clientSession.onMemberJoinCallRoom(memberId, p2pSession, status);
        }
    }

    @Override
    public void onError(long requestId, ErrorCode errorCode, @Nullable String errorParameter) {
        Log.error("{} Error requestId={} errorCode={} errorParameter={}",
                mProxyIdent, requestId, errorCode, errorParameter);
    }

    /**
     * Get the count of clients that have been managed by the {@link ProxyApplication} instance.
     * <p>
     * Note that the returned value is monotonic, and always equals or greater than a previous value
     * returned by this same function call
     * </p>
     *
     * @return Total count of clients managed by this instance since its creation
     */
    public long getCreatedClientCount() {
        return mClientId.get();
    }

    /**
     * Get the current number of active rooms
     *
     * @return Current number of active rooms
     */
    public final int getActiveCallRoomCount() {
        final int count;
        synchronized (mActiveCallRooms) {
            count = mActiveCallRooms.size();
        }
        return count;
    }

    /**
     * Get the current number of active peer-to-peer sessions
     *
     * @return Current number of active peer-to-peer sessions
     */
    public final int getActiveSessionCount() {
        return mActiveSessions.size();
    }

    /**
     * Get the current number of available twincodes from all pools
     *
     * @return Current number of available twincodes
     */
    public final int getTwincodePoolCount() {
        final int count;
        synchronized (mTwincodePool) {
            count = mTwincodePool.size();
        }
        return count;
    }

    /**
     * Finish proxy application setup after the TwinlifeImpl is configured.
     */
    private void onTwinlifeReady() {
        Log.info("{} Proxy application is now ready to get the twincode factory pools", mProxyIdent);

        final PeerCallService peerCallService = mTwinlifeImpl.getPeerCallService();
        peerCallService.addServiceObserver(this);
        peerCallService.setSignalingListener(this);

        // Load the TwincodeFactoryPool objects and setup the twincode pool.
        new GetTwincodeFactoryPools(mTwinlifeContext, (ErrorCode status, List<TwincodeFactoryPool> twincodePools) -> {
            if (twincodePools != null) {
                int count;
                synchronized (mTwincodePool) {
                    for (TwincodeFactoryPool pool : twincodePools) {
                        mTwincodePool.addAll(pool.getTwincodeFactories());
                    }
                    count = mTwincodePool.size();
                }

                synchronized (mTwincodeFactoryPools) {
                    mTwincodeFactoryPools.addAll(twincodePools);
                }

                Log.info("{} Proxy application has loaded {} twincodes in {} pool objects",
                        mProxyIdent, count, twincodePools.size());
            }
        });
    }

    private void addActiveSession(@NonNull UUID sessionId, @NonNull ClientSession client) {
        Log.debug("Adding active session {} for client {}", sessionId, client.mClientId);

        List<ClientSession> sessions = mActiveSessions.computeIfAbsent(sessionId, s -> new ArrayList<>());

        for (ClientSession session : sessions) {
            if (session == client) {
                Log.error("Active session {} already added for client {}!", session, client.mClientId, new Exception());
                return;
            }
        }

        if (sessions.size() == 2) {
            Log.error("sessionId {} already has 2 peers! Not adding client {}", sessionId, client.mClientId, new Exception());
            return;
        }

        sessions.add(client);
    }

    @Nullable
    private ClientSession getActiveSession(@NonNull UUID sessionId) {
        return getActiveSession(sessionId, null);
    }

    @Nullable
    private ClientSession getActiveSession(@NonNull UUID sessionId, @Nullable String memberId) {
        List<ClientSession> sessions = mActiveSessions.get(sessionId);

        if (sessions == null || sessions.isEmpty()) {
            return null;
        }

        if (memberId == null || sessions.size() == 1) {
            if (sessions.size() != 1) {
                Log.warn("getActiveSession: memberId is null but session is local, you should probably specify a memberId", new Exception());
            }

            return sessions.get(0);
        }

        for (ClientSession session : sessions) {
            if (memberId.equals(session.getCallRoomMemberId())) {
                return session;
            }
        }

        Log.debug("Unknown member {} for session {}", memberId, sessionId);
        return null;
    }

    @Nullable
    private ClientSession getLocalPeer(@NonNull UUID sessionId, @NonNull String to) {
        if (!isLocalSession(sessionId)) {
            return null;
        }

        return getActiveSession(sessionId, to);
    }

    private boolean isLocalSession(@NonNull UUID sessionId) {
        List<ClientSession> sessions = mActiveSessions.get(sessionId);
        return sessions != null && sessions.size() == 2;
    }
}
