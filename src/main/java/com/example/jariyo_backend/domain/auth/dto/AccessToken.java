package com.example.jariyo_backend.domain.auth.dto;

public record AccessToken(String value, long expiresIn) {
}
