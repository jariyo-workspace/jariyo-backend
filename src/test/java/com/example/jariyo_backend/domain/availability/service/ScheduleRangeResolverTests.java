package com.example.jariyo_backend.domain.availability.service;

import static com.example.jariyo_backend.domain.availability.service.ScheduleRangeResolver.resolveStoreRanges;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.domain.store.entity.BusinessHour;
import com.example.jariyo_backend.domain.store.entity.ScheduleException;
import com.example.jariyo_backend.domain.store.entity.ScheduleExceptionType;
import org.junit.jupiter.api.Test;

class ScheduleRangeResolverTests {
	private static final LocalDate DATE = LocalDate.of(2026, 8, 3);

	@Test
	void blockedPeriodIsRemovedFromBusinessHours() {
		BusinessHour hours = new BusinessHour(null, UUID.randomUUID(), DayOfWeek.MONDAY,
			LocalTime.of(9, 0), LocalTime.of(18, 0), false);
		ScheduleException blocked = new ScheduleException(null, hours.getStoreId(), DATE,
			ScheduleExceptionType.BLOCKED_PERIOD, LocalTime.of(12, 0), LocalTime.of(13, 0), null, null);

		var result = resolveStoreRanges(DATE, List.of(hours), List.of(blocked));

		assertEquals(2, result.size());
		assertTrue(result.get(0).contains(DATE.atTime(9, 0), DATE.atTime(12, 0)));
		assertTrue(result.get(1).contains(DATE.atTime(13, 0), DATE.atTime(18, 0)));
	}

	@Test
	void allDayClosureOverridesBusinessHours() {
		BusinessHour hours = new BusinessHour(null, UUID.randomUUID(), DayOfWeek.MONDAY,
			LocalTime.of(9, 0), LocalTime.of(18, 0), false);
		ScheduleException closed = new ScheduleException(null, hours.getStoreId(), DATE,
			ScheduleExceptionType.CLOSED_ALL_DAY, null, null, null, null);

		assertTrue(resolveStoreRanges(DATE, List.of(hours), List.of(closed)).isEmpty());
	}
}
