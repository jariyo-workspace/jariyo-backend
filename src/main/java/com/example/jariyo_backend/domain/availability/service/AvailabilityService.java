package com.example.jariyo_backend.domain.availability.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import static com.example.jariyo_backend.domain.availability.service.ScheduleRangeResolver.intersect;
import static com.example.jariyo_backend.domain.availability.service.ScheduleRangeResolver.resolveStaffRanges;
import static com.example.jariyo_backend.domain.availability.service.ScheduleRangeResolver.resolveStoreRanges;
import com.example.jariyo_backend.domain.availability.service.ScheduleRangeResolver.TimeRange;
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
import com.example.jariyo_backend.domain.store.entity.ServiceStatus;
import com.example.jariyo_backend.domain.store.entity.StaffSchedule;
import com.example.jariyo_backend.domain.store.entity.StaffScheduleException;
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
		validateRange(from, to, partySize);
		AvailabilityContext context = loadContext(storeId, serviceId, staffId, from, to, partySize);

		List<AvailabilityDateResponse> dates = new ArrayList<>();
		for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
			List<AvailabilitySlotResponse> slots = calculateSlotsForDate(date, context);
			dates.add(new AvailabilityDateResponse(date, slots));
		}

		return new AvailabilityResponse(storeId, serviceId, staffId, dates);
	}

	private void validateRange(LocalDate from, LocalDate to, int partySize) {
		if (from.isAfter(to)) {
			throw new BusinessException(ErrorCode.INVALID_AVAILABILITY_RANGE);
		}
		if (partySize < 1) {
			throw new BusinessException(ErrorCode.INVALID_PARTY_SIZE);
		}
	}

	private AvailabilityContext loadContext(UUID storeId, UUID serviceId, UUID staffId, LocalDate from, LocalDate to,
		int partySize) {
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
		return new AvailabilityContext(store, policy, serviceDefinition, zoneId, now, candidates, staffServices,
			loadAvailabilityInputs(storeId, from, to, zoneId, staffServices.keySet()));
	}

	private AvailabilityInputs loadAvailabilityInputs(UUID storeId, LocalDate from, LocalDate to, ZoneId zoneId,
		Collection<UUID> staffIds) {
		Map<UUID, List<StaffSchedule>> schedulesByStaffId = staffScheduleRepository.findAllByStoreMemberIdIn(staffIds).stream()
			.collect(Collectors.groupingBy(StaffSchedule::getStoreMemberId));
		Map<LocalDate, List<ScheduleException>> storeExceptionsByDate = scheduleExceptionRepository
			.findAllByStoreIdAndTargetDateBetween(storeId, from, to).stream()
			.collect(Collectors.groupingBy(ScheduleException::getTargetDate));
		Map<UUID, Map<LocalDate, List<StaffScheduleException>>> staffExceptions = staffScheduleExceptionRepository
			.findAllByStoreMemberIdInAndTargetDateBetween(staffIds, from, to).stream()
			.collect(Collectors.groupingBy(StaffScheduleException::getStoreMemberId,
				Collectors.groupingBy(StaffScheduleException::getTargetDate)));
		Map<UUID, List<Reservation>> reservationsByStaffId = reservationRepository
			.findActiveReservationsForAvailability(storeId, staffIds, ACTIVE_RESERVATION_STATUSES,
				ReservationStatus.HELD, clock.instant(), from.atStartOfDay(zoneId).toInstant(),
				to.plusDays(1).atStartOfDay(zoneId).toInstant())
			.stream()
			.collect(Collectors.groupingBy(Reservation::getAssignedStaffId));
		Map<DayOfWeekValue, List<BusinessHour>> businessHoursByDay = businessHourRepository.findAllByStoreId(storeId)
			.stream()
			.collect(Collectors.groupingBy(BusinessHour::getDayOfWeek));
		return new AvailabilityInputs(schedulesByStaffId, storeExceptionsByDate, staffExceptions,
			reservationsByStaffId, businessHoursByDay);
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

	private List<AvailabilitySlotResponse> calculateSlotsForDate(LocalDate date, AvailabilityContext context) {
		DayOfWeekValue dayOfWeek = DayOfWeekValue.valueOf(date.getDayOfWeek().name());
		List<TimeRange> storeRanges = resolveStoreRanges(date,
			context.inputs().businessHoursByDay().getOrDefault(dayOfWeek, List.of()),
			context.inputs().storeExceptionsByDate().getOrDefault(date, List.of()));
		if (storeRanges.isEmpty()) {
			return List.of();
		}

		BookingWindow bookingWindow = bookingWindow(context.now(), context.zoneId(), context.policy());
		if (date.isAfter(bookingWindow.lastBookableDate())) {
			return List.of();
		}

		List<StoreMember> sortedCandidates = context.candidates().stream()
			.filter(member -> context.staffServices().containsKey(member.getId()))
			.sorted(Comparator.comparing(StoreMember::getDisplayName).thenComparing(StoreMember::getId))
			.toList();

		List<AvailabilitySlotResponse> slots = new ArrayList<>();
		for (StoreMember member : sortedCandidates) {
			UUID staffId = member.getId();
			List<TimeRange> staffRanges = resolveStaffRanges(date,
				context.inputs().schedulesByStaffId().getOrDefault(staffId, List.of()),
				context.inputs().staffExceptions().getOrDefault(staffId, Map.of()).getOrDefault(date, List.of()));
			if (staffRanges.isEmpty()) {
				continue;
			}
			List<TimeRange> bookableRanges = intersect(storeRanges, staffRanges);
			int durationMinutes = resolveServiceDuration(context.serviceDefinition(), context.staffServices().get(staffId));
			Duration serviceDuration = Duration.ofMinutes(durationMinutes);
			Duration occupiedDuration = serviceDuration.plusMinutes(context.serviceDefinition().getCleanupMinutes());
			for (TimeRange range : bookableRanges) {
				LocalDateTime cursor = ceilToSlot(range.start());
				while (!cursor.plus(occupiedDuration).isAfter(range.end())) {
					ZonedDateTime startAt = cursor.atZone(context.zoneId());
					if (!startAt.isBefore(bookingWindow.minimumBookableAt())
						&& !startAt.toLocalDate().isAfter(bookingWindow.lastBookableDate())
						&& isAvailable(context.inputs().reservationsByStaffId().getOrDefault(staffId, List.of()),
							startAt, occupiedDuration)) {
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

	private LocalDateTime ceilToSlot(LocalDateTime time) {
		LocalDateTime truncated = time.truncatedTo(ChronoUnit.MINUTES);
		int minute = truncated.getMinute();
		int offset = minute % SLOT_INTERVAL_MINUTES;
		if (offset == 0) {
			return truncated;
		}
		return truncated.plusMinutes(SLOT_INTERVAL_MINUTES - offset).withSecond(0).withNano(0);
	}

	private BookingWindow bookingWindow(ZonedDateTime now, ZoneId zoneId, StorePolicy policy) {
		return new BookingWindow(now.plusMinutes(policy.getMinimumBookingNoticeMinutes()),
			now.withZoneSameInstant(zoneId).toLocalDate().plusDays(policy.getBookingOpenDays()));
	}

	private record BookingWindow(ZonedDateTime minimumBookableAt, LocalDate lastBookableDate) {
	}

	private record AvailabilityContext(
		Store store,
		StorePolicy policy,
		StoreServiceDefinition serviceDefinition,
		ZoneId zoneId,
		ZonedDateTime now,
		List<StoreMember> candidates,
		Map<UUID, StaffService> staffServices,
		AvailabilityInputs inputs
	) {
	}

	private record AvailabilityInputs(
		Map<UUID, List<StaffSchedule>> schedulesByStaffId,
		Map<LocalDate, List<ScheduleException>> storeExceptionsByDate,
		Map<UUID, Map<LocalDate, List<StaffScheduleException>>> staffExceptions,
		Map<UUID, List<Reservation>> reservationsByStaffId,
		Map<DayOfWeekValue, List<BusinessHour>> businessHoursByDay
	) {
	}
}
