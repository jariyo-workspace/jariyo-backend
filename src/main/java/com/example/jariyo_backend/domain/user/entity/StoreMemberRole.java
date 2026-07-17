package com.example.jariyo_backend.domain.user.entity;

public enum StoreMemberRole {
	STAFF(1),
	MANAGER(2),
	OWNER(3);

	private final int level;

	StoreMemberRole(int level) {
		this.level = level;
	}

	public boolean includes(StoreMemberRole requiredRole) {
		return level >= requiredRole.level;
	}
}
