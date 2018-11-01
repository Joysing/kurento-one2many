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

package org.kurento.tutorial.one2manycall;

import com.google.gson.JsonObject;
import org.kurento.client.*;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

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
  private final MediaPipeline pipeline;
  private final WebSocketSession session;
  private WebRtcEndpoint webRtcEndpoint;
  private WebRtcEndpoint webRtcEndpointScreen;
    @Autowired
    private UserRegistry registry;


    UserSession(String name,
              String roomName,
              WebSocketSession session,
              MediaPipeline pipeline) {
    this.session = session;
    this.name = name;
    this.roomName = roomName;
    this.pipeline = pipeline;
    webRtcEndpoint = new WebRtcEndpoint.Builder(pipeline).build();
    webRtcEndpointScreen = new WebRtcEndpoint.Builder(pipeline).build();

    addIceCandidateFoundListener(webRtcEndpoint,"iceCandidate");
    addIceCandidateFoundListener(webRtcEndpointScreen,"iceCandidateScreen");
  }

    synchronized void precenter(UserSession sender, String sdpOffer, String responseType) throws IOException {
        log.info("USER {}: connecting with {} in room {}", this.name, sender.getName(), this.roomName);

        log.trace("USER {}: SdpOffer for {} is {}", this.name, sender.getName(), sdpOffer);
        String ipSdpAnswer = null;
        if ("presenterResponse".equals(responseType)) {
            ipSdpAnswer = webRtcEndpoint.processOffer(sdpOffer);
        } else if ("presenterScreenResponse".equals(responseType)) {
            ipSdpAnswer = webRtcEndpointScreen.processOffer(sdpOffer);
        }
        final JsonObject scParams = new JsonObject();
        scParams.addProperty("id", responseType);
        scParams.addProperty("response", "accepted");
        scParams.addProperty("name", sender.getName());
        scParams.addProperty("sdpAnswer", ipSdpAnswer);

        log.trace("USER {}: SdpAnswer for {} is {}", this.name, sender.getName(), ipSdpAnswer);
        synchronized (session) {
            this.sendMessage(scParams);
        }
        log.debug("gather candidates");
        if ("presenterResponse".equals(responseType)) {
            webRtcEndpoint.gatherCandidates();
        } else if ("presenterScreenResponse".equals(responseType)) {
            webRtcEndpointScreen.gatherCandidates();
        }
    }
    synchronized void viewer(UserSession sender, String sdpOffer, String responseType) throws IOException {
        log.info("USER {}: connecting with {} in room {}", this.name, sender.getName(), this.roomName);

        log.trace("USER {}: SdpOffer for {} is {}", this.name, sender.getName(), sdpOffer);
        String ipSdpAnswer = null;
        if ("viewerResponse".equals(responseType)) {
            ipSdpAnswer = webRtcEndpoint.processOffer(sdpOffer);
        } else if ("viewerScreenResponse".equals(responseType)) {
            ipSdpAnswer = webRtcEndpointScreen.processOffer(sdpOffer);
        }
        final JsonObject scParams = new JsonObject();
        scParams.addProperty("id", responseType);
        scParams.addProperty("response", "accepted");
        scParams.addProperty("name", sender.getName());
        scParams.addProperty("sdpAnswer", ipSdpAnswer);

        log.trace("USER {}: SdpAnswer for {} is {}", this.name, sender.getName(), ipSdpAnswer);
        synchronized (session) {
            this.sendMessage(scParams);
        }
        log.debug("gather candidates");
        if ("viewerResponse".equals(responseType)) {
            registry.getByName("teacher").getWebRtcEndpoint().connect(webRtcEndpoint);
            webRtcEndpoint.gatherCandidates();
        } else if ("viewerScreenResponse".equals(responseType)) {
            registry.getByName("teacher").getWebRtcEndpointScreen().connect(webRtcEndpointScreen);
            webRtcEndpointScreen.gatherCandidates();
        }
    }

    private void addIceCandidateFoundListener(WebRtcEndpoint presenterWebRtc,final String responseId) {
        presenterWebRtc.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

            @Override
            public void onEvent(IceCandidateFoundEvent event) {
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
            }
        });
    }
  public WebSocketSession getSession() {
    return session;
  }

  public void sendMessage(JsonObject message) throws IOException {
    log.debug("Sending message from user with session Id '{}': {}", session.getId(), message);
    session.sendMessage(new TextMessage(message.toString()));
  }

  public WebRtcEndpoint getWebRtcEndpoint() {
    return webRtcEndpoint;
  }

  public void setWebRtcEndpoint(WebRtcEndpoint webRtcEndpoint) {
    this.webRtcEndpoint = webRtcEndpoint;
  }
  public WebRtcEndpoint getWebRtcEndpointScreen() {
    return webRtcEndpointScreen;
  }

  public void setWebRtcEndpointScreen(WebRtcEndpoint webRtcEndpoint) {
    this.webRtcEndpointScreen = webRtcEndpoint;
  }

  public void addCandidate(IceCandidate candidate,String name) {
       webRtcEndpoint.addIceCandidate(candidate);
  }
  public void addCandidateScreen(IceCandidate candidate,String name) {
      webRtcEndpointScreen.addIceCandidate(candidate);
  }

    public String getName() {
        return name;
    }

    public String getRoomName() {
        return roomName;
    }

    public void close() throws IOException {
        log.debug("PARTICIPANT {}: Releasing resources", this.name);

        webRtcEndpoint.release(new Continuation<Void>() {

            @Override
            public void onSuccess(Void result) {
                log.trace("PARTICIPANT {}: Released outgoing EP", UserSession.this.name);
            }

            @Override
            public void onError(Throwable cause) {
                log.warn("USER {}: Could not release outgoing EP", UserSession.this.name);
            }
        });
        webRtcEndpointScreen.release(new Continuation<Void>() {

            @Override
            public void onSuccess(Void result) {
                log.trace("PARTICIPANT {}: Released outgoingScreen EP", UserSession.this.name);
            }

            @Override
            public void onError(Throwable cause) {
                log.warn("USER {}: Could not release outgoingScreen EP", UserSession.this.name);
            }
        });
    }
}
