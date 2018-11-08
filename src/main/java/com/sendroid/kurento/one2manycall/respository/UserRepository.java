package com.sendroid.kurento.one2manycall.respository;

import com.sendroid.kurento.one2manycall.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;


public interface UserRepository extends JpaRepository<User, Long> {

    User findUserByUsername(String username);
}
