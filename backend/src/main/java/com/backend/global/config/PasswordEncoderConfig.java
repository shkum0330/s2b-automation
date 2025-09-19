package com.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {
    /**
     * Exposes a PasswordEncoder bean using BCrypt for hashing passwords.
     *
     * <p>Registers a singleton PasswordEncoder (BCryptPasswordEncoder) in the Spring context
     * for injection wherever password encoding/verification is required.
     *
     * @return a BCrypt-based PasswordEncoder instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
