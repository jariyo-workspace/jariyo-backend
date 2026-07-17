package com.example.jariyo_backend.domain.store.dto;

import java.util.UUID;
import com.example.jariyo_backend.domain.user.entity.StoreMember;

public record StoreMemberSummary(UUID id, String displayName, String role, String status, boolean bookingEnabled) {
	public static StoreMemberSummary from(StoreMember member) {
		return new StoreMemberSummary(member.getId(), member.getDisplayName(), member.getRole().name(),
			member.getStatus().name(), member.isBookingEnabled());
	}
}
