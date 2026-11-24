package com.example.helpdesk.auth;

import com.example.helpdesk.model.User;
import com.example.helpdesk.model.UserRole;
import org.springframework.security.core.authority.AuthorityUtils;

class HelpdeskPrincipal extends org.springframework.security.core.userdetails.User {

    private final Long id;
    private final UserRole role;

    HelpdeskPrincipal(User user) {
        super(user.getEmail(), user.getPasswordHash(), AuthorityUtils.createAuthorityList("ROLE_" + user.getRole().name()));
        this.id = user.getId();
        this.role = user.getRole();
    }

    Long id() {
        return id;
    }

    UserRole role() {
        return role;
    }
}
