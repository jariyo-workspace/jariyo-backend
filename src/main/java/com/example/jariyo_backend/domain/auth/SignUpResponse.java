package com.example.jariyo_backend.domain.auth;

import java.util.UUID;

public record SignUpResponse(UUID userId, String accessToken, long expiresIn) {
	public static SignUpResponse from(AuthResult result) {
		return new SignUpResponse(result.userId(), result.accessToken().value(), result.accessToken().expiresIn());
	}
}
