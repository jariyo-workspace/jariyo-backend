package com.example.jariyo_backend.domain.store.dto;

import java.util.UUID;
import com.example.jariyo_backend.domain.store.entity.StaffService;
import com.example.jariyo_backend.domain.user.entity.StoreMember;

public record ServiceStaffSummary(UUID id, String displayName, boolean bookingEnabled, Integer customDurationMinutes) {
	public static ServiceStaffSummary from(StoreMember member, StaffService service) {
		return new ServiceStaffSummary(member.getId(), member.getDisplayName(), member.isBookingEnabled(),
			service.getCustomDurationMinutes());
	}
}
