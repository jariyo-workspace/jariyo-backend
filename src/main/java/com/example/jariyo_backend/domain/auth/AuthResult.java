package com.example.jariyo_backend.domain.auth;

import java.util.UUID;

public record AuthResult(UUID userId, AccessToken accessToken, String refreshToken) {
}
