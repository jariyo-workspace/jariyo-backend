package com.example.jariyo_backend.domain.availability.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.domain.availability.dto.AvailabilityDateResponse;
import com.example.jariyo_backend.domain.availability.dto.AvailabilityResponse;
import com.example.jariyo_backend.domain.availability.dto.AvailabilitySlotResponse;
import com.example.jariyo_backend.domain.availability.dto.AvailabilitySlotStatus;
import com.example.jariyo_backend.domain.reservation.entity.Reservation;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.repository.ReservationRepository;
import com.example.jariyo_backend.domain.store.entity.BusinessHour;
import com.example.jariyo_backend.domain.store.entity.DayOfWeekValue;
import com.example.jariyo_backend.domain.store.entity.ScheduleException;
import com.example.jariyo_backend.domain.store.entity.ScheduleExceptionType;
import com.example.jariyo_backend.domain.store.entity.ServiceStatus;
import com.example.jariyo_backend.domain.store.entity.StaffSchedule;
import com.example.jariyo_backend.domain.store.entity.StaffScheduleException;
import com.example.jariyo_backend.domain.store.entity.StaffScheduleExceptionType;
import com.example.jariyo_backend.domain.store.entity.StaffService;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.entity.StorePolicy;
import com.example.jariyo_backend.domain.store.entity.StoreServiceDefinition;
import com.example.jariyo_backend.domain.store.entity.StoreStatus;
import com.example.jariyo_backend.domain.store.repository.BusinessHourRepository;
import com.example.jariyo_backend.domain.store.repository.ScheduleExceptionRepository;
import com.example.jariyo_backend.domain.store.repository.StaffScheduleExceptionRepository;
import com.example.jariyo_backend.domain.store.repository.StaffScheduleRepository;
import com.example.jariyo_backend.domain.store.repository.StaffServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StorePolicyRepository;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.store.repository.StoreServiceDefinitionRepository;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.entity.StoreMemberStatus;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import org.springframework.stereotype.Service;

@Service
public class AvailabilityService {
	private static final int SLOT_INTERVAL_MINUTES = 30;
	private static final EnumSet<ReservationStatus> ACTIVE_RESERVATION_STATUSES = EnumSet.of(
		ReservationStatus.HELD,
		ReservationStatus.CONFIRMED,
		ReservationStatus.CHECKED_IN,
		ReservationStatus.IN_SERVICE);

	private final StoreRepository storeRepository;
	private final StorePolicyRepository storePolicyRepository;
	private final StoreServiceDefinitionRepository storeServiceDefinitionRepository;
	private final BusinessHourRepository businessHourRepository;
	private final ScheduleExceptionRepository scheduleExceptionRepository;
	private final StoreMemberRepository storeMemberRepository;
	private final StaffServiceRepository staffServiceRepository;
	private final StaffScheduleRepository staffScheduleRepository;
	private final StaffScheduleExceptionRepository staffScheduleExceptionRepository;
	private final ReservationRepository reservationRepository;
	private final Clock clock;

	public AvailabilityService(
		StoreRepository storeRepository,
		StorePolicyRepository storePolicyRepository,
		StoreServiceDefinitionRepository storeServiceDefinitionRepository,
		BusinessHourRepository businessHourRepository,
		ScheduleExceptionRepository scheduleExceptionRepository,
		StoreMemberRepository storeMemberRepository,
		StaffServiceRepository staffServiceRepository,
		StaffScheduleRepository staffScheduleRepository,
		StaffScheduleExceptionRepository staffScheduleExceptionRepository,
		ReservationRepository reservationRepository,
		Clock clock
	) {
		this.storeRepository = storeRepository;
		this.storePolicyRepository = storePolicyRepository;
		this.storeServiceDefinitionRepository = storeServiceDefinitionRepository;
		this.businessHourRepository = businessHourRepository;
		this.scheduleExceptionRepository = scheduleExceptionRepository;
		this.storeMemberRepository = storeMemberRepository;
		this.staffServiceRepository = staffServiceRepository;
		this.staffScheduleRepository = staffScheduleRepository;
		this.staffScheduleExceptionRepository = staffScheduleExceptionRepository;
		this.reservationRepository = reservationRepository;
		this.clock = clock;
	}

	public AvailabilityResponse getAvailability(UUID storeId, UUID serviceId, UUID staffId, LocalDate from,
		LocalDate to, int partySize) {
		if (from.isAfter(to)) {
			throw new BusinessException(ErrorCode.INVALID_AVAILABILITY_RANGE);
		}

		Store store = storeRepository.findByIdAndStatus(storeId, StoreStatus.ACTIVE)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
		StorePolicy policy = storePolicyRepository.findByStoreId(storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_POLICY_NOT_FOUND));
		StoreServiceDefinition serviceDefinition = storeServiceDefinitionRepository
			.findByIdAndStoreIdAndStatus(serviceId, storeId, ServiceStatus.ACTIVE)
			.orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_NOT_FOUND));
		if (partySize > serviceDefinition.getCapacity()) {
			throw new BusinessException(ErrorCode.INVALID_PARTY_SIZE);
		}

		ZoneId zoneId = ZoneId.of(store.getTimezone());
		ZonedDateTime now = Instant.now(clock).atZone(zoneId);
		List<StoreMember> candidates = resolveCandidateMembers(storeId, staffId);
		Map<UUID, StaffService> staffServices = resolveStaffServices(serviceDefinition, candidates);
		Map<UUID, List<StaffSchedule>> schedulesByStaffId = staffScheduleRepository
			.findAllByStoreMemberIdIn(staffServices.keySet())
			.stream()
			.collect(Collectors.groupingBy(StaffSchedule::getStoreMemberId));
		Map<LocalDate, List<ScheduleException>> storeExceptionsByDate = scheduleExceptionRepository
			.findAllByStoreIdAndTargetDateBetween(storeId, from, to)
			.stream()
			.collect(Collectors.groupingBy(ScheduleException::getTargetDate));
		Map<UUID, Map<LocalDate, List<StaffScheduleException>>> staffExceptions = staffScheduleExceptionRepository
			.findAllByStoreMemberIdInAndTargetDateBetween(staffServices.keySet(), from, to)
			.stream()
			.collect(Collectors.groupingBy(StaffScheduleException::getStoreMemberId,
				Collectors.groupingBy(StaffScheduleException::getTargetDate)));
		Map<UUID, List<Reservation>> reservationsByStaffId = reservationRepository
			.findActiveReservationsForAvailability(storeId, staffServices.keySet(), ACTIVE_RESERVATION_STATUSES,
				from.atStartOfDay(zoneId).toInstant(), to.plusDays(1).atStartOfDay(zoneId).toInstant())
			.stream()
			.collect(Collectors.groupingBy(Reservation::getAssignedStaffId));
		Map<DayOfWeekValue, List<BusinessHour>> businessHoursByDay = businessHourRepository.findAllByStoreId(storeId)
			.stream()
			.collect(Collectors.groupingBy(BusinessHour::getDayOfWeek));

		List<AvailabilityDateResponse> dates = new ArrayList<>();
		for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
			List<AvailabilitySlotResponse> slots = calculateSlotsForDate(date, now, zoneId, policy, serviceDefinition,
				candidates, staffServices, schedulesByStaffId, staffExceptions, reservationsByStaffId,
				businessHoursByDay, storeExceptionsByDate.getOrDefault(date, List.of()));
			dates.add(new AvailabilityDateResponse(date, slots));
		}

		return new AvailabilityResponse(storeId, serviceId, staffId, dates);
	}

	private List<StoreMember> resolveCandidateMembers(UUID storeId, UUID staffId) {
		if (staffId != null) {
			StoreMember member = storeMemberRepository.findByIdAndStoreId(staffId, storeId)
				.filter(candidate -> candidate.getStatus() == StoreMemberStatus.ACTIVE && candidate.isBookingEnabled())
				.orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));
			return List.of(member);
		}
		return storeMemberRepository.findAllByStoreIdAndStatusAndBookingEnabledTrue(storeId, StoreMemberStatus.ACTIVE);
	}

	private Map<UUID, StaffService> resolveStaffServices(StoreServiceDefinition serviceDefinition, List<StoreMember> candidates) {
		List<UUID> memberIds = candidates.stream()
			.map(StoreMember::getId)
			.toList();
		Map<UUID, StaffService> staffServices = staffServiceRepository
			.findAllByServiceIdAndActiveTrueAndStoreMemberIdIn(serviceDefinition.getId(), memberIds)
			.stream()
			.collect(Collectors.toMap(StaffService::getStoreMemberId, staffService -> staffService));
		if (staffServices.isEmpty()) {
			throw new BusinessException(ErrorCode.STAFF_NOT_FOUND);
		}
		return staffServices.entrySet().stream()
			.sorted(Map.Entry.<UUID, StaffService>comparingByKey())
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, HashMap::new));
	}

	private List<AvailabilitySlotResponse> calculateSlotsForDate(LocalDate date, ZonedDateTime now, ZoneId zoneId,
		StorePolicy policy, StoreServiceDefinition serviceDefinition, List<StoreMember> candidates,
		Map<UUID, StaffService> staffServices, Map<UUID, List<StaffSchedule>> schedulesByStaffId,
		Map<UUID, Map<LocalDate, List<StaffScheduleException>>> staffExceptions, Map<UUID, List<Reservation>> reservationsByStaffId,
		Map<DayOfWeekValue, List<BusinessHour>> businessHoursByDay, List<ScheduleException> storeExceptions) {
		DayOfWeekValue dayOfWeek = DayOfWeekValue.valueOf(date.getDayOfWeek().name());
		List<TimeRange> storeRanges = resolveStoreRanges(date, businessHoursByDay.getOrDefault(dayOfWeek, List.of()),
			storeExceptions);
		if (storeRanges.isEmpty()) {
			return List.of();
		}

		ZonedDateTime bookingWindowEnd = now.truncatedTo(ChronoUnit.DAYS).plusDays(policy.getBookingOpenDays() + 1L);
		ZonedDateTime minimumBookableTime = now.plusMinutes(policy.getMinimumBookingNoticeMinutes());
		if (date.atStartOfDay(zoneId).isAfter(bookingWindowEnd)) {
			return List.of();
		}

		List<StoreMember> sortedCandidates = candidates.stream()
			.filter(member -> staffServices.containsKey(member.getId()))
			.sorted(Comparator.comparing(StoreMember::getDisplayName).thenComparing(StoreMember::getId))
			.toList();

		List<AvailabilitySlotResponse> slots = new ArrayList<>();
		for (StoreMember member : sortedCandidates) {
			UUID staffId = member.getId();
			List<TimeRange> staffRanges = resolveStaffRanges(date,
				schedulesByStaffId.getOrDefault(staffId, List.of()),
				staffExceptions.getOrDefault(staffId, Map.of()).getOrDefault(date, List.of()));
			if (staffRanges.isEmpty()) {
				continue;
			}
			List<TimeRange> bookableRanges = intersect(storeRanges, staffRanges);
			int durationMinutes = resolveServiceDuration(serviceDefinition, staffServices.get(staffId));
			Duration serviceDuration = Duration.ofMinutes(durationMinutes);
			Duration occupiedDuration = serviceDuration.plusMinutes(serviceDefinition.getCleanupMinutes());
			for (TimeRange range : bookableRanges) {
				LocalDateTime cursor = ceilToSlot(range.start());
				while (!cursor.plus(occupiedDuration).isAfter(range.end())) {
					ZonedDateTime startAt = cursor.atZone(zoneId);
					if (!startAt.isBefore(minimumBookableTime) && !startAt.isAfter(bookingWindowEnd)
						&& isAvailable(reservationsByStaffId.getOrDefault(staffId, List.of()), startAt, occupiedDuration)) {
						ZonedDateTime serviceEndAt = startAt.plus(serviceDuration);
						ZonedDateTime occupiedUntil = startAt.plus(occupiedDuration);
						slots.add(new AvailabilitySlotResponse(
							startAt.toOffsetDateTime(),
							serviceEndAt.toOffsetDateTime(),
							occupiedUntil.toOffsetDateTime(),
							staffId,
							AvailabilitySlotStatus.AVAILABLE));
					}
					cursor = cursor.plusMinutes(SLOT_INTERVAL_MINUTES);
				}
			}
		}

		return slots.stream()
			.sorted(Comparator.comparing(AvailabilitySlotResponse::startAt))
			.toList();
	}

	private List<TimeRange> resolveStoreRanges(LocalDate date, List<BusinessHour> businessHours,
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

	private List<TimeRange> resolveStaffRanges(LocalDate date, List<StaffSchedule> schedules,
		List<StaffScheduleException> exceptions) {
		if (exceptions.stream().anyMatch(exception -> exception.getType() == StaffScheduleExceptionType.DAY_OFF)) {
			return List.of();
		}
		DayOfWeekValue dayOfWeek = DayOfWeekValue.valueOf(date.getDayOfWeek().name());
		List<TimeRange> baseRanges = schedules.stream()
			.filter(schedule -> schedule.getDayOfWeek() == dayOfWeek)
			.filter(schedule -> !date.isBefore(schedule.getValidFrom()))
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

	private int resolveServiceDuration(StoreServiceDefinition serviceDefinition, StaffService staffService) {
		return staffService.getCustomDurationMinutes() != null
			? staffService.getCustomDurationMinutes()
			: serviceDefinition.getDurationMinutes();
	}

	private boolean isAvailable(List<Reservation> reservations, ZonedDateTime slotStart, Duration occupiedDuration) {
		Instant slotStartInstant = slotStart.toInstant();
		Instant slotEndInstant = slotStart.plus(occupiedDuration).toInstant();
		return reservations.stream()
			.noneMatch(reservation -> reservation.getStartAt().isBefore(slotEndInstant)
				&& reservation.getOccupiedUntil().isAfter(slotStartInstant));
	}

	private List<TimeRange> intersect(List<TimeRange> left, List<TimeRange> right) {
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

	private List<TimeRange> subtract(List<TimeRange> source, List<TimeRange> blocked) {
		List<TimeRange> result = new ArrayList<>(source);
		for (TimeRange blockedRange : blocked) {
			List<TimeRange> updated = new ArrayList<>();
			for (TimeRange range : result) {
				if (!overlaps(range, blockedRange)) {
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

	private boolean overlaps(TimeRange left, TimeRange right) {
		return left.start().isBefore(right.end()) && right.start().isBefore(left.end());
	}

	private LocalDateTime ceilToSlot(LocalDateTime time) {
		LocalDateTime truncated = time.truncatedTo(ChronoUnit.MINUTES);
		int minute = truncated.getMinute();
		int offset = minute % SLOT_INTERVAL_MINUTES;
		if (offset == 0) {
			return truncated;
		}
		return truncated.plusMinutes(SLOT_INTERVAL_MINUTES - offset).withSecond(0).withNano(0);
	}

	private record TimeRange(LocalDateTime start, LocalDateTime end) {
	}
}
