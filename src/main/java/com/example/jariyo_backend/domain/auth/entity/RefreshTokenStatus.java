package com.example.jariyo_backend.domain.auth.entity;

public enum RefreshTokenStatus {
	ACTIVE,
	ROTATED,
	REVOKED,
	REUSED
}
