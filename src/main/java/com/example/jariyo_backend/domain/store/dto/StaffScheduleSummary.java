package com.example.jariyo_backend.domain.store.dto;

import java.util.UUID;
import com.example.jariyo_backend.domain.store.entity.StaffSchedule;

public record StaffScheduleSummary(UUID id, String dayOfWeek, String startTime, String endTime,
	String validFrom, String validUntil) {
	public static StaffScheduleSummary from(StaffSchedule schedule) {
		return new StaffScheduleSummary(schedule.getId(), schedule.getDayOfWeek().name(),
			schedule.getStartTime().toString(), schedule.getEndTime().toString(),
			schedule.getValidFrom() == null ? null : schedule.getValidFrom().toString(),
			schedule.getValidUntil() == null ? null : schedule.getValidUntil().toString());
	}
}
