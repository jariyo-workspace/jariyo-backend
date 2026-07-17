package com.example.jariyo_backend.domain.store.dto;

import java.util.UUID;
import com.example.jariyo_backend.domain.store.entity.ScheduleException;

public record ScheduleExceptionSummary(UUID id, String targetDate, String type, String startTime, String endTime,
	String reason) {
	public static ScheduleExceptionSummary from(ScheduleException exception) {
		return new ScheduleExceptionSummary(exception.getId(), exception.getTargetDate().toString(),
			exception.getType().name(), exception.getStartTime() == null ? null : exception.getStartTime().toString(),
			exception.getEndTime() == null ? null : exception.getEndTime().toString(), exception.getReason());
	}
}
