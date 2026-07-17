package com.example.jariyo_backend.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
	String issuer,
	String audience,
	Duration accessTokenTtl,
	String publicKey,
	String privateKey
) {
}
