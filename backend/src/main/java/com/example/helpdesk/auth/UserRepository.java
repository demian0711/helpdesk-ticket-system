package com.example.helpdesk.auth;

import com.example.helpdesk.model.User;
import org.springframework.data.repository.Repository;

import java.util.Optional;

interface UserRepository extends Repository<User, Long> {

    Optional<User> findByEmail(String email);
}
