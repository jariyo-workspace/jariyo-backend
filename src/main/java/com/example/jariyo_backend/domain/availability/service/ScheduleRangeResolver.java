package com.example.jariyo_backend.domain.availability.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.example.jariyo_backend.domain.store.entity.BusinessHour;
import com.example.jariyo_backend.domain.store.entity.DayOfWeekValue;
import com.example.jariyo_backend.domain.store.entity.ScheduleException;
import com.example.jariyo_backend.domain.store.entity.ScheduleExceptionType;
import com.example.jariyo_backend.domain.store.entity.StaffSchedule;
import com.example.jariyo_backend.domain.store.entity.StaffScheduleException;
import com.example.jariyo_backend.domain.store.entity.StaffScheduleExceptionType;

public final class ScheduleRangeResolver {
	private ScheduleRangeResolver() {
	}

	public static List<TimeRange> resolveStoreRanges(LocalDate date, List<BusinessHour> businessHours,
		List<ScheduleException> exceptions) {
		if (exceptions.stream().anyMatch(exception -> exception.getType() == ScheduleExceptionType.CLOSED_ALL_DAY)) {
			return List.of();
		}
		List<TimeRange> baseRanges = businessHours.stream()
			.filter(hour -> !hour.isClosed())
			.map(hour -> new TimeRange(date.atTime(hour.getOpenTime()), date.atTime(hour.getCloseTime())))
			.sorted(Comparator.comparing(TimeRange::start))
			.toList();
		List<ScheduleException> specialHours = exceptions.stream()
			.filter(exception -> exception.getType() == ScheduleExceptionType.SPECIAL_OPENING_HOURS)
			.toList();
		List<TimeRange> resolved = specialHours.isEmpty() ? baseRanges : specialHours.stream()
			.map(exception -> new TimeRange(date.atTime(exception.getStartTime()), date.atTime(exception.getEndTime())))
			.sorted(Comparator.comparing(TimeRange::start))
			.toList();
		List<TimeRange> blocked = exceptions.stream()
			.filter(exception -> exception.getType() == ScheduleExceptionType.BLOCKED_PERIOD)
			.map(exception -> new TimeRange(date.atTime(exception.getStartTime()), date.atTime(exception.getEndTime())))
			.toList();
		return subtract(resolved, blocked);
	}

	public static List<TimeRange> resolveStaffRanges(LocalDate date, List<StaffSchedule> schedules,
		List<StaffScheduleException> exceptions) {
		if (exceptions.stream().anyMatch(exception -> exception.getType() == StaffScheduleExceptionType.DAY_OFF)) {
			return List.of();
		}
		DayOfWeekValue dayOfWeek = DayOfWeekValue.valueOf(date.getDayOfWeek().name());
		List<TimeRange> baseRanges = schedules.stream()
			.filter(schedule -> schedule.getDayOfWeek() == dayOfWeek)
			.filter(schedule -> schedule.getValidFrom() == null || !date.isBefore(schedule.getValidFrom()))
			.filter(schedule -> schedule.getValidUntil() == null || !date.isAfter(schedule.getValidUntil()))
			.map(schedule -> new TimeRange(date.atTime(schedule.getStartTime()), date.atTime(schedule.getEndTime())))
			.sorted(Comparator.comparing(TimeRange::start))
			.toList();
		List<StaffScheduleException> customHours = exceptions.stream()
			.filter(exception -> exception.getType() == StaffScheduleExceptionType.CUSTOM_WORKING_HOURS)
			.toList();
		List<TimeRange> resolved = customHours.isEmpty() ? baseRanges : customHours.stream()
			.map(exception -> new TimeRange(date.atTime(exception.getStartTime()), date.atTime(exception.getEndTime())))
			.sorted(Comparator.comparing(TimeRange::start))
			.toList();
		List<TimeRange> blocked = exceptions.stream()
			.filter(exception -> exception.getType() == StaffScheduleExceptionType.BLOCKED_PERIOD)
			.map(exception -> new TimeRange(date.atTime(exception.getStartTime()), date.atTime(exception.getEndTime())))
			.toList();
		return subtract(resolved, blocked);
	}

	public static List<TimeRange> intersect(List<TimeRange> left, List<TimeRange> right) {
		List<TimeRange> intersections = new ArrayList<>();
		for (TimeRange leftRange : left) {
			for (TimeRange rightRange : right) {
				LocalDateTime start = leftRange.start().isAfter(rightRange.start()) ? leftRange.start() : rightRange.start();
				LocalDateTime end = leftRange.end().isBefore(rightRange.end()) ? leftRange.end() : rightRange.end();
				if (start.isBefore(end)) {
					intersections.add(new TimeRange(start, end));
				}
			}
		}
		return intersections;
	}

	private static List<TimeRange> subtract(List<TimeRange> source, List<TimeRange> blocked) {
		List<TimeRange> result = new ArrayList<>(source);
		for (TimeRange blockedRange : blocked) {
			List<TimeRange> updated = new ArrayList<>();
			for (TimeRange range : result) {
				if (!range.overlaps(blockedRange)) {
					updated.add(range);
					continue;
				}
				if (blockedRange.start().isAfter(range.start())) {
					updated.add(new TimeRange(range.start(), blockedRange.start()));
				}
				if (blockedRange.end().isBefore(range.end())) {
					updated.add(new TimeRange(blockedRange.end(), range.end()));
				}
			}
			result = updated;
		}
		return result.stream()
			.filter(range -> range.start().isBefore(range.end()))
			.sorted(Comparator.comparing(TimeRange::start))
			.toList();
	}

	public record TimeRange(LocalDateTime start, LocalDateTime end) {
		public boolean contains(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
			return !rangeStart.isBefore(start) && !rangeEnd.isAfter(end);
		}

		private boolean overlaps(TimeRange other) {
			return start.isBefore(other.end) && other.start.isBefore(end);
		}
	}
}
