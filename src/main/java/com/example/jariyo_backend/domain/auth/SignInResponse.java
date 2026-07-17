package com.example.jariyo_backend.domain.auth;

public record SignInResponse(String accessToken, long expiresIn) {
	public static SignInResponse from(AuthResult result) {
		return new SignInResponse(result.accessToken().value(), result.accessToken().expiresIn());
	}
}
