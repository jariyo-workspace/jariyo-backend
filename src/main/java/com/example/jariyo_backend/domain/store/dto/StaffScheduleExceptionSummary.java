package com.example.jariyo_backend.domain.store.dto;

import java.util.UUID;
import com.example.jariyo_backend.domain.store.entity.StaffScheduleException;

public record StaffScheduleExceptionSummary(UUID id, String targetDate, String type, String startTime,
	String endTime, String reason) {
	public static StaffScheduleExceptionSummary from(StaffScheduleException exception) {
		return new StaffScheduleExceptionSummary(exception.getId(), exception.getTargetDate().toString(),
			exception.getType().name(), exception.getStartTime() == null ? null : exception.getStartTime().toString(),
			exception.getEndTime() == null ? null : exception.getEndTime().toString(), exception.getReason());
	}
}
