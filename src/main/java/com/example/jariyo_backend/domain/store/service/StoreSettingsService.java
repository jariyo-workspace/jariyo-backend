package com.example.jariyo_backend.domain.store.service;

import static com.example.jariyo_backend.domain.availability.service.ScheduleRangeResolver.resolveStaffRanges;
import static com.example.jariyo_backend.domain.availability.service.ScheduleRangeResolver.resolveStoreRanges;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.domain.admin.entity.AuditActorType;
import com.example.jariyo_backend.domain.admin.entity.AuditLog;
import com.example.jariyo_backend.domain.admin.repository.AuditLogRepository;
import com.example.jariyo_backend.domain.reservation.entity.Reservation;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.repository.ReservationRepository;
import com.example.jariyo_backend.domain.store.entity.BusinessHour;
import com.example.jariyo_backend.domain.store.entity.ScheduleException;
import com.example.jariyo_backend.domain.store.entity.ScheduleExceptionType;
import com.example.jariyo_backend.domain.store.entity.ServiceOffering;
import com.example.jariyo_backend.domain.store.entity.ServiceStatus;
import com.example.jariyo_backend.domain.store.entity.StaffSchedule;
import com.example.jariyo_backend.domain.store.entity.StaffScheduleException;
import com.example.jariyo_backend.domain.store.entity.StaffScheduleExceptionType;
import com.example.jariyo_backend.domain.store.entity.StaffService;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.entity.StorePolicy;
import com.example.jariyo_backend.domain.store.repository.BusinessHourRepository;
import com.example.jariyo_backend.domain.store.repository.ScheduleExceptionRepository;
import com.example.jariyo_backend.domain.store.repository.ServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StaffScheduleExceptionRepository;
import com.example.jariyo_backend.domain.store.repository.StaffScheduleRepository;
import com.example.jariyo_backend.domain.store.repository.StaffServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StorePolicyRepository;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.entity.StoreMemberRole;
import com.example.jariyo_backend.domain.user.entity.StoreMemberStatus;
import com.example.jariyo_backend.domain.user.entity.UserAccount;
import com.example.jariyo_backend.domain.user.entity.UserStatus;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import com.example.jariyo_backend.domain.user.repository.UserAccountRepository;
import com.example.jariyo_backend.domain.user.service.StoreAuthorizationService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreSettingsService {
	private static final Set<ReservationStatus> ACTIVE_RESERVATION_STATUSES = EnumSet.of(
		ReservationStatus.HELD, ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN,
		ReservationStatus.IN_SERVICE);

	private final StoreRepository storeRepository;
	private final StorePolicyRepository storePolicyRepository;
	private final ServiceRepository serviceRepository;
	private final StoreMemberRepository storeMemberRepository;
	private final UserAccountRepository userAccountRepository;
	private final StaffServiceRepository staffServiceRepository;
	private final BusinessHourRepository businessHourRepository;
	private final ScheduleExceptionRepository scheduleExceptionRepository;
	private final StaffScheduleRepository staffScheduleRepository;
	private final StaffScheduleExceptionRepository staffScheduleExceptionRepository;
	private final ReservationRepository reservationRepository;
	private final StoreAuthorizationService authorizationService;
	private final AuditLogRepository auditLogRepository;
	private final EntityManager entityManager;
	private final Clock clock;

	public StoreSettingsService(StoreRepository storeRepository, StorePolicyRepository storePolicyRepository,
		ServiceRepository serviceRepository, StoreMemberRepository storeMemberRepository,
		UserAccountRepository userAccountRepository, StaffServiceRepository staffServiceRepository,
		BusinessHourRepository businessHourRepository, ScheduleExceptionRepository scheduleExceptionRepository,
		StaffScheduleRepository staffScheduleRepository,
		StaffScheduleExceptionRepository staffScheduleExceptionRepository,
		ReservationRepository reservationRepository, StoreAuthorizationService authorizationService,
		AuditLogRepository auditLogRepository, EntityManager entityManager, Clock clock) {
		this.storeRepository = storeRepository;
		this.storePolicyRepository = storePolicyRepository;
		this.serviceRepository = serviceRepository;
		this.storeMemberRepository = storeMemberRepository;
		this.userAccountRepository = userAccountRepository;
		this.staffServiceRepository = staffServiceRepository;
		this.businessHourRepository = businessHourRepository;
		this.scheduleExceptionRepository = scheduleExceptionRepository;
		this.staffScheduleRepository = staffScheduleRepository;
		this.staffScheduleExceptionRepository = staffScheduleExceptionRepository;
		this.reservationRepository = reservationRepository;
		this.authorizationService = authorizationService;
		this.auditLogRepository = auditLogRepository;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	@Transactional
	public StoreQueryService.StoreSummary updateStore(UUID userId, UUID storeId, StoreCommand command) {
		StoreMember actor = authorizeManager(userId, storeId);
		Store store = requireStore(storeId);
		String previous = store.getName() + "|" + store.getPhoneNumber() + "|" + store.getAddress();
		store.update(command.name(), command.description(), command.phoneNumber(), command.address());
		recordAudit(storeId, actor, "STORE_UPDATED", "STORE", storeId, previous, command.toString());
		return StoreQueryService.StoreSummary.from(store);
	}

	@Transactional
	public StoreQueryService.StorePolicySummary updatePolicy(UUID userId, UUID storeId, PolicyCommand command) {
		StoreMember actor = authorizeManager(userId, storeId);
		StorePolicy policy = storePolicyRepository.findByStoreId(storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_POLICY_NOT_FOUND));
		String previous = StoreQueryService.StorePolicySummary.from(policy).toString();
		policy.update(command.bookingOpenDays(), command.minimumBookingNoticeMinutes(),
			command.cancellationDeadlineMinutes(), command.checkInOpenBeforeMinutes(), command.lateToleranceMinutes(),
			command.noShowAfterMinutes(), command.reservationHoldMinutes(), command.slotOfferExpirationMinutes(),
			command.walkInCallTimeoutMinutes(), command.waitlistEnabled(), command.walkInEnabled(),
			command.autoNoShowEnabled());
		recordAudit(storeId, actor, "STORE_POLICY_UPDATED", "STORE_POLICY", storeId, previous, command.toString());
		return StoreQueryService.StorePolicySummary.from(policy);
	}

	@Transactional
	public StoreQueryService.ServiceSummary createService(UUID userId, UUID storeId, ServiceCommand command) {
		StoreMember actor = authorizeManager(userId, storeId);
		ServiceOffering service = serviceRepository.save(new ServiceOffering(storeId, command.name(), command.description(),
			command.durationMinutes(), command.cleanupMinutes(), command.capacity()));
		recordAudit(storeId, actor, "SERVICE_CREATED", "SERVICE", service.getId(), null, command.toString());
		return StoreQueryService.ServiceSummary.from(service, 0);
	}

	@Transactional
	public StoreQueryService.ServiceSummary updateService(UUID userId, UUID storeId, UUID serviceId,
		ServiceCommand command) {
		StoreMember actor = authorizeManager(userId, storeId);
		ServiceOffering service = requireService(storeId, serviceId);
		String previous = service.getName() + "|" + service.getDurationMinutes() + "|" + service.getCleanupMinutes();
		service.update(command.name(), command.description(), command.durationMinutes(), command.cleanupMinutes(),
			command.capacity());
		recordAudit(storeId, actor, "SERVICE_UPDATED", "SERVICE", serviceId, previous, command.toString());
		return serviceSummary(service);
	}

	@Transactional
	public StoreQueryService.ServiceSummary activateService(UUID userId, UUID storeId, UUID serviceId) {
		StoreMember actor = authorizeManager(userId, storeId);
		ServiceOffering service = requireService(storeId, serviceId);
		ServiceStatus previous = service.getStatus();
		service.activate();
		recordAudit(storeId, actor, "SERVICE_ACTIVATED", "SERVICE", serviceId, previous.name(), service.getStatus().name());
		return serviceSummary(service);
	}

	@Transactional
	public UpdateResult deactivateService(UUID userId, UUID storeId, UUID serviceId) {
		StoreMember actor = authorizeManager(userId, storeId);
		ServiceOffering service = requireService(storeId, serviceId);
		lockAllStaff(storeId);
		Store store = requireStore(storeId);
		List<ReservationConflict> conflicts = futureReservations(storeId).stream()
			.filter(reservation -> reservation.getServiceId().equals(serviceId))
			.map(reservation -> conflict(reservation, store))
			.toList();
		if (!conflicts.isEmpty()) {
			return UpdateResult.rejected(conflicts);
		}
		service.deactivate();
		recordAudit(storeId, actor, "SERVICE_DEACTIVATED", "SERVICE", serviceId, ServiceStatus.ACTIVE.name(),
			ServiceStatus.INACTIVE.name());
		return UpdateResult.success();
	}

	@Transactional
	public StoreQueryService.StoreMemberDetail addStaff(UUID userId, UUID storeId, StaffAddCommand command) {
		StoreMember actor = authorizeOwner(userId, storeId);
		UserAccount user = userAccountRepository.findByEmailAndStatusNot(command.email(), UserStatus.WITHDRAWN)
			.filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		if (storeMemberRepository.existsByUser_IdAndStoreId(user.getId(), storeId)) {
			throw new BusinessException(ErrorCode.STAFF_ALREADY_EXISTS);
		}
		StoreMember member = storeMemberRepository.save(new StoreMember(storeId, user, command.role(),
			command.displayName(), true));
		recordAudit(storeId, actor, "STAFF_ADDED", "STORE_MEMBER", member.getId(), null, command.toString());
		return StoreQueryService.StoreMemberDetail.from(member);
	}

	@Transactional
	public UpdateResult updateStaff(UUID userId, UUID storeId, UUID staffId, StaffUpdateCommand command) {
		StoreMember actor = authorizeManager(userId, storeId);
		StoreMember target = requireStaff(storeId, staffId);
		boolean protectedChange = target.getRole() != command.role() || target.getStatus() != command.status();
		if (protectedChange) {
			actor = authorizationService.requireOwner(userId, storeId);
			validateOwnerChange(actor, target, command.role(), command.status());
		}
		lockStaff(staffId);
		if (command.status() != StoreMemberStatus.ACTIVE) {
			List<ReservationConflict> conflicts = conflictsForStaff(storeId, staffId);
			if (!conflicts.isEmpty()) {
				return UpdateResult.rejected(conflicts);
			}
		}
		String previous = target.getDisplayName() + "|" + target.getRole() + "|" + target.getStatus() + "|"
			+ target.isBookingEnabled();
		target.update(command.displayName(), command.role(), command.bookingEnabled(), command.status());
		recordAudit(storeId, actor, "STAFF_UPDATED", "STORE_MEMBER", staffId, previous, command.toString());
		return UpdateResult.success();
	}

	@Transactional
	public UpdateResult deactivateStaff(UUID userId, UUID storeId, UUID staffId) {
		StoreMember actor = authorizeOwner(userId, storeId);
		StoreMember target = requireStaff(storeId, staffId);
		if (target.getStatus() == StoreMemberStatus.INACTIVE) {
			throw new BusinessException(ErrorCode.STAFF_ALREADY_INACTIVE);
		}
		validateOwnerChange(actor, target, target.getRole(), StoreMemberStatus.INACTIVE);
		lockStaff(staffId);
		List<ReservationConflict> conflicts = conflictsForStaff(storeId, staffId);
		if (!conflicts.isEmpty()) {
			return UpdateResult.rejected(conflicts);
		}
		target.deactivate();
		recordAudit(storeId, actor, "STAFF_DEACTIVATED", "STORE_MEMBER", staffId, null,
			StoreMemberStatus.INACTIVE.name());
		return UpdateResult.success();
	}

	@Transactional
	public UpdateResult replaceStaffServices(UUID userId, UUID storeId, UUID staffId,
		List<StaffServiceCommand> commands) {
		StoreMember actor = authorizeManager(userId, storeId);
		requireStaff(storeId, staffId);
		lockStaff(staffId);
		Set<UUID> serviceIds = new HashSet<>();
		for (StaffServiceCommand command : commands) {
			if (!serviceIds.add(command.serviceId())) {
				throw invalidSetting("담당 서비스가 중복되었습니다.");
			}
			requireService(storeId, command.serviceId());
		}
		Set<UUID> activeServiceIds = commands.stream().filter(StaffServiceCommand::active)
			.map(StaffServiceCommand::serviceId).collect(java.util.stream.Collectors.toSet());
		List<ReservationConflict> conflicts = futureReservations(storeId).stream()
			.filter(reservation -> staffId.equals(reservation.getAssignedStaffId()))
			.filter(reservation -> !activeServiceIds.contains(reservation.getServiceId()))
			.map(reservation -> conflict(reservation, requireStore(storeId)))
			.toList();
		if (!conflicts.isEmpty()) {
			return UpdateResult.rejected(conflicts);
		}
		staffServiceRepository.deleteAllByStoreMemberId(staffId);
		staffServiceRepository.flush();
		staffServiceRepository.saveAll(commands.stream().map(command -> new StaffService(null, staffId,
			command.serviceId(), command.customDurationMinutes(), command.active())).toList());
		recordAudit(storeId, actor, "STAFF_SERVICES_UPDATED", "STORE_MEMBER", staffId, null, commands.toString());
		return UpdateResult.success();
	}

	@Transactional
	public UpdateResult replaceStaffSchedules(UUID userId, UUID storeId, UUID staffId,
		List<ScheduleCommand> commands) {
		StoreMember actor = authorizeManager(userId, storeId);
		requireStaff(storeId, staffId);
		validateSchedules(commands);
		lockStaff(staffId);
		List<StaffSchedule> schedules = commands.stream().flatMap(command -> command.periods().stream()
			.map(period -> new StaffSchedule(null, staffId, command.dayOfWeek(), period.startTime(), period.endTime(),
				command.validFrom(), command.validUntil()))).toList();
		List<ReservationConflict> conflicts = staffScheduleConflicts(storeId, staffId, schedules,
			staffScheduleExceptionRepository.findAllByStoreMemberIdOrderByTargetDateAscCreatedAtAsc(staffId));
		if (!conflicts.isEmpty()) {
			return UpdateResult.rejected(conflicts);
		}
		staffScheduleRepository.deleteAllByStoreMemberId(staffId);
		staffScheduleRepository.saveAll(schedules);
		recordAudit(storeId, actor, "STAFF_SCHEDULES_UPDATED", "STORE_MEMBER", staffId, null, commands.toString());
		return UpdateResult.success();
	}

	@Transactional
	public UpdateResult createStaffException(UUID userId, UUID storeId, UUID staffId, StaffExceptionCommand command) {
		StoreMember actor = authorizeManager(userId, storeId);
		requireStaff(storeId, staffId);
		validateStaffException(command);
		lockStaff(staffId);
		List<StaffScheduleException> exceptions = new ArrayList<>(
			staffScheduleExceptionRepository.findAllByStoreMemberIdOrderByTargetDateAscCreatedAtAsc(staffId));
		StaffScheduleException exception = new StaffScheduleException(staffId, command.targetDate(), command.type(),
			command.startTime(), command.endTime(), command.reason(), actor.getId());
		exceptions.add(exception);
		List<ReservationConflict> conflicts = staffScheduleConflicts(storeId, staffId,
			staffScheduleRepository.findAllByStoreMemberIdOrderByDayOfWeekAscStartTimeAsc(staffId), exceptions);
		if (!conflicts.isEmpty()) {
			return UpdateResult.rejected(conflicts);
		}
		staffScheduleExceptionRepository.save(exception);
		recordAudit(storeId, actor, "STAFF_SCHEDULE_EXCEPTION_CREATED", "STAFF_SCHEDULE_EXCEPTION", exception.getId(),
			null, command.toString());
		return UpdateResult.success();
	}

	@Transactional
	public UpdateResult deleteStaffException(UUID userId, UUID storeId, UUID staffId, UUID exceptionId) {
		StoreMember actor = authorizeManager(userId, storeId);
		requireStaff(storeId, staffId);
		StaffScheduleException target = staffScheduleExceptionRepository.findByIdAndStoreMemberId(exceptionId, staffId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		lockStaff(staffId);
		List<StaffScheduleException> exceptions = staffScheduleExceptionRepository
			.findAllByStoreMemberIdOrderByTargetDateAscCreatedAtAsc(staffId).stream()
			.filter(value -> !value.getId().equals(exceptionId)).toList();
		List<ReservationConflict> conflicts = staffScheduleConflicts(storeId, staffId,
			staffScheduleRepository.findAllByStoreMemberIdOrderByDayOfWeekAscStartTimeAsc(staffId), exceptions);
		if (!conflicts.isEmpty()) {
			return UpdateResult.rejected(conflicts);
		}
		staffScheduleExceptionRepository.delete(target);
		recordAudit(storeId, actor, "STAFF_SCHEDULE_EXCEPTION_DELETED", "STAFF_SCHEDULE_EXCEPTION", exceptionId,
			target.toString(), null);
		return UpdateResult.success();
	}

	@Transactional
	public UpdateResult replaceBusinessHours(UUID userId, UUID storeId, List<BusinessDayCommand> commands) {
		StoreMember actor = authorizeManager(userId, storeId);
		validateBusinessHours(commands);
		lockAllStaff(storeId);
		List<BusinessHour> hours = commands.stream().flatMap(command -> command.closed()
			? java.util.stream.Stream.of(new BusinessHour(null, storeId, command.dayOfWeek(), null, null, true))
			: command.periods().stream().map(period -> new BusinessHour(null, storeId, command.dayOfWeek(),
				period.startTime(), period.endTime(), false))).toList();
		List<ReservationConflict> conflicts = storeScheduleConflicts(storeId, hours,
			scheduleExceptionRepository.findAllByStoreIdOrderByTargetDateAscCreatedAtAsc(storeId));
		if (!conflicts.isEmpty()) {
			return UpdateResult.rejected(conflicts);
		}
		businessHourRepository.deleteAllByStoreId(storeId);
		businessHourRepository.saveAll(hours);
		recordAudit(storeId, actor, "BUSINESS_HOURS_UPDATED", "STORE", storeId, null, commands.toString());
		return UpdateResult.success();
	}

	@Transactional
	public UpdateResult createStoreException(UUID userId, UUID storeId, StoreExceptionCommand command) {
		StoreMember actor = authorizeManager(userId, storeId);
		validateStoreException(command);
		lockAllStaff(storeId);
		List<ScheduleException> exceptions = new ArrayList<>(
			scheduleExceptionRepository.findAllByStoreIdOrderByTargetDateAscCreatedAtAsc(storeId));
		ScheduleException exception = new ScheduleException(null, storeId, command.targetDate(), command.type(),
			command.startTime(), command.endTime(), command.reason(), actor.getId());
		exceptions.add(exception);
		List<ReservationConflict> conflicts = storeScheduleConflicts(storeId,
			businessHourRepository.findAllByStoreId(storeId), exceptions);
		if (!conflicts.isEmpty()) {
			return UpdateResult.rejected(conflicts);
		}
		scheduleExceptionRepository.save(exception);
		recordAudit(storeId, actor, "STORE_SCHEDULE_EXCEPTION_CREATED", "SCHEDULE_EXCEPTION", exception.getId(),
			null, command.toString());
		return UpdateResult.success();
	}

	@Transactional
	public UpdateResult deleteStoreException(UUID userId, UUID storeId, UUID exceptionId) {
		StoreMember actor = authorizeManager(userId, storeId);
		ScheduleException target = scheduleExceptionRepository.findByIdAndStoreId(exceptionId, storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		lockAllStaff(storeId);
		List<ScheduleException> exceptions = scheduleExceptionRepository
			.findAllByStoreIdOrderByTargetDateAscCreatedAtAsc(storeId).stream()
			.filter(value -> !value.getId().equals(exceptionId)).toList();
		List<ReservationConflict> conflicts = storeScheduleConflicts(storeId,
			businessHourRepository.findAllByStoreId(storeId), exceptions);
		if (!conflicts.isEmpty()) {
			return UpdateResult.rejected(conflicts);
		}
		scheduleExceptionRepository.delete(target);
		recordAudit(storeId, actor, "STORE_SCHEDULE_EXCEPTION_DELETED", "SCHEDULE_EXCEPTION", exceptionId,
			target.toString(), null);
		return UpdateResult.success();
	}

	private StoreMember authorizeManager(UUID userId, UUID storeId) {
		lockSettings(storeId);
		return authorizationService.requireManager(userId, storeId);
	}

	private StoreMember authorizeOwner(UUID userId, UUID storeId) {
		lockSettings(storeId);
		return authorizationService.requireOwner(userId, storeId);
	}

	private void validateOwnerChange(StoreMember actor, StoreMember target, StoreMemberRole nextRole,
		StoreMemberStatus nextStatus) {
		if (target.getRole() != StoreMemberRole.OWNER) {
			return;
		}
		if (!actor.getId().equals(target.getId())) {
			throw new BusinessException(ErrorCode.OWNER_CHANGE_NOT_ALLOWED);
		}
		if ((nextRole != StoreMemberRole.OWNER || nextStatus != StoreMemberStatus.ACTIVE)
			&& storeMemberRepository.countByStoreIdAndRoleAndStatus(target.getStoreId(), StoreMemberRole.OWNER,
				StoreMemberStatus.ACTIVE) <= 1) {
			throw new BusinessException(ErrorCode.LAST_ACTIVE_OWNER_REQUIRED);
		}
	}

	private List<ReservationConflict> conflictsForStaff(UUID storeId, UUID staffId) {
		Store store = requireStore(storeId);
		return futureReservations(storeId).stream()
			.filter(reservation -> staffId.equals(reservation.getAssignedStaffId()))
			.map(reservation -> conflict(reservation, store)).toList();
	}

	private List<ReservationConflict> staffScheduleConflicts(UUID storeId, UUID staffId,
		List<StaffSchedule> schedules, List<StaffScheduleException> exceptions) {
		Store store = requireStore(storeId);
		ZoneId zoneId = ZoneId.of(store.getTimezone());
		return futureReservations(storeId).stream()
			.filter(reservation -> staffId.equals(reservation.getAssignedStaffId()))
			.filter(reservation -> !fitsStaffSchedule(reservation, zoneId, schedules, exceptions))
			.map(reservation -> conflict(reservation, store)).toList();
	}

	private List<ReservationConflict> storeScheduleConflicts(UUID storeId, List<BusinessHour> hours,
		List<ScheduleException> exceptions) {
		Store store = requireStore(storeId);
		ZoneId zoneId = ZoneId.of(store.getTimezone());
		return futureReservations(storeId).stream()
			.filter(reservation -> !fitsStoreSchedule(reservation, zoneId, hours, exceptions))
			.map(reservation -> conflict(reservation, store)).toList();
	}

	private boolean fitsStaffSchedule(Reservation reservation, ZoneId zoneId, List<StaffSchedule> schedules,
		List<StaffScheduleException> exceptions) {
		LocalDate date = reservation.getStartAt().atZone(zoneId).toLocalDate();
		LocalDateTime start = reservation.getStartAt().atZone(zoneId).toLocalDateTime();
		LocalDateTime end = reservation.getOccupiedUntil().atZone(zoneId).toLocalDateTime();
		return end.toLocalDate().equals(date) && resolveStaffRanges(date, schedules,
			exceptions.stream().filter(value -> value.getTargetDate().equals(date)).toList()).stream()
			.anyMatch(range -> range.contains(start, end));
	}

	private boolean fitsStoreSchedule(Reservation reservation, ZoneId zoneId, List<BusinessHour> hours,
		List<ScheduleException> exceptions) {
		LocalDate date = reservation.getStartAt().atZone(zoneId).toLocalDate();
		LocalDateTime start = reservation.getStartAt().atZone(zoneId).toLocalDateTime();
		LocalDateTime end = reservation.getOccupiedUntil().atZone(zoneId).toLocalDateTime();
		return end.toLocalDate().equals(date) && resolveStoreRanges(date,
			hours.stream().filter(value -> value.getDayOfWeek().name().equals(date.getDayOfWeek().name())).toList(),
			exceptions.stream().filter(value -> value.getTargetDate().equals(date)).toList()).stream()
			.anyMatch(range -> range.contains(start, end));
	}

	private List<Reservation> futureReservations(UUID storeId) {
		Instant now = clock.instant();
		return reservationRepository.findFutureActiveReservations(storeId, ACTIVE_RESERVATION_STATUSES,
			ReservationStatus.HELD, now);
	}

	private ReservationConflict conflict(Reservation reservation, Store store) {
		return new ReservationConflict(reservation.getId(),
			OffsetDateTime.ofInstant(reservation.getStartAt(), ZoneId.of(store.getTimezone())));
	}

	private void validateSchedules(List<ScheduleCommand> commands) {
		Set<DayOfWeek> days = new HashSet<>();
		for (ScheduleCommand command : commands) {
			if (!days.add(command.dayOfWeek()) || command.validFrom() == null
				|| command.validUntil() != null && command.validFrom().isAfter(command.validUntil())) {
				throw invalidSetting("직원 반복 일정의 요일 또는 적용 기간이 올바르지 않습니다.");
			}
			validatePeriods(command.periods());
		}
	}

	private void validateBusinessHours(List<BusinessDayCommand> commands) {
		Set<DayOfWeek> days = new HashSet<>();
		for (BusinessDayCommand command : commands) {
			if (!days.add(command.dayOfWeek()) || command.closed() != command.periods().isEmpty()) {
				throw invalidSetting("영업일은 중복될 수 없고 휴무 여부와 시간 구간이 일치해야 합니다.");
			}
			if (!command.closed()) {
				validatePeriods(command.periods());
			}
		}
	}

	private void validatePeriods(List<TimePeriod> periods) {
		if (periods.isEmpty()) {
			throw invalidSetting("시간 구간이 필요합니다.");
		}
		List<TimePeriod> sorted = periods.stream().sorted(Comparator.comparing(TimePeriod::startTime)).toList();
		for (int index = 0; index < sorted.size(); index++) {
			TimePeriod period = sorted.get(index);
			if (period.startTime() == null || period.endTime() == null || !period.startTime().isBefore(period.endTime())
				|| index > 0 && sorted.get(index - 1).endTime().isAfter(period.startTime())) {
				throw invalidSetting("시간 구간이 올바르지 않거나 서로 겹칩니다.");
			}
		}
	}

	private void validateStoreException(StoreExceptionCommand command) {
		boolean timed = command.type() != ScheduleExceptionType.CLOSED_ALL_DAY;
		validateExceptionTimes(timed, command.startTime(), command.endTime());
	}

	private void validateStaffException(StaffExceptionCommand command) {
		boolean timed = command.type() != StaffScheduleExceptionType.DAY_OFF;
		validateExceptionTimes(timed, command.startTime(), command.endTime());
	}

	private void validateExceptionTimes(boolean timed, LocalTime startTime, LocalTime endTime) {
		if (timed != (startTime != null && endTime != null)
			|| timed && !startTime.isBefore(endTime)) {
			throw invalidSetting("일정 예외 유형과 시간 구간이 일치하지 않습니다.");
		}
	}

	private BusinessException invalidSetting(String message) {
		return new BusinessException(ErrorCode.INVALID_STORE_SETTING, message);
	}

	private Store requireStore(UUID storeId) {
		return storeRepository.findById(storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
	}

	private ServiceOffering requireService(UUID storeId, UUID serviceId) {
		return serviceRepository.findByIdAndStoreId(serviceId, storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_NOT_FOUND));
	}

	private StoreMember requireStaff(UUID storeId, UUID staffId) {
		return storeMemberRepository.findByIdAndStoreId(staffId, storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));
	}

	private StoreQueryService.ServiceSummary serviceSummary(ServiceOffering service) {
		long count = staffServiceRepository.findAllByServiceIdInAndActiveTrue(List.of(service.getId())).size();
		return StoreQueryService.ServiceSummary.from(service, count);
	}

	private void lockSettings(UUID storeId) {
		lock("store-settings:" + storeId);
	}

	private void lockAllStaff(UUID storeId) {
		storeMemberRepository.findAllByStoreIdOrderByCreatedAtAsc(storeId).stream()
			.map(StoreMember::getId).sorted().forEach(this::lockStaff);
	}

	private void lockStaff(UUID staffId) {
		lock("reservation:staff:" + staffId);
	}

	private void lock(String key) {
		entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:lockKey))")
			.setParameter("lockKey", key).getSingleResult();
	}

	private void recordAudit(UUID storeId, StoreMember actor, String action, String targetType, UUID targetId,
		String previous, String changed) {
		auditLogRepository.save(new AuditLog(storeId, AuditActorType.STORE_MEMBER, actor.getId(), action, targetType,
			targetId, null, previous, changed, null, clock.instant()));
	}

	public record StoreCommand(String name, String description, String phoneNumber, String address) { }
	public record ServiceCommand(String name, String description, int durationMinutes, int cleanupMinutes, int capacity) { }
	public record PolicyCommand(int bookingOpenDays, int minimumBookingNoticeMinutes,
		int cancellationDeadlineMinutes, int checkInOpenBeforeMinutes, int lateToleranceMinutes,
		int noShowAfterMinutes, int reservationHoldMinutes, int slotOfferExpirationMinutes,
		int walkInCallTimeoutMinutes, boolean waitlistEnabled, boolean walkInEnabled,
		boolean autoNoShowEnabled) { }
	public record StaffAddCommand(String email, StoreMemberRole role, String displayName) { }
	public record StaffUpdateCommand(String displayName, StoreMemberRole role, boolean bookingEnabled,
		StoreMemberStatus status) { }
	public record StaffServiceCommand(UUID serviceId, boolean active, Integer customDurationMinutes) { }
	public record TimePeriod(LocalTime startTime, LocalTime endTime) { }
	public record ScheduleCommand(DayOfWeek dayOfWeek, List<TimePeriod> periods, LocalDate validFrom,
		LocalDate validUntil) { }
	public record BusinessDayCommand(DayOfWeek dayOfWeek, boolean closed, List<TimePeriod> periods) { }
	public record StoreExceptionCommand(LocalDate targetDate, ScheduleExceptionType type, LocalTime startTime,
		LocalTime endTime, String reason) { }
	public record StaffExceptionCommand(LocalDate targetDate, StaffScheduleExceptionType type, LocalTime startTime,
		LocalTime endTime, String reason) { }
	public record ReservationConflict(UUID reservationId, OffsetDateTime startAt) { }
	public record UpdateResult(boolean updated, List<ReservationConflict> conflicts) {
		static UpdateResult success() {
			return new UpdateResult(true, List.of());
		}

		static UpdateResult rejected(List<ReservationConflict> conflicts) {
			return new UpdateResult(false, conflicts);
		}
	}
}
