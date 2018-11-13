/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sendroid.kurento;

import com.google.gson.JsonObject;
import com.sendroid.kurento.entity.User;
import org.kurento.client.*;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * User session.
 *
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @since 5.0.0
 */
public class UserSession implements Cloneable {

    private static final Logger log = LoggerFactory.getLogger(UserSession.class);

    private final String name;
    private final String roomName;
    private User.AccountType accountType;
    private final WebSocketSession session;
    private final MediaPipeline pipeline;
    private WebRtcEndpoint webRtcEndpoint;
    private WebRtcEndpoint webRtcEndpointScreen;

    //多对多时使用的输入端点
    private final ConcurrentMap<String, WebRtcEndpoint> incomingMedia = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WebRtcEndpoint> incomingMediaScreen = new ConcurrentHashMap<>();

    @Autowired
    private RoomManager roomManager;

    UserSession(final String name,
                User.AccountType accountType,
                String roomName,
                final WebSocketSession session,
                MediaPipeline pipeline) {
        this.session = session;
        this.name = name;
        this.roomName = roomName;
        this.accountType = accountType;
        this.pipeline = pipeline;
        webRtcEndpoint = new WebRtcEndpoint.Builder(pipeline).build();
        webRtcEndpointScreen = new WebRtcEndpoint.Builder(pipeline).build();

        addIceCandidateFoundListener(webRtcEndpoint, "iceCandidate",name);
        addIceCandidateFoundListener(webRtcEndpointScreen, "iceCandidateScreen",name);
    }

    synchronized void receiveFrom(UserSession teacherSession, String sdpOffer, String responseType) throws IOException {
        log.info("USER {}: connecting with {} in room {}", this.name, teacherSession.getName(), this.roomName);

        log.trace("USER {}: SdpOffer for {} is {}", this.name, teacherSession.getName(), sdpOffer);
        String ipSdpAnswer = null;
        if ("presenterResponse".equals(responseType) || "viewerResponse".equals(responseType)) {
            ipSdpAnswer = webRtcEndpoint.processOffer(sdpOffer);
        } else if ("presenterScreenResponse".equals(responseType) || "viewerScreenResponse".equals(responseType)) {
            ipSdpAnswer = webRtcEndpointScreen.processOffer(sdpOffer);
        }
        final JsonObject scParams = new JsonObject();
        scParams.addProperty("id", responseType);
        scParams.addProperty("response", "accepted");
        scParams.addProperty("name", teacherSession.getName());
        scParams.addProperty("sdpAnswer", ipSdpAnswer);

        log.trace("USER {}: SdpAnswer for {} is {}", this.name, teacherSession.getName(), ipSdpAnswer);
        this.sendMessage(scParams);
        log.debug("gather candidates");
        if ("presenterResponse".equals(responseType)) {
            webRtcEndpoint.gatherCandidates();
        } else if ("presenterScreenResponse".equals(responseType)) {
            webRtcEndpointScreen.gatherCandidates();
        } else if ("viewerResponse".equals(responseType)) {
            teacherSession.getWebRtcEndpoint().connect(webRtcEndpoint);
            webRtcEndpoint.gatherCandidates();
        } else if ("viewerScreenResponse".equals(responseType)) {
            teacherSession.getWebRtcEndpointScreen().connect(webRtcEndpointScreen);
            webRtcEndpointScreen.gatherCandidates();
        }
    }

    private void addIceCandidateFoundListener(WebRtcEndpoint presenterWebRtc, final String responseId,String name) {
        presenterWebRtc.addIceCandidateFoundListener(event -> {
            JsonObject response = new JsonObject();
            response.addProperty("id", responseId);
            response.addProperty("name", name);
            response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(response.toString()));
                }
            } catch (IOException e) {
                log.debug(e.getMessage());
            }
        });
    }

    public void sendMessage(JsonObject message) throws IOException {
        log.debug("Sending message from user with session Id '{}': {}", session.getId(), message);
        synchronized (session) {
            session.sendMessage(new TextMessage(message.toString()));
        }
    }

    public void addCandidate(IceCandidate candidate) {
        webRtcEndpoint.addIceCandidate(candidate);
    }

    public void addCandidateScreen(IceCandidate candidate) {
        webRtcEndpointScreen.addIceCandidate(candidate);
    }

    public WebRtcEndpoint getWebRtcEndpoint() {
        return webRtcEndpoint;
    }

    public WebRtcEndpoint getWebRtcEndpointScreen() {
        return webRtcEndpointScreen;
    }

    public WebSocketSession getSession() {
        return session;
    }

    public String getName() {
        return name;
    }

    public String getRoomName() {
        return roomName;
    }

    public User.AccountType getAccountType() {
        return accountType;
    }

    synchronized void stop() throws IOException {
        if (accountType == User.AccountType.TEACHER) {
            Room room = roomManager.getRoom(roomName);
            ConcurrentMap<String, UserSession> participants = room.getParticipants();
            for (UserSession viewer : participants.values()) {
                JsonObject response = new JsonObject();
                response.addProperty("id", "stopCommunication");
                viewer.sendMessage(response);
            }
        } else {
            if (getWebRtcEndpoint() != null) {
                getWebRtcEndpoint().release();
            }
            if (getWebRtcEndpointScreen() != null) {
                getWebRtcEndpointScreen().release();
            }
        }
    }

    public void close() throws IOException {
        log.debug("PARTICIPANT {}: Releasing resources", this.name);

        webRtcEndpoint.release(new Continuation<Void>() {

            @Override
            public void onSuccess(Void result) {
            }

            @Override
            public void onError(Throwable cause) {
            }
        });
        webRtcEndpointScreen.release(new Continuation<Void>() {

            @Override
            public void onSuccess(Void result) {
            }

            @Override
            public void onError(Throwable cause) {
            }
        });
    }

    void receiveFromGroup(UserSession sender, String sdpOffer, String Answer) throws IOException {
        log.info("USER {}: connecting with {} in room {}", this.name, sender.getName(), this.roomName);

        log.trace("USER {}: SdpOffer for {} is {}", this.name, sender.getName(), sdpOffer);
        String ipSdpAnswer = null;
        if ("receiveVideoAnswer".equals(Answer)) {
            ipSdpAnswer = this.getEndpointForUser(sender).processOffer(sdpOffer);
        } else if ("receiveScreenAnswer".equals(Answer)) {
            ipSdpAnswer = this.getScreenEndpointForUser(sender).processOffer(sdpOffer);
        }
        final JsonObject scParams = new JsonObject();
        scParams.addProperty("id", Answer);
        scParams.addProperty("name", sender.getName());
        scParams.addProperty("sdpAnswer", ipSdpAnswer);

        log.trace("USER {}: SdpAnswer for {} is {}", this.name, sender.getName(), ipSdpAnswer);
        this.sendMessage(scParams);
        log.debug("gather candidates");
        if ("receiveVideoAnswer".equals(Answer)) {
            this.getEndpointForUser(sender).gatherCandidates();
        } else if ("receiveScreenAnswer".equals(Answer)) {
            this.getScreenEndpointForUser(sender).gatherCandidates();
        }
    }

    private WebRtcEndpoint getEndpointForUser(final UserSession sender) {
        return getWebRtcEndpointForUser(sender, webRtcEndpoint, incomingMedia, "iceCandidate");
    }


    private WebRtcEndpoint getScreenEndpointForUser(final UserSession sender) {
        return getWebRtcEndpointForUser(sender, webRtcEndpointScreen, incomingMediaScreen, "iceCandidateScreen");
    }

    private WebRtcEndpoint getWebRtcEndpointForUser(final UserSession sender,
                                                    WebRtcEndpoint outgoingMedia,
                                                    ConcurrentMap<String, WebRtcEndpoint> incomingMedia,
                                                    final String iceCandidateId) {
        if (sender.getName().equals(name)) {
            log.debug("PARTICIPANT {}: configuring loopback", this.name);
            return outgoingMedia;
        }

        log.debug("PARTICIPANT {}: receiving video from {}", this.name, sender.getName());

        WebRtcEndpoint incoming = incomingMedia.get(sender.getName());
        if (incoming == null) {
            log.debug("PARTICIPANT {}: creating new endpoint for {}", this.name, sender.getName());
            incoming = new WebRtcEndpoint.Builder(pipeline).build();
            addIceCandidateFoundListener(incoming,iceCandidateId,sender.getName());
            incomingMedia.put(sender.getName(), incoming);
        }

        log.debug("PARTICIPANT {}: obtained endpoint for {}", this.name, sender.getName());
        if ("iceCandidate".equals(iceCandidateId)) {
            sender.getWebRtcEndpoint().connect(incoming);
        } else if ("iceCandidateScreen".equals(iceCandidateId)) {
            sender.getWebRtcEndpointScreen().connect(incoming);
        }
        return incoming;
    }

    void cancelVideoFrom(final String senderName) {
        log.debug("PARTICIPANT {}: canceling video reception from {}", this.name, senderName);
        incomingMedia.remove(senderName);
        log.debug("PARTICIPANT {}: removing endpoint for {}", this.name, senderName);
        incomingMediaScreen.remove(senderName);
    }

    void addCandidate(IceCandidate candidate, String name) {
        addCandidate(candidate, name, webRtcEndpoint, incomingMedia);
    }

    void addCandidateScreen(IceCandidate candidate, String name) {
        addCandidate(candidate, name, webRtcEndpointScreen, incomingMediaScreen);
    }

    private void addCandidate(IceCandidate candidate,
                              String name,
                              WebRtcEndpoint outgoingMedia,
                              ConcurrentMap<String, WebRtcEndpoint> incomingMedia) {
        if (this.name.compareTo(name) == 0) {
            outgoingMedia.addIceCandidate(candidate);
        } else {
            WebRtcEndpoint webRtc = incomingMedia.get(name);
            if (webRtc != null) {
                webRtc.addIceCandidate(candidate);
            }
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }
        if (!(obj instanceof UserSession)) {
            return false;
        }
        UserSession other = (UserSession) obj;
        boolean eq = name.equals(other.name);
        eq &= roomName.equals(other.roomName);
        return eq;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + name.hashCode();
        result = 31 * result + roomName.hashCode();
        return result;
    }
}
