package com.example.helpdesk.auth;

import com.example.helpdesk.model.UserRole;

import java.util.Arrays;

enum DemoAccount {
    REQUESTER("requester@helpdesk.test", UserRole.REQUESTER),
    AGENT("agent@helpdesk.test", UserRole.AGENT),
    ADMIN("admin@helpdesk.test", UserRole.ADMIN);

    static final String PASSWORD = "HelpdeskDemo!2026";

    private final String email;
    private final UserRole role;

    DemoAccount(String email, UserRole role) {
        this.email = email;
        this.role = role;
    }

    String email() {
        return email;
    }

    UserRole role() {
        return role;
    }

    static boolean isDemoEmail(String email) {
        return Arrays.stream(values()).anyMatch(account -> account.email.equals(email));
    }
}
