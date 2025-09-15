/*
 *  Copyright (c) 2022-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.web;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.fasterxml.jackson.databind.JsonNode;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.Configuration;
import org.twinlife.twinlife.PushNotificationContent;
import org.twinlife.twinlife.PushNotificationOperation;
import org.twinlife.twinlife.PushNotificationPriority;
import org.twinlife.twinlife.Offer;
import org.twinlife.twinlife.OfferToReceive;
import org.twinlife.twinlife.PeerCallService;
import org.twinlife.twinlife.Sdp;
import org.twinlife.twinlife.SdpType;
import org.twinlife.twinlife.TerminateReason;
import org.twinlife.twinlife.TransportCandidate;
import org.twinlife.twinlife.TransportCandidateList;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinlife.util.Version;
import org.twinlife.twinme.models.Capabilities;
import org.twinlife.twinme.models.TwincodeKind;
import org.twinlife.web.kafka.RecordSender;
import org.twinlife.web.kafka.records.ClickToCallRecord;
import org.twinlife.web.kafka.records.ClickToCallAcceptRecord;
import org.twinlife.web.kafka.records.ClickToCallInitiateRecord;
import org.twinlife.web.kafka.records.ClickToCallJoinRoomRecord;
import org.twinlife.web.messages.CandidateInfo;
import org.twinlife.web.messages.DeviceRingingMessage;
import org.twinlife.web.messages.ErrorMessage;
import org.twinlife.web.messages.InviteCallRoomMessage;
import org.twinlife.web.messages.JoinCallRoomMessage;
import org.twinlife.web.messages.MemberInfo;
import org.twinlife.web.messages.MemberJoinCallMessage;
import org.twinlife.web.messages.PingPongMessage;
import org.twinlife.web.messages.SessionAcceptMessage;
import org.twinlife.web.messages.SessionConfigMessage;
import org.twinlife.web.messages.SessionInitiateMessage;
import org.twinlife.web.messages.SessionInitiateResponseMessage;
import org.twinlife.web.messages.SessionOffer;
import org.twinlife.web.messages.SessionOfferToReceive;
import org.twinlife.web.messages.SessionTerminateMessage;
import org.twinlife.web.messages.SessionUpdateMessage;
import org.twinlife.web.messages.TransportInfoMessage;

import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.twinlife.twinlife.TwincodeFactory;
import org.twinlife.web.util.Json;

/**
 * A web client session that is connected through a WebSocket connection.
 *
 * The WebSocket connection is used to allow to web client to make WebRTC calls.
 * It maintains a list of active P2P sessions and is responsible for forwarding
 * the session-initiate/accept/update/transport/terminate through the Openfire
 * connection.
 */
public class ClientSession {
    static final Logger Log = LogManager.getLogger(ClientSession.class);

    private static final long MAX_IDLE_DELAY = ProxyController.MAX_CLIENT_IDLE_DELAY;

    @Nonnull
    private final ProxyApplication mApplication;
    private final Map<UUID, String> mPeerSessions;
    public final String mClientId;
    private final String mSessionId;
    @Nullable
    private TwincodeFactory mCallTwincode;
    @Nullable
    private UUID mCallRoomId;
    @Nullable
    private String mCallRoomMemberId;
    @Nullable
    private final RecordSender<String, ClickToCallRecord > mKafkaRecordSender;
    @Nullable
    private WeakReference<WebSocketClientSession> mSession;
    private long mLastAccessTime;
    @Nonnull
    private final Set<String> mMemberIds;
    @Nullable
    private List<Object> mMessageQueue;
    private long mQueueCreationDate;
    private InetAddress mClientAddress;

    public ClientSession(@Nonnull String sessionId,
                         @Nullable RecordSender<String, ClickToCallRecord> kafkaRecordSender) {

        mSessionId = sessionId;
        mKafkaRecordSender = kafkaRecordSender;
        mPeerSessions = new ConcurrentHashMap<>();
        mApplication = ProxyController.getProxyApplication();
        mClientId = mApplication.allocateIdentifier();
        mMemberIds = new ConcurrentSkipListSet<>();
    }

    public String getSessionId() {

        return mSessionId;
    }

    void setSession(@Nonnull WebSocketClientSession session, InetAddress clientAddress) {

        while (true) {
            final List<Object> pending;
            synchronized (this) {
                pending = mMessageQueue;
                mMessageQueue = null;
                if (pending == null) {
                    mSession = new WeakReference<>(session);
                    mClientAddress = clientAddress;
                    return;
                }
            }
            for (Object msg : pending) {
                session.sendMessage(msg);
            }
        }
    }

    /**
     * Check whether this client session has expired:
     * - it is not connected anymore to a WebSocket,
     * - we have not received any message during the last 5 minutes
     *
     * @return
     */
    boolean isExpired() {

        if (mSession != null && mSession.get() != null) {
            return false;
        }

        return mLastAccessTime + MAX_IDLE_DELAY < System.currentTimeMillis();
    }

    public synchronized String getSessionTo(@Nonnull UUID sessionId) {

        return mPeerSessions.get(sessionId);
    }

    public synchronized String getCallRoomMemberId(){
        return mCallRoomMemberId;
    }

    /**
     * Called when a session-initiate IQ is received.
     *
     * @param sessionId            the P2P session id.
     * @param from                 the target identification string.
     * @param sdp                  the sdp content (clear text | compressed | encrypted).
     * @param offer                the offer.
     * @param offerToReceive       the offer to receive.
     * @param maxReceivedFrameSize the max received frame size.
     * @param maxReceivedFrameRate the max received frame rate.
     * @return SUCCESS, NO_PERMISSION, ITEM_NOT_FOUND if the session id is not known.
     */
    @Nonnull
    public ErrorCode onSessionInitiate(@Nonnull UUID sessionId, @Nonnull String from,
                                       @Nonnull Sdp sdp, @Nonnull Offer offer,
                                       @Nonnull OfferToReceive offerToReceive,
                                       int maxReceivedFrameSize, int maxReceivedFrameRate) {
        Log.debug("{} onSessionInitiate sessionId={} from={} sdp={} offer={}",
                mClientId, sessionId, from, sdp, offer);

        mPeerSessions.put(sessionId, from);

        final SessionInitiateMessage msg = new SessionInitiateMessage();
        msg.sessionId = sessionId.toString();
        msg.to = from;
        msg.maxFrameRate = maxReceivedFrameRate;
        msg.maxFrameSize = maxReceivedFrameSize;
        msg.sdp = sdp.getSdp();
        if (msg.sdp == null) {
            Log.error("{} SDP encrypted or invalid in session {} ", mClientId, sessionId);

            return ErrorCode.FEATURE_NOT_SUPPORTED_BY_PEER;
        }
        msg.offer = new SessionOffer();
        msg.offer.data = offer.data;
        msg.offer.audio = offer.audio;
        msg.offer.video = offer.video;
        msg.offer.videoBell = offer.videoBell;
        msg.offer.version = offer.version.toString();

        msg.offerToReceive = new SessionOfferToReceive();
        msg.offerToReceive.audio = offerToReceive.audio;
        msg.offerToReceive.video = offerToReceive.video;
        msg.offerToReceive.data = offerToReceive.data;

        ProxyEvent.logEvent("forward-session-initiate", "clientId", mClientId,
                "from", from, "sessionId", msg.sessionId);

        sendMessage(msg);

        if (mKafkaRecordSender != null && mCallTwincode != null && mClientAddress != null) {
            final ClickToCallRecord record = new ClickToCallInitiateRecord(sessionId, mCallTwincode.getTwincodeOutbound().getId(), mClientAddress);
            mKafkaRecordSender.queueEvent(record);
        }
        return ErrorCode.SUCCESS;
    }

    /**
     * Called when a session-accept IQ is received.
     *
     * @param sessionId            the P2P session id.
     * @param sdp                  the sdp content (clear text | compressed | encrypted).
     * @param offer                the offer.
     * @param offerToReceive       the offer to receive.
     * @param maxReceivedFrameSize the max received frame size.
     * @param maxReceivedFrameRate the max received frame rate.
     * @return SUCCESS, NO_PERMISSION, ITEM_NOT_FOUND if the session id is not known.
     */
    @Nonnull
    public ErrorCode onSessionAccept(@Nonnull UUID sessionId, @Nonnull Sdp sdp, @Nonnull Offer offer,
                                     @Nonnull OfferToReceive offerToReceive, int maxReceivedFrameSize,
                                     int maxReceivedFrameRate) {
        Log.debug("{} onSessionAccept sessionId={} sdp={} offer={}", mClientId, sessionId, sdp, offer);

        final String to = mPeerSessions.get(sessionId);
        if (to == null) {

            return ErrorCode.ITEM_NOT_FOUND;
        }

        final SessionAcceptMessage msg = new SessionAcceptMessage();
        msg.sessionId = sessionId.toString();
        msg.maxFrameRate = maxReceivedFrameRate;
        msg.maxFrameSize = maxReceivedFrameSize;
        msg.sdp = sdp.getSdp();
        if (msg.sdp == null) {
            Log.error("SDP encrypted or invalid in session {} ", sessionId);

            return ErrorCode.FEATURE_NOT_SUPPORTED_BY_PEER;
        }
        msg.offer = new SessionOffer();
        msg.offer.data = offer.data;
        msg.offer.audio = offer.audio;
        msg.offer.video = offer.video;
        msg.offer.videoBell = offer.videoBell;
        msg.offer.version = offer.version.toString();

        msg.offerToReceive = new SessionOfferToReceive();
        msg.offerToReceive.audio = offerToReceive.audio;
        msg.offerToReceive.video = offerToReceive.video;
        msg.offerToReceive.data = offerToReceive.data;

        ProxyEvent.logEvent("forward-session-accept", "clientId", mClientId,
                "to", to, "sessionId", msg.sessionId);

        sendMessage(msg);

        if (mKafkaRecordSender != null && mCallTwincode != null && mClientAddress != null) {
            final ClickToCallRecord record;
            record = new ClickToCallAcceptRecord(sessionId, mCallTwincode.getTwincodeOutbound().getId(), mClientAddress);
            mKafkaRecordSender.queueEvent(record);
        }
        return ErrorCode.SUCCESS;
    }

    /**
     * Called when a session-update IQ is received.
     *
     * @param sessionId  the P2P session id.
     * @param updateType whether this is an offer or an answer.
     * @param sdp        the sdp content (clear text | compressed | encrypted).
     * @return SUCCESS or ITEM_NOT_FOUND if the session id is not known.
     */
    @Nonnull
    public ErrorCode onSessionUpdate(@Nonnull UUID sessionId, @Nonnull SdpType updateType, @Nonnull Sdp sdp) {
        Log.debug("{} onSessionUpdate sessionId={} updateType={} sdp={}", mClientId, sessionId, updateType, sdp);

        final String to = mPeerSessions.get(sessionId);
        if (to == null) {

            return ErrorCode.ITEM_NOT_FOUND;
        }

        final SessionUpdateMessage msg = new SessionUpdateMessage();
        msg.sessionId = sessionId.toString();
        msg.updateType = updateType == SdpType.OFFER ? "offer" : "answer";
        msg.sdp = sdp.getSdp();
        if (msg.sdp == null) {

            Log.error("{} SDP encrypted or invalid in session {} ", mClientId, sessionId);
            return ErrorCode.FEATURE_NOT_SUPPORTED_BY_PEER;
        }

        ProxyEvent.logEvent("forward-session-update", "clientId", mClientId,
                "to", to, "sessionId", msg.sessionId);

        sendMessage(msg);
        return ErrorCode.SUCCESS;
    }

    /**
     * Called when a transport-info IQ is received with a list of candidates.
     *
     * @param sessionId  the P2P session id.
     * @param sdp the list of candidates.
     * @return SUCCESS or ITEM_NOT_FOUND if the session id is not known.
     */
    @Nonnull
    public ErrorCode onTransportInfo(@Nonnull UUID sessionId, @Nonnull Sdp sdp) {
        Log.debug("{} onTransportInfo sessionId={} candidates={}", mClientId, sessionId, sdp);

        final String to = mPeerSessions.get(sessionId);
        if (to == null) {

            return ErrorCode.ITEM_NOT_FOUND;
        }

        final TransportInfoMessage msg = new TransportInfoMessage();
        msg.sessionId = sessionId.toString();
        msg.candidates = new ArrayList<>();
        if (sdp.isEncrypted()) {
            return ErrorCode.FEATURE_NOT_SUPPORTED_BY_PEER;
        }

        final TransportCandidate[] transportCandidates = sdp.getCandidates();
        if (transportCandidates == null) {
            return ErrorCode.BAD_REQUEST;
        }
        for (TransportCandidate c : transportCandidates) {
            msg.candidates.add(new CandidateInfo(c));
        }

        ProxyEvent.logEvent("forward-transport-info", "clientId", mClientId,
                "to", to, "sessionId", msg.sessionId, "candidates", Integer.toString(msg.candidates.size()));

        sendMessage(msg);
        return ErrorCode.SUCCESS;
    }

    /**
     * Called when a session-terminate IQ is received for the given P2P session.
     *
     * @param sessionId the P2P session id.
     * @param reason    the terminate reason.
     */
    public void onSessionTerminate(@Nonnull UUID sessionId, @Nonnull TerminateReason reason) {
        Log.debug("{} onSessionTerminate sessionId={} reason={}", mClientId, sessionId, reason);

        final String to = terminate(sessionId);

        if (to != null) {
            final SessionTerminateMessage msg = new SessionTerminateMessage(sessionId.toString(), reason.toString());

            ProxyEvent.logEvent("forward-session-terminate", "clientId", mClientId,
                    "to", to, "sessionId", msg.sessionId, "reason", msg.reason);

            sendMessage(msg);
        }
    }

    public void onDeviceRinging(@Nonnull UUID sessionId) {
        Log.debug("{} onDeviceRinging sessionId={}", mClientId, sessionId);

        final String to = mPeerSessions.get(sessionId);
        if (to == null) {

            return;
        }

        final DeviceRingingMessage msg = new DeviceRingingMessage(sessionId.toString());
        ProxyEvent.logEvent("device-ringing", "clientId", mClientId,
                "to", to, "sessionId", msg.sessionId);

        sendMessage(msg);
    }

    public boolean onInviteCallRoom(@Nonnull UUID callRoomId, @Nullable UUID p2pSession, int maxCount) {
        Log.debug("{} onInviteCallRoom {} and session {}", mClientId, callRoomId, p2pSession);

        if (p2pSession == null || mCallRoomId != null) {
            Log.warn("{} cannot be invited to {}: already member of call room {}",
                    mClientId, callRoomId, mCallRoomId);
            return false;
        }

        final String to = mPeerSessions.get(p2pSession);
        if (to == null) {
            Log.warn("{} cannot be invited to {}: invalid P2P session {}",
                    mClientId, callRoomId, p2pSession);
            return false;
        }

        TwincodeInbound twincodeInbound;
        synchronized (this) {
            if (mCallTwincode == null) {
                Log.warn("{} has no twincode to join callroom {}", mClientId, callRoomId);
                return false;
            }
            twincodeInbound = mCallTwincode.getTwincodeInbound();
        }

        ProxyEvent.logEvent("forward-invite-callroom", "clientId", mClientId,
                "to", to, "sessionId", p2pSession.toString(), "callRoomId", callRoomId.toString());

        mApplication.joinCallRoom(callRoomId, twincodeInbound.getId(), p2pSession);
        return true;
    }

    public void onJoinCallRoom(@Nonnull UUID callRoomId, @Nonnull String memberId,
                               @Nonnull List<PeerCallService.MemberInfo> members, int maxMemberCount) {
        Log.debug("{} onJoinCallRoom with callRoom {} and memberId {}, {} members", mClientId, callRoomId, memberId, members.size());


        mCallRoomId = callRoomId;
        mCallRoomMemberId = memberId;

        final JoinCallRoomMessage msg = new JoinCallRoomMessage();
        msg.callRoomId = callRoomId.toString();
        msg.memberId = memberId;
        msg.members = new ArrayList<>();
        for (PeerCallService.MemberInfo member : members) {
            final MemberInfo apiMember = new MemberInfo();
            apiMember.memberId = member.memberId;
            apiMember.sessionId = member.p2pSessionId == null ? null : member.p2pSessionId.toString();
            switch (member.status) {
                case NEW_MEMBER:
                    apiMember.status = "member-new";
                    break;

                case NEW_MEMBER_NEED_SESSION:
                    apiMember.status = "member-need-session";
                    break;

                case DEL_MEMBER:
                    apiMember.status = "member-delete";
                    break;
            }
            msg.members.add(apiMember);
            mMemberIds.add(apiMember.memberId);
        }
        msg.maxMemberCount = maxMemberCount;

        ProxyEvent.logEvent("forward-join-callroom", "clientId", mClientId,
                "callRoomId", msg.callRoomId, "memberCount", Integer.toString(msg.members.size()));

        sendMessage(msg);

        if (mKafkaRecordSender != null && mCallTwincode != null && mClientAddress != null) {
            final ClickToCallRecord record;
            record = new ClickToCallJoinRoomRecord(null, mCallTwincode.getTwincodeOutbound().getId(), mClientAddress, callRoomId);
            mKafkaRecordSender.queueEvent(record);
        }
    }

    public void onMemberJoinCallRoom(@Nonnull String memberId, @Nullable UUID p2pSession,
                                     @Nonnull PeerCallService.MemberStatus status) {
        Log.debug("{} onMemberJoinCallRoom {} and session {} status {}",
                mClientId, memberId, p2pSession, status);

        if(mMemberIds.contains(memberId)){
            // happens when multiple ClientSessions are part of the same call room:
            // When a new member joins the call room, OpenFire sends one MemberNotificationIQ for each ClientSession.
            // We forward the first one to all existing members and ignore the next ones.
            // TODO: synchronize?
            Log.debug("{} onMemberJoinCallRoom: member {} already added", mClientId, memberId);
            return;
        }

        mMemberIds.add(memberId);

        final MemberJoinCallMessage msg = new MemberJoinCallMessage();
        msg.memberId = memberId;
        msg.sessionId = p2pSession == null ? null : p2pSession.toString();
        msg.status = status;
        sendMessage(msg);
    }

    public void onMessage(@Nonnull String msg, @Nonnull JsonNode jsonNode) {
        Log.debug("{} onMessage {}", mClientId, jsonNode);

        switch (msg) {
            case "session-request":
                sessionRequest(jsonNode);
                break;

            case "ping":
                sendMessage(new PingPongMessage());
                break;

            case SessionInitiateMessage.NAME:
                sessionInitiate(jsonNode);
                break;

            case SessionAcceptMessage.NAME:
                sessionAccept(jsonNode);
                break;

            case SessionUpdateMessage.NAME:
                sessionUpdate(jsonNode);
                break;

            case TransportInfoMessage.NAME:
                transportInfo(jsonNode);
                break;

            case SessionTerminateMessage.NAME:
                sessionTerminate(jsonNode);
                break;

            case InviteCallRoomMessage.NAME:
                inviteCallRoom(jsonNode);
                break;

            default:
                Log.warn("Unknown message: {}", msg);
                sendMessage(new ErrorMessage("Invalid message"));
                break;
        }
    }

    boolean close(int statusCode, String reason) {
        Log.debug("close {} reason {}", statusCode, reason);

        // synchronize barrier for concurrent sendMessage().
        synchronized (this) {
            mSession = null;
        }

        // Release the session only when the browser closed with status 1000 (CLOSE_NORMAL)
        // or 1001 (CLOSE_GOING_AWAY).
        // This indicates a normal closure when the browser has no pending active call in progress.
        // If the session was not created with a valid session id, we also close and terminate immediately.
        final boolean disposed = statusCode == 1000 || statusCode == 1001
                || !mSessionId.startsWith(WebSocketClientSession.MAGIC_PREFIX);
        if (disposed) {
            dispose();
        }
        return disposed;
    }

    void dispose() {
        Log.info("dispose client {}", mClientId);

        // When the web client disconnects we must terminate any active session.
        if (!mPeerSessions.isEmpty()) {
            ProxyEvent.logEvent("terminate-all", "clientId", mClientId,
                    "count", String.valueOf(mPeerSessions.size()));

            for (Map.Entry<UUID, String> session : mPeerSessions.entrySet()) {
                ProxyEvent.logEvent("session-terminate", "clientId", mClientId,
                        "sessionId", session.getKey().toString(), "reason", "disconnected");

                mApplication.sessionTerminate(session.getKey(), session.getValue(), TerminateReason.DISCONNECTED);
            }
        }

        if (mCallTwincode != null) {
            mApplication.releaseCallTwincode(mCallTwincode);
            mCallTwincode = null;
        }

        if (mCallRoomId != null) {
            mApplication.leaveCallRoom(mCallRoomId, mCallRoomMemberId);
            mMemberIds.clear();
        }
    }

    @Nullable
    private static Offer getOffer(final @Nonnull JsonNode jsonNode, final String name) {

        JsonNode item = jsonNode.get(name);
        if (item == null) {
            return null;
        }

        boolean audio = Json.getBoolean(item, "audio", false);
        boolean video = Json.getBoolean(item, "video", false);
        boolean videoBell = Json.getBoolean(item, "videoBell", false);
        boolean data = Json.getBoolean(item, "data", false);
        boolean transfer = Json.getBoolean(item, "transfer", false);

        final Offer offer = new Offer(audio, video, videoBell, data, transfer);
        offer.version = new Version("2.0");
        return offer;
    }

    @Nullable
    private static OfferToReceive getOfferToReceive(final @Nonnull JsonNode jsonNode, final String name) {

        JsonNode item = jsonNode.get(name);
        if (item == null) {
            return null;
        }

        boolean audio = Json.getBoolean(item, "audio", false);
        boolean video = Json.getBoolean(item, "video", false);
        boolean data = Json.getBoolean(item, "data", false);

        final OfferToReceive offer = new OfferToReceive(audio, video, data);
        return offer;
    }

    /**
     * Web proxy is requesting to initiate a new session and needs some configuration.
     *
     * @param jsonNode
     */
    private void sessionRequest(@Nonnull final JsonNode jsonNode) {
        Log.debug("{} received session-request message {}", mClientId, jsonNode);

        final SessionConfigMessage sessionConfigMessage = new SessionConfigMessage();
        final Configuration configuration = mApplication.getConfiguration();
        if (configuration == null) {

            return;
        }

        ProxyEvent.logEvent("session-config", "clientId", mClientId);

        // Never send the `Configuration` object as is because it contains sensitive information.
        // We want to expose only a subset of it.
        sessionConfigMessage.turnServers = configuration.turnServers;
        sessionConfigMessage.maxSendFrameRate = configuration.maxSentFrameRate;
        sessionConfigMessage.maxSendFrameSize = configuration.maxSentFrameSize;
        sessionConfigMessage.maxReceivedFrameRate = configuration.maxReceivedFrameRate;
        sessionConfigMessage.maxReceivedFrameSize = configuration.maxReceivedFrameSize;
        sendMessage(sessionConfigMessage);
    }

    /**
     * Web proxy is starting a new P2P session.
     *
     * @param jsonNode the JSON session-initiate.
     */
    private void sessionInitiate(@Nonnull final JsonNode jsonNode) {
        Log.debug("{} received session-initiate message {}", mClientId, jsonNode);

        final TwincodeOutbound proxyTwincode;
        synchronized (this) {
            if (mCallTwincode != null) {
                proxyTwincode = mCallTwincode.getTwincodeOutbound();
            } else {
                proxyTwincode = null;
            }
        }

        if (proxyTwincode != null) {
            sessionInitiate(jsonNode, proxyTwincode.getId());
            return;
        }

        // Allocate a twincode for this session the first time a session-initiate is made.
        mApplication.allocateCallTwincodeFactory(this, (ErrorCode errorCode, TwincodeFactory factory) -> {
            if (errorCode == ErrorCode.SUCCESS && factory != null) {
                synchronized (this) {
                    if (mCallTwincode == null) {
                        mCallTwincode = factory;
                    } else {
                        mApplication.releaseCallTwincode(factory);
                        factory = mCallTwincode;
                    }
                }
                sessionInitiate(jsonNode, factory.getTwincodeOutbound().getId());
            } else {
                Log.error("{}: cannot issue session initiate due to error {}", mClientId, errorCode);
            }
        });
    }

    private void sessionInitiate(@Nonnull final JsonNode jsonNode, @Nonnull UUID twincodeId) {
        Log.debug("{} received session-initiate message {} proxy twincode {}", mClientId, jsonNode, twincodeId);

        final String requestTo = Json.getString(jsonNode, "to");
        final String sdpContent = Json.getString(jsonNode, "sdp");
        final Offer offer = getOffer(jsonNode, "offer");
        final OfferToReceive offerToReceive = getOfferToReceive(jsonNode, "offerToReceive");
        final int maxFrameSize = Json.getInteger(jsonNode, "maxFrameSize", 0);
        final int maxFrameRate = Json.getInteger(jsonNode, "maxFrameRate", 0);

        if (requestTo == null || sdpContent == null || offer == null || offerToReceive == null) {

            return;
        }

        ProxyEvent.logEvent("request-session", "clientId", mClientId,
                "to", requestTo, "twincodeId", twincodeId.toString());

        int pos = requestTo.indexOf('@');
        if (pos > 0) {
            int sep = requestTo.indexOf('.', pos + 1);
            if (sep <= 0) {
                return;
            }
            UUID callRoomId = Utils.UUIDFromString(requestTo.substring(pos + 1, sep));
            if (mCallRoomId == null || !mCallRoomId.equals(callRoomId)) {
                Log.error("{} invalid callroom in destination {}", mClientId, requestTo);
                return;
            }
            if (!requestTo.startsWith(".callroom.", sep)) {
                Log.error("{} invalid .callroom. suffix in destination {}", mClientId, requestTo);
                return;
            }

            doSessionInitiate(twincodeId, requestTo, sdpContent, offer, offerToReceive, maxFrameSize, maxFrameRate, requestTo);
        } else {
            final UUID peerTwincodeId = Utils.toUUID(requestTo);
            if (peerTwincodeId == null) {
                Log.error("{} invalid 'to' destination {}", mClientId, requestTo);
                return;
            }

            final String to = mApplication.getPeerId(peerTwincodeId, twincodeId);
            if (to == null) {
                Log.error("{} internal error getPeerId() is null for {}", mClientId, requestTo);
                return;
            }

            mApplication.getTwincode(peerTwincodeId, (ErrorCode errorCode, TwincodeOutbound twincodeOutbound) -> {
                if (errorCode != ErrorCode.SUCCESS || twincodeOutbound == null) {
                    Log.error("{} could not get twincodeOutbound {}, errorCode={}", mClientId, peerTwincodeId, errorCode);
                    return;
                }

                if (checkCallReceiverTwincodeOutbound(twincodeOutbound, requestTo)) {
                    offer.transfer = checkTransferTwincodeOutbound(twincodeOutbound);
                    doSessionInitiate(twincodeId, requestTo, sdpContent, offer, offerToReceive, maxFrameSize, maxFrameRate, to);
                }
            });
        }
    }

    private boolean checkCallReceiverTwincodeOutbound(@Nonnull TwincodeOutbound twincodeOutbound, @Nonnull String requestTo) {
        String capString = twincodeOutbound.getCapabilities();

        if (capString == null) {
            Log.error("{} twincodeOutbound {} doesn't have capabilities", mClientId, twincodeOutbound.getId());
            sendMessage(new SessionInitiateResponseMessage(requestTo, "gone"));
            return false;
        }

        Capabilities caps = new Capabilities(capString);

        if (caps.getKind() != TwincodeKind.CALL_RECEIVER || !(caps.hasAudio() || caps.hasVideo())) {
            Log.error("{} twincodeOutbound {} is not an active call receiver, capabilities={}", mClientId, twincodeOutbound.getId(), capString);
            sendMessage(new SessionInitiateResponseMessage(requestTo, "not-authorized"));
            return false;
        }

        if (caps.getSchedule() != null && !caps.getSchedule().isTimestampInRange(System.currentTimeMillis())){
            Log.error("{} twincodeOutbound {} is unavailable due to its schedule: {}", mClientId, twincodeOutbound.getId(), caps.getSchedule());
            sendMessage(new SessionInitiateResponseMessage(requestTo, "schedule"));
            return false;
        }

        return true;
    }

    private boolean checkTransferTwincodeOutbound(TwincodeOutbound twincodeOutbound) {
        String capString = twincodeOutbound.getCapabilities();
        if (capString != null) {
            return new Capabilities(capString).hasTransfer();
        }

        return false;
    }

    private void doSessionInitiate(@Nonnull UUID twincodeId, String requestTo, String sdpContent, Offer offer, OfferToReceive offerToReceive, int maxFrameSize, int maxFrameRate, String to) {
        final UUID sessionId = UUID.randomUUID();
        mPeerSessions.put(sessionId, to);

        // For the twinapp proxy, filter the codecs from SDP that the browser sent:
        // - in the session-initiate,
        // - in the session-accept,
        // - in the session-update
        // We don't need to filter codecs from SDP that we receive from the Openfire server
        // because we expect that they are already filtered by the application.
        final Sdp sdp = new Sdp(Sdp.filterCodecs(sdpContent));
        PushNotificationContent notificationContent = new PushNotificationContent();
        offer.data = true;
        if (offer.video) {
            notificationContent.operation = PushNotificationOperation.VIDEO_CALL;
        } else if (offer.audio) {
            notificationContent.operation = PushNotificationOperation.AUDIO_CALL;
        } else {
            notificationContent.operation = PushNotificationOperation.PUSH_MESSAGE;
        }

        notificationContent.priority = PushNotificationPriority.HIGH;
        mApplication.sessionInitiate(this, sessionId, to, sdp, offer, offerToReceive,
                maxFrameSize, maxFrameRate, notificationContent, (ErrorCode errorCode, Long requestId) -> {

                    if (errorCode == ErrorCode.ITEM_NOT_FOUND) {
                        terminate(sessionId);
                        sendMessage(new SessionInitiateResponseMessage(requestTo, "peer not found"));
                        return;
                    }

                    ProxyEvent.logEvent("session-initiate", "clientId", mClientId,
                            "to", requestTo, "twincodeId", twincodeId.toString(), "sessionId", sessionId.toString());

                    sendMessage(new SessionInitiateResponseMessage(requestTo, sessionId));

                    if (mKafkaRecordSender != null && mCallTwincode != null && mClientAddress != null) {
                        final ClickToCallRecord record = new ClickToCallInitiateRecord(sessionId, mCallTwincode.getTwincodeOutbound().getId(), mClientAddress);
                        mKafkaRecordSender.queueEvent(record);
                    }
                });
    }

    private void sessionAccept(@Nonnull final JsonNode jsonNode) {
        Log.debug("{} received session-accept message {}", mClientId, jsonNode);

        final String requestTo = Json.getString(jsonNode, "to");
        final UUID sessionId = Utils.UUIDFromString(Json.getString(jsonNode, "sessionId"));
        final String sdpContent = Json.getString(jsonNode, "sdp");
        final Offer offer = getOffer(jsonNode, "offer");
        final OfferToReceive offerToReceive = getOfferToReceive(jsonNode, "offerToReceive");
        final int maxFrameSize = Json.getInteger(jsonNode, "maxFrameSize", 0);
        final int maxFrameRate = Json.getInteger(jsonNode, "maxFrameRate", 0);

        if (requestTo == null || sessionId == null || sdpContent == null || offer == null || offerToReceive == null) {

            return;
        }

        final Sdp sdp = new Sdp(Sdp.filterCodecs(sdpContent));
        final String to = mPeerSessions.get(sessionId);
        if (to == null) {
            return;
        }

        ProxyEvent.logEvent("session-accept", "clientId", mClientId,
                "to", requestTo, "sessionId", sessionId.toString());

        mApplication.sessionAccept(sessionId, mCallRoomMemberId, to, sdp, offer, offerToReceive, maxFrameSize, maxFrameRate,
                (ErrorCode errorCode, Long requestId) -> {

                    if (errorCode == ErrorCode.ITEM_NOT_FOUND) {
                        terminate(sessionId);
                        // sendMessage(new SessionInitiateResponseMessage(requestTo, "peer not found"));
                        return;
                    }

                    // FIXME: Log.info() here ?
                    Log.error("{} session accept sent {}", mClientId, sessionId);
                    // sendMessage(new SessionInitiateResponseMessage(requestTo, sessionId));

                    if (mKafkaRecordSender != null && mCallTwincode != null && mClientAddress != null) {
                        final ClickToCallRecord record;
                        record = new ClickToCallAcceptRecord(sessionId, mCallTwincode.getTwincodeOutbound().getId(), mClientAddress);
                        mKafkaRecordSender.queueEvent(record);
                    }
                });
    }

    private void sessionUpdate(@Nonnull final JsonNode jsonNode) {
        Log.debug("{} received session-update message {}", mClientId, jsonNode);

        final UUID sessionId = Utils.UUIDFromString(Json.getString(jsonNode, "sessionId"));
        final String sdpContent = Json.getString(jsonNode, "sdp");
        final String updateType = Json.getString(jsonNode, "updateType");

        if (sessionId == null || sdpContent == null || updateType == null) {

            return;
        }

        final String to = mPeerSessions.get(sessionId);
        if (to != null) {

            ProxyEvent.logEvent("session-update", "clientId", mClientId,
                    "to", to, "sessionId", sessionId.toString(), "updateType", updateType);

            final Sdp sdp = new Sdp(Sdp.filterCodecs(sdpContent));
            final SdpType type = "offer".equals(updateType) ? SdpType.OFFER : SdpType.ANSWER;

            Log.debug("{} sending {} session-update to {}", mClientId, sdp, sessionId);
            mApplication.sessionUpdate(sessionId, to, sdp, type, (ErrorCode errorCode, Long requestId) -> {
                Log.debug("{} session-update result {}", mClientId, errorCode);
                if (errorCode == ErrorCode.ITEM_NOT_FOUND) {
                    terminate(sessionId);
                }
            });
        }
    }

    private void transportInfo(@Nonnull final JsonNode jsonNode) {
        Log.debug("{} received transport-info message {}", mClientId, jsonNode);

        final UUID sessionId = Utils.UUIDFromString(Json.getString(jsonNode, "sessionId"));
        final JsonNode candidates = jsonNode.get("candidates");

        if (sessionId == null || candidates == null) {

            return;
        }

        final String to = mPeerSessions.get(sessionId);
        if (to != null) {

            final TransportCandidateList list = new TransportCandidateList();
            final Iterator<JsonNode> iter = candidates.elements();
            int count = 0;
            while (iter.hasNext()) {
                final JsonNode candidate = iter.next();
                final String sdp = Json.getString(candidate, "candidate");
                final String label = Json.getString(candidate, "sdpMid");
                final int index = Json.getInteger(candidate, "sdpMLineIndex", 0);
                final boolean removed = Json.getBoolean(candidate, "removed", false);
                if (sdp != null && label != null) {
                    if (removed) {
                        list.removeCandidate(index, label, sdp);
                    } else {
                        list.addCandidate(index, label, sdp);
                    }
                    count++;
                }
            }

            ProxyEvent.logEvent("transport-info", "clientId", mClientId,
                    "to", to, "sessionId", sessionId.toString(), "candidates", Integer.toString(count));

            Log.debug("{} sending {} transport info to {} through session {}", mClientId, list, to, sessionId);
            mApplication.transportInfo(sessionId, to, list, (ErrorCode errorCode, Long requestId) -> {
                Log.debug("{} transport info result {}", mClientId, errorCode);
                if (errorCode == ErrorCode.ITEM_NOT_FOUND) {
                    terminate(sessionId);
                }
            });
        }
    }

    private void sessionTerminate(@Nonnull final JsonNode jsonNode) {
        Log.debug("{} received session-terminate message {}", mClientId, jsonNode);

        final UUID sessionId = Utils.UUIDFromString(Json.getString(jsonNode, "sessionId"));
        final String reason = Json.getString(jsonNode, "reason");

        if (sessionId == null || reason == null) {

            return;
        }

        ProxyEvent.logEvent("session-terminate", "clientId", mClientId,
                "sessionId", sessionId.toString(), "reason", reason);

        final String to = terminate(sessionId);
        if (to != null) {
            final TerminateReason terminateReason = TerminateReason.fromString(reason);
            mApplication.sessionTerminate(sessionId, to, terminateReason);
        }
    }

    /**
     * Terminate the peer session and leave the call room if there is no P2P connection left.
     *
     * @param sessionId the P2P session that is terminated.
     * @return the to identification if the P2P session was removed or null.
     */
    @Nullable
    private String terminate(@Nonnull UUID sessionId) {

        final String to = mPeerSessions.remove(sessionId);
        if (mPeerSessions.isEmpty() && mCallRoomId != null) {
            mApplication.leaveCallRoom(mCallRoomId, mCallRoomMemberId);
            mCallRoomId = null;
            mCallRoomMemberId = null;
        }
        return to;
    }

    private void inviteCallRoom(@Nonnull final JsonNode jsonNode) {
        Log.debug("{} received invite-call-room message {}", mClientId, jsonNode);

        final UUID sessionId = Utils.UUIDFromString(Json.getString(jsonNode, "sessionId"));
        final UUID twincodeOutboundId = Utils.toUUID(Json.getString(jsonNode, "twincodeOutboundId"));
        final UUID callRoomId = Utils.UUIDFromString(Json.getString(jsonNode, "callRoomId"));
        // Ignore mode and maxMemberCount for now

        if (sessionId == null || twincodeOutboundId == null || callRoomId == null) {
            Log.debug("{} invalid invite-call-room message: {}", mClientId, jsonNode);
            return;
        }

        mApplication.inviteCallRoom(callRoomId, twincodeOutboundId, sessionId);
    }

    /**
     * Send the message to the web socket connection as a JSON content.
     *
     * @param object the object to send.
     */
    private void sendMessage(Object object) {
        Log.debug("Send message {}", object);

        final WebSocketClientSession session;
        synchronized (this) {
            if (mSession == null) {
                session = null;
            } else {
                session = mSession.get();
            }

            // No message, put the message in the queue to be able to send it
            // as soon as thew client re-connects.
            if (session == null) {
                if (mMessageQueue == null) {
                    mMessageQueue = new ArrayList<>();
                    mQueueCreationDate = System.currentTimeMillis();
                }
                mMessageQueue.add(object);
                return;
            }
        }
        if (session.sendMessage(object)) {
            mLastAccessTime = System.currentTimeMillis();
        }
    }

    @Override
    public String toString() {
        return "ClientSession{" +
                "mClientId='" + mClientId + '\'' +
                ", mCallRoomMemberId='" + mCallRoomMemberId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientSession that = (ClientSession) o;
        return Objects.equals(mClientId, that.mClientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mClientId);
    }
}