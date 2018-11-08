package com.sendroid.kurento.one2manycall.service;

import com.sendroid.kurento.one2manycall.entity.User;
import com.sendroid.kurento.one2manycall.respository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserService {

    private UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User findUserByUsername(String username){
        return userRepository.findUserByUsername(username);
    }
}
