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

package com.sendroid.kurento.one2manycall;

import com.google.gson.JsonObject;
import com.sendroid.kurento.one2manycall.entity.User;
import org.kurento.client.Continuation;
import org.kurento.client.IceCandidate;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
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
    private WebRtcEndpoint webRtcEndpoint;
    private WebRtcEndpoint webRtcEndpointScreen;

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
        webRtcEndpoint = new WebRtcEndpoint.Builder(pipeline).build();
        webRtcEndpointScreen = new WebRtcEndpoint.Builder(pipeline).build();

        addIceCandidateFoundListener(webRtcEndpoint, "iceCandidate");
        addIceCandidateFoundListener(webRtcEndpointScreen, "iceCandidateScreen");
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

    private void addIceCandidateFoundListener(WebRtcEndpoint presenterWebRtc, final String responseId) {
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
            Room room=roomManager.getRoom(roomName);
            ConcurrentMap<String, UserSession> participants=room.getParticipants();
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
}
