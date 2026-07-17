package com.example.jariyo_backend.domain.store.dto;

import java.util.List;
import com.example.jariyo_backend.domain.store.entity.BusinessHour;

public record BusinessHourSummary(String dayOfWeek, List<Period> periods) {
	public static BusinessHourSummary from(BusinessHour hour) {
		return new BusinessHourSummary(hour.getDayOfWeek().name(),
			List.of(new Period(hour.getOpenTime() == null ? null : hour.getOpenTime().toString(),
				hour.getCloseTime() == null ? null : hour.getCloseTime().toString())));
	}

	public record Period(String openTime, String closeTime) {
	}
}
