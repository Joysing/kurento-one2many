package org.kurento.tutorial.one2manycall;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.kurento.client.*;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class CallHandler extends TextWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(CallHandler.class);
  private static final Gson gson = new GsonBuilder().create();


  private final ConcurrentHashMap<String, UserSession> viewers = new ConcurrentHashMap<>();

    @Autowired
    private RoomManager roomManager;

    @Autowired
    private UserRegistry registry;

  @Autowired
  private KurentoClient kurento;

  private MediaPipeline pipeline;
  private UserSession presenterUserSession;

  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
    log.debug("Incoming message from session '{}': {}", session.getId(), jsonMessage);
      final UserSession user = registry.getBySession(session);
    switch (jsonMessage.get("id").getAsString()) {
        case "joinRoom":
            joinRoom(jsonMessage, session);
            break;
      case "presenter":
          if (user != null) {
              user.precenter(registry.getByName(jsonMessage.get("name").getAsString()),
                      jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString(),
                      "presenterResponse");
          }
          break;
//        try {
//          presenter(session, jsonMessage,"presenterResponse");
//        } catch (Throwable t) {
//          handleErrorResponse(t, session, "presenterResponse");
//        }
//        break;
      case "viewer":
          if (user != null) {
              user.viewer(registry.getByName(jsonMessage.get("name").getAsString()),
                      jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString(),
                      "viewerResponse");
          }
//        try {
//          viewer(session, jsonMessage,"viewerResponse");
//        } catch (Throwable t) {
//          handleErrorResponse(t, session, "viewerResponse");
//        }
        break;
      case "presenterScreen":
          if (user != null) {
              user.precenter(registry.getByName(jsonMessage.get("name").getAsString()),
                      jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString(),
                      "presenterScreenResponse");
          }
          break;
//        try {
//          presenter(session, jsonMessage,"presenterScreenResponse");
//        } catch (Throwable t) {
//          handleErrorResponse(t, session, "presenterResponse");
//        }
//        break;
      case "viewerScreen":
          user.viewer(registry.getByName(jsonMessage.get("name").getAsString()),
                  jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString(),
                  "viewerScreenResponse");
//        try {
//          viewer(session, jsonMessage,"viewerScreenResponse");
//        } catch (Throwable t) {
//          handleErrorResponse(t, session, "viewerResponse");
//        }
        break;
      case "onIceCandidate": {

          JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();
          if (user != null) {
              IceCandidate cand =
                      new IceCandidate(candidate.get("candidate").getAsString(), candidate.get("sdpMid")
                              .getAsString(), candidate.get("sdpMLineIndex").getAsInt());
              user.addCandidate(cand, jsonMessage.get("name").getAsString());
          }

          break;
      }
      case "onIceCandidateScreen": {
          JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();
          if (user != null) {
              IceCandidate cand =
                      new IceCandidate(candidate.get("candidate").getAsString(), candidate.get("sdpMid")
                              .getAsString(), candidate.get("sdpMLineIndex").getAsInt());
              user.addCandidateScreen(cand, jsonMessage.get("name").getAsString());
          }
          break;
      }
      case "stop":
        stop(session);
        break;
      default:
        break;
    }
  }

  private void handleErrorResponse(Throwable throwable, WebSocketSession session, String responseId)
      throws IOException {
    stop(session);
    log.error(throwable.getMessage(), throwable);
    JsonObject response = new JsonObject();
    response.addProperty("id", responseId);
    response.addProperty("response", "rejected");
    response.addProperty("message", throwable.getMessage());
    session.sendMessage(new TextMessage(response.toString()));
  }

//  private synchronized void presenter(final WebSocketSession session, JsonObject jsonMessage,String responseType)
//      throws IOException {
//      if(presenterUserSession==null){
//          presenterUserSession = new UserSession(session);
//          pipeline = kurento.createMediaPipeline();
//      }
//      WebRtcEndpoint presenterWebRtc = new WebRtcEndpoint.Builder(pipeline).build();
//    if ("presenterResponse".equals(responseType)) {
//        presenterUserSession.setWebRtcEndpoint(presenterWebRtc);
//        addIceCandidateFoundListener(session, presenterWebRtc,"iceCandidate");
//    }else{
//        presenterUserSession.setWebRtcEndpointScreen(presenterWebRtc);
//        addIceCandidateFoundListener(session, presenterWebRtc,"iceCandidateScreen");
//    }
//        String sdpOffer = jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString();
//        String sdpAnswer = presenterWebRtc.processOffer(sdpOffer);
//
//        JsonObject response = new JsonObject();
//        response.addProperty("id", responseType);
//        response.addProperty("response", "accepted");
//        response.addProperty("sdpAnswer", sdpAnswer);
//
//        synchronized (session) {
//            presenterUserSession.sendMessage(response);
//        }
//        presenterWebRtc.gatherCandidates();
//
//  }

    private void addIceCandidateFoundListener(final WebSocketSession session, WebRtcEndpoint presenterWebRtc,final String responseId) {
        presenterWebRtc.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

            @Override
            public void onEvent(IceCandidateFoundEvent event) {
                JsonObject response = new JsonObject();
                response.addProperty("id", responseId);
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

//    private synchronized void viewer(final WebSocketSession session, JsonObject jsonMessage,String responseType)
//      throws IOException {
//    if (presenterUserSession == null || presenterUserSession.getWebRtcEndpoint() == null) {
//      JsonObject response = new JsonObject();
//      response.addProperty("id", "viewerResponse");
//      response.addProperty("response", "rejected");
//      response.addProperty("message",
//          "No active sender now. Become sender or . Try again later ...");
//      session.sendMessage(new TextMessage(response.toString()));
//    } else {
//        UserSession viewer;
//        if (!viewers.containsKey(session.getId())) {
//            viewer = new UserSession(session);
//            viewers.put(session.getId(), viewer);
//        }else{
//            viewer=viewers.get(session.getId());
//        }
//        WebRtcEndpoint nextWebRtc = new WebRtcEndpoint.Builder(pipeline).build();
//        if("viewerResponse".equals(responseType)) {
//            addIceCandidateFoundListener(session, nextWebRtc,"iceCandidate");
//            viewer.setWebRtcEndpoint(nextWebRtc);
//            presenterUserSession.getWebRtcEndpoint().connect(nextWebRtc);
//        }else{
//            addIceCandidateFoundListener(session, nextWebRtc,"iceCandidateScreen");
//            viewer.setWebRtcEndpointScreen(nextWebRtc);
//            presenterUserSession.getWebRtcEndpointScreen().connect(nextWebRtc);
//
//        }
//        String sdpOffer = jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString();
//        String sdpAnswer = nextWebRtc.processOffer(sdpOffer);
//
//        JsonObject response = new JsonObject();
//        response.addProperty("id", responseType);
//        response.addProperty("response", "accepted");
//        response.addProperty("sdpAnswer", sdpAnswer);
//
//        synchronized (session) {
//            viewer.sendMessage(response);
//        }
//        nextWebRtc.gatherCandidates();
//    }
//  }

  private synchronized void stop(WebSocketSession session) throws IOException {
    String sessionId = session.getId();
    if (presenterUserSession != null && presenterUserSession.getSession().getId().equals(sessionId)) {
      for (UserSession viewer : viewers.values()) {
        JsonObject response = new JsonObject();
        response.addProperty("id", "stopCommunication");
        viewer.sendMessage(response);
      }

      log.info("Releasing media pipeline");
      if (pipeline != null) {
        pipeline.release();
      }
      pipeline = null;
      presenterUserSession = null;
    } else if (viewers.containsKey(sessionId)) {
      if (viewers.get(sessionId).getWebRtcEndpoint() != null) {
        viewers.get(sessionId).getWebRtcEndpoint().release();
      }
      if (viewers.get(sessionId).getWebRtcEndpointScreen() != null) {
        viewers.get(sessionId).getWebRtcEndpointScreen().release();
      }
      viewers.remove(sessionId);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    stop(session);
  }


    private void joinRoom(JsonObject params, WebSocketSession session) throws IOException {
        final String roomName = params.get("room").getAsString();
        final String name = params.get("name").getAsString();
        log.info("PARTICIPANT {}: trying to join room {}", name, roomName);

        Room room = roomManager.getRoom(roomName);
        final UserSession user = room.join(name, session);
        registry.register(user);
    }

    private void leaveRoom(UserSession user) throws IOException {
        final Room room = roomManager.getRoom(user.getRoomName());
        room.leave(user);
        if (room.getParticipants().isEmpty()) {
            roomManager.removeRoom(room);
        }
    }
}
