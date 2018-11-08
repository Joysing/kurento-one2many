package com.sendroid.kurento.one2manycall;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sendroid.kurento.one2manycall.entity.User;
import com.sendroid.kurento.one2manycall.service.UserService;
import org.kurento.client.IceCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Component
public class CallHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(CallHandler.class);
    private static final Gson gson = new GsonBuilder().create();

    @Autowired
    private RoomManager roomManager;

    @Autowired
    private UserRegistry registry;

    private static UserService userService;

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
        log.debug("Incoming message from session '{}': {}", session.getId(), jsonMessage);
        final UserSession user = registry.getBySession(session);

        switch (jsonMessage.get("id").getAsString()) {
            case "joinRoom":
                joinRoom(jsonMessage, session);
                break;
            case "testjoinRoom":
                testjoinRoom(jsonMessage,session);
                break;
            case "presenter":
                if (user != null) {
                    user.receiveFrom(registry.getByName(jsonMessage.get("name").getAsString()),
                            jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString(),
                            "presenterResponse");
                }
                break;
            case "viewer": {
                UserSession teacherSession = roomManager.getRoom(jsonMessage.get("room").getAsString()).getTeacher();
                if (teacherSession != null) {
                    user.receiveFrom(teacherSession,
                            jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString(),
                            "viewerResponse");
                } else {
                    log.info("no teacher online");
                }
                break;
            }
            case "presenterScreen":
                if (user != null) {
                    user.receiveFrom(registry.getByName(jsonMessage.get("name").getAsString()),
                            jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString(),
                            "presenterScreenResponse");
                }
                break;
            case "viewerScreen": {
                UserSession teacherSession = roomManager.getRoom(jsonMessage.get("room").getAsString()).getTeacher();
                if (teacherSession != null) {
                    user.receiveFrom(teacherSession,
                            jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString(),
                            "viewerScreenResponse");
                } else {
                    log.info("no teacher online");
                }
                break;
            }
            case "onIceCandidate": {
                JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();
                if (user != null) {
                    IceCandidate cand =
                            new IceCandidate(candidate.get("candidate").getAsString(), candidate.get("sdpMid")
                                    .getAsString(), candidate.get("sdpMLineIndex").getAsInt());
                    user.addCandidate(cand);
                }
                break;
            }
            case "onIceCandidateScreen": {
                JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();
                if (user != null) {
                    IceCandidate cand =
                            new IceCandidate(candidate.get("candidate").getAsString(), candidate.get("sdpMid")
                                    .getAsString(), candidate.get("sdpMLineIndex").getAsInt());
                    user.addCandidateScreen(cand);
                }
                break;
            }
            case "stop":
                leaveRoom(user);
                break;
            default:
                break;
        }
    }

    private void handleErrorResponse(Throwable throwable, WebSocketSession session, String responseId)
            throws IOException {
        log.error(throwable.getMessage(), throwable);
        JsonObject response = new JsonObject();
        response.addProperty("id", responseId);
        response.addProperty("response", "rejected");
        response.addProperty("message", throwable.getMessage());
        session.sendMessage(new TextMessage(response.toString()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        UserSession user = registry.removeBySession(session);
        roomManager.getRoom(user.getRoomName()).leave(user);
    }

    private void joinRoom(JsonObject params, WebSocketSession session) throws IOException {
        final String roomName = params.get("room").getAsString();
        final String name = params.get("name").getAsString();
        User.AccountType accountType=userService.findUserByUsername(name).getAccountType();
        log.info("PARTICIPANT {}: trying to join room {}", name, roomName);

        Room room = roomManager.getRoom(roomName);
        final UserSession user = room.join(name, accountType,session);
        registry.register(user);
    }
    private void testjoinRoom(JsonObject params,WebSocketSession session) throws IOException {
        final String roomName = params.get("room").getAsString();
        final String name = params.get("name").getAsString();
        User.AccountType accountType= User.AccountType.STUDENT;
        log.info("PARTICIPANT {}: trying to join room {}", name, roomName);

        Room room = roomManager.getRoom(roomName);
        final UserSession user = room.join(name, accountType,session);
        registry.register(user);
    }

    private void leaveRoom(UserSession user) throws IOException {
        final Room room = roomManager.getRoom(user.getRoomName());
        room.leave(user);
        if (room.getParticipants().values().isEmpty()) {
            roomManager.removeRoom(room);
        }
    }
    @Autowired
    public void setUserService(UserService userService){
        CallHandler.userService =userService;
    }
}
