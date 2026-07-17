package com.example.jariyo_backend.domain.store.dto;

import java.util.UUID;
import com.example.jariyo_backend.domain.store.entity.ServiceOffering;

public record ServiceSummary(UUID id, String name, String description, int durationMinutes, int cleanupMinutes,
	int capacity, String status, long availableStaffCount) {
	public static ServiceSummary from(ServiceOffering service, long availableStaffCount) {
		return new ServiceSummary(service.getId(), service.getName(), service.getDescription(),
			service.getDurationMinutes(), service.getCleanupMinutes(), service.getCapacity(),
			service.getStatus().name(), availableStaffCount);
	}
}
