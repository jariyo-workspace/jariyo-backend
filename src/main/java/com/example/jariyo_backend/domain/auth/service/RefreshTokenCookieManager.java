package com.example.jariyo_backend.domain.auth.service;

import java.time.Duration;
import com.example.jariyo_backend.common.config.RefreshTokenProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookieManager {
	public static final String COOKIE_NAME = "jariyo_refresh";

	private final RefreshTokenProperties properties;

	public RefreshTokenCookieManager(RefreshTokenProperties properties) {
		this.properties = properties;
	}

	public ResponseCookie create(String refreshToken) {
		return base(refreshToken)
			.maxAge(properties.ttl())
			.build();
	}

	public ResponseCookie clear() {
		return base("")
			.maxAge(Duration.ZERO)
			.build();
	}

	private ResponseCookie.ResponseCookieBuilder base(String value) {
		return ResponseCookie.from(COOKIE_NAME, value)
			.httpOnly(true)
			.secure(properties.cookieSecure())
			.sameSite("Strict")
			.path("/api/v1/auth");
	}
}
