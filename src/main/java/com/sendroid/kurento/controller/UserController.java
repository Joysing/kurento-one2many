package com.sendroid.kurento.controller;

import com.sendroid.kurento.entity.User;
import com.sendroid.kurento.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.kurento.jsonrpc.client.JsonRpcClient.log;

@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    private UserService userService;
    UserController(){

    }
    @PostMapping("/join_room")
    public void joinRoom(final String roomName,
                         final String name) {
        log.info("PARTICIPANT {}: trying to join room {}", name, roomName);
        userService.findUserByUsername("admin");
    }
    @GetMapping("/get_account_type")
    public User.AccountType getAccountType(String name) {
        return userService.findUserByUsername(name).getAccountType();
    }
    @PostMapping("/create_room")
    public void createRoom(final String roomName,
                         final String name) {
        log.info("PARTICIPANT {}: trying to join room {}", name, roomName);
        userService.findUserByUsername("admin");
    }

}
