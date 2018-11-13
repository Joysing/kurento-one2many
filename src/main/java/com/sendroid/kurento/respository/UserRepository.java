package com.sendroid.kurento.respository;

import com.sendroid.kurento.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;


public interface UserRepository extends JpaRepository<User, Long> {

    User findUserByUsername(String username);
}
