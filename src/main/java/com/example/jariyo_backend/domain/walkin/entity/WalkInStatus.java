package com.example.jariyo_backend.domain.walkin.entity;

public enum WalkInStatus {
	WAITING,
	CALLED,
	CHECKED_IN,
	IN_SERVICE,
	COMPLETED,
	SKIPPED,
	CANCELLED,
	NO_SHOW;

	public boolean isActive() {
		return this == WAITING || this == CALLED || this == CHECKED_IN || this == IN_SERVICE || this == SKIPPED;
	}
}
