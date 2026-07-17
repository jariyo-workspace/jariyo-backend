package com.example.jariyo_backend.domain.store.dto;

import java.util.UUID;
import com.example.jariyo_backend.domain.user.entity.StoreMember;

public record StoreMemberDetail(UUID id, String displayName, String role, String status, boolean bookingEnabled,
	UUID storeId) {
	public static StoreMemberDetail from(StoreMember member) {
		return new StoreMemberDetail(member.getId(), member.getDisplayName(), member.getRole().name(),
			member.getStatus().name(), member.isBookingEnabled(), member.getStoreId());
	}
}
