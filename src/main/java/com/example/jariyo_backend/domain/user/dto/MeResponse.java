package com.example.jariyo_backend.domain.user.dto;

import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.domain.user.entity.StoreMemberRole;
import com.example.jariyo_backend.domain.user.entity.StoreMemberStatus;

public record MeResponse(
	UUID id,
	String email,
	String displayName,
	String phoneNumber,
	CustomerProfileResponse customerProfile,
	List<StoreMembershipResponse> storeMemberships
) {
	public record CustomerProfileResponse(boolean notificationConsent, boolean marketingConsent) {
	}

	public record StoreMembershipResponse(UUID storeId, StoreMemberRole role, StoreMemberStatus status) {
	}
}
