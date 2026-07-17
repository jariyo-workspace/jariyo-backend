package com.example.jariyo_backend.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.refresh-token")
public record RefreshTokenProperties(Duration ttl, boolean cookieSecure) {
}
