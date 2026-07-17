package com.example.jariyo_backend.domain.auth;

public record AccessToken(String value, long expiresIn) {
}
