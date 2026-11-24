package com.example.helpdesk.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
class DemoAccountSeeder implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    DemoAccountSeeder(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (DemoAccount account : DemoAccount.values()) {
            jdbc.update("""
                    INSERT INTO users (email, password_hash, role)
                    VALUES (?, ?, ?)
                    ON CONFLICT (email) DO NOTHING
                    """, account.email(), passwordEncoder.encode(DemoAccount.PASSWORD), account.role().name());
        }
    }
}
