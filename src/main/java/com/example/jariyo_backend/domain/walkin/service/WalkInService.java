package com.example.jariyo_backend.domain.walkin.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.common.idempotency.PersistentIdempotencyService;
import com.example.jariyo_backend.domain.auth.support.PhoneNumberNormalizer;
import com.example.jariyo_backend.domain.store.entity.BusinessHour;
import com.example.jariyo_backend.domain.store.entity.DayOfWeekValue;
import com.example.jariyo_backend.domain.store.entity.ScheduleException;
import com.example.jariyo_backend.domain.store.entity.ScheduleExceptionType;
import com.example.jariyo_backend.domain.store.entity.ServiceOffering;
import com.example.jariyo_backend.domain.store.entity.ServiceStatus;
import com.example.jariyo_backend.domain.store.entity.StaffService;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.entity.StorePolicy;
import com.example.jariyo_backend.domain.store.entity.StoreStatus;
import com.example.jariyo_backend.domain.store.repository.BusinessHourRepository;
import com.example.jariyo_backend.domain.store.repository.ScheduleExceptionRepository;
import com.example.jariyo_backend.domain.store.repository.ServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StaffServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StorePolicyRepository;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.user.entity.CustomerProfile;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.entity.StoreMemberRole;
import com.example.jariyo_backend.domain.user.entity.StoreMemberStatus;
import com.example.jariyo_backend.domain.user.repository.CustomerProfileRepository;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import com.example.jariyo_backend.domain.user.service.StoreAuthorizationService;
import com.example.jariyo_backend.domain.walkin.entity.CallHistory;
import com.example.jariyo_backend.domain.walkin.entity.CallResponseStatus;
import com.example.jariyo_backend.domain.walkin.entity.CheckIn;
import com.example.jariyo_backend.domain.walkin.entity.CheckInMethod;
import com.example.jariyo_backend.domain.walkin.entity.ServiceSession;
import com.example.jariyo_backend.domain.walkin.entity.ServiceSessionStatus;
import com.example.jariyo_backend.domain.walkin.entity.WalkInActorType;
import com.example.jariyo_backend.domain.walkin.entity.WalkInEntry;
import com.example.jariyo_backend.domain.walkin.entity.WalkInStatus;
import com.example.jariyo_backend.domain.walkin.entity.WalkInStatusHistory;
import com.example.jariyo_backend.domain.walkin.repository.CallHistoryRepository;
import com.example.jariyo_backend.domain.walkin.repository.CheckInRepository;
import com.example.jariyo_backend.domain.walkin.repository.QueueNumberIssuer;
import com.example.jariyo_backend.domain.walkin.repository.ServiceSessionRepository;
import com.example.jariyo_backend.domain.walkin.repository.WalkInEntryRepository;
import com.example.jariyo_backend.domain.walkin.repository.WalkInStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalkInService {
	private static final List<WalkInStatus> QUEUE_STATUSES = List.of(WalkInStatus.WAITING, WalkInStatus.CALLED,
		WalkInStatus.SKIPPED);

	private final StoreRepository storeRepository;
	private final StorePolicyRepository storePolicyRepository;
	private final ServiceRepository serviceRepository;
	private final StaffServiceRepository staffServiceRepository;
	private final StoreMemberRepository storeMemberRepository;
	private final CustomerProfileRepository customerProfileRepository;
	private final BusinessHourRepository businessHourRepository;
	private final ScheduleExceptionRepository scheduleExceptionRepository;
	private final WalkInEntryRepository walkInEntryRepository;
	private final CallHistoryRepository callHistoryRepository;
	private final CheckInRepository checkInRepository;
	private final ServiceSessionRepository serviceSessionRepository;
	private final WalkInStatusHistoryRepository historyRepository;
	private final QueueNumberIssuer queueNumberIssuer;
	private final StoreAuthorizationService storeAuthorizationService;
	private final PersistentIdempotencyService idempotencyService;

	public WalkInService(StoreRepository storeRepository, StorePolicyRepository storePolicyRepository,
		ServiceRepository serviceRepository, StaffServiceRepository staffServiceRepository,
		StoreMemberRepository storeMemberRepository, CustomerProfileRepository customerProfileRepository,
		BusinessHourRepository businessHourRepository, ScheduleExceptionRepository scheduleExceptionRepository,
		WalkInEntryRepository walkInEntryRepository, CallHistoryRepository callHistoryRepository,
		CheckInRepository checkInRepository, ServiceSessionRepository serviceSessionRepository,
		WalkInStatusHistoryRepository historyRepository, QueueNumberIssuer queueNumberIssuer,
		StoreAuthorizationService storeAuthorizationService, PersistentIdempotencyService idempotencyService) {
		this.storeRepository = storeRepository;
		this.storePolicyRepository = storePolicyRepository;
		this.serviceRepository = serviceRepository;
		this.staffServiceRepository = staffServiceRepository;
		this.storeMemberRepository = storeMemberRepository;
		this.customerProfileRepository = customerProfileRepository;
		this.businessHourRepository = businessHourRepository;
		this.scheduleExceptionRepository = scheduleExceptionRepository;
		this.walkInEntryRepository = walkInEntryRepository;
		this.callHistoryRepository = callHistoryRepository;
		this.checkInRepository = checkInRepository;
		this.serviceSessionRepository = serviceSessionRepository;
		this.historyRepository = historyRepository;
		this.queueNumberIssuer = queueNumberIssuer;
		this.storeAuthorizationService = storeAuthorizationService;
		this.idempotencyService = idempotencyService;
	}

	@Transactional(readOnly = true)
	public WalkInAvailability getAvailability(UUID storeId, UUID serviceId, UUID staffId) {
		Store store = requireStore(storeId);
		StorePolicy policy = requirePolicy(storeId);
		ZoneId zone = ZoneId.of(store.getTimezone());
		OffsetDateTime now = OffsetDateTime.now(zone);
		ServiceOffering service = serviceId == null ? null : requireActiveService(storeId, serviceId);
		if (service != null && staffId != null) requireAvailableStaff(storeId, serviceId, staffId);
		BusinessWindow window = businessWindow(storeId, now.toLocalDate(), now.toLocalTime());
		boolean accepting = store.getStatus() == StoreStatus.ACTIVE && policy.isWalkInEnabled() && window.open();
		long waitingCount = queueEntries(storeId, now.toLocalDate(), serviceId, staffId).size();
		int estimate = estimateWait(service, serviceId, staffId, waitingCount);
		return new WalkInAvailability(storeId, policy.isWalkInEnabled(), accepting, waitingCount, estimate,
			window.lastEntryAt() == null ? null : window.lastEntryAt().toString());
	}

	@Transactional
	public WalkInSummary registerCustomer(UUID userId, String key, RegisterCustomerCommand command) {
		return idempotencyService.execute(userId, "walk-in:register-customer", key, command, WalkInSummary.class,
			() -> {
				CustomerProfile customer = requireCustomer(userId);
				return register(command.storeId(), customer.getId(), null, null, command.serviceId(),
					command.preferredStaffId(), command.partySize(), WalkInActorType.CUSTOMER, userId);
			});
	}

	@Transactional
	public WalkInSummary registerGuest(UUID userId, String key, UUID storeId, RegisterGuestCommand command) {
		return idempotencyService.execute(userId, "walk-in:register-guest:" + storeId, key, command,
			WalkInSummary.class, () -> {
				StoreMember member = requireStaff(userId, storeId);
				return register(storeId, null, command.guestName(), PhoneNumberNormalizer.normalize(command.guestPhoneNumber()),
					command.serviceId(), command.preferredStaffId(), command.partySize(), WalkInActorType.STORE_MEMBER,
					member.getId());
			});
	}

	private WalkInSummary register(UUID storeId, UUID customerId, String guestName, String guestPhone, UUID serviceId,
		UUID preferredStaffId, int partySize, WalkInActorType actorType, UUID actorId) {
		if (partySize < 1) throw new BusinessException(ErrorCode.INVALID_PARTY_SIZE);
		Store store = requireStore(storeId);
		StorePolicy policy = requirePolicy(storeId);
		ServiceOffering service = requireActiveService(storeId, serviceId);
		if (preferredStaffId != null) requireAvailableStaff(storeId, serviceId, preferredStaffId);
		ZoneId zone = ZoneId.of(store.getTimezone());
		OffsetDateTime now = OffsetDateTime.now(zone);
		if (store.getStatus() != StoreStatus.ACTIVE || !policy.isWalkInEnabled()) {
			throw new BusinessException(ErrorCode.WALK_IN_NOT_ENABLED);
		}
		if (!businessWindow(storeId, now.toLocalDate(), now.toLocalTime()).open()) {
			throw new BusinessException(ErrorCode.WALK_IN_REGISTRATION_CLOSED);
		}
		long waiting = queueEntries(storeId, now.toLocalDate(), serviceId, preferredStaffId).size();
		int number = queueNumberIssuer.issue(storeId, now.toLocalDate());
		int estimate = estimateWait(service, serviceId, preferredStaffId, waiting);
		WalkInEntry entry = customerId == null
			? WalkInEntry.forGuest(storeId, guestName, guestPhone, serviceId, preferredStaffId, partySize,
				now.toLocalDate(), number, estimate)
			: WalkInEntry.forCustomer(storeId, customerId, serviceId, preferredStaffId, partySize, now.toLocalDate(),
				number, estimate);
		walkInEntryRepository.saveAndFlush(entry);
		historyRepository.save(new WalkInStatusHistory(entry.getId(), null, WalkInStatus.WAITING, actorType, actorId,
			"현장 대기 등록", Instant.now()));
		return summary(entry, waiting);
	}

	@Transactional(readOnly = true)
	public List<WalkInSummary> listMine(UUID userId, WalkInStatus status, LocalDate date) {
		CustomerProfile customer = requireCustomer(userId);
		return walkInEntryRepository.findAllByCustomerIdOrderByCreatedAtDesc(customer.getId()).stream()
			.filter(entry -> status == null || entry.getStatus() == status)
			.filter(entry -> date == null || entry.getOperationDate().equals(date))
			.map(entry -> summary(entry, waitingAhead(entry)))
			.toList();
	}

	@Transactional(readOnly = true)
	public WalkInDetail getMine(UUID userId, UUID walkInId) {
		CustomerProfile customer = requireCustomer(userId);
		WalkInEntry entry = requireEntry(walkInId);
		if (!customer.getId().equals(entry.getCustomerId())) throw new BusinessException(ErrorCode.RESOURCE_NOT_OWNED_BY_USER);
		return detail(entry);
	}

	@Transactional(readOnly = true)
	public List<AdminWalkInSummary> listAdmin(UUID userId, UUID storeId, WalkInStatus status, UUID serviceId,
		UUID staffId, LocalDate date) {
		requireStaff(userId, storeId);
		LocalDate target = date == null ? LocalDate.now(ZoneId.of(requireStore(storeId).getTimezone())) : date;
		return walkInEntryRepository.findAllByStoreIdAndOperationDateOrderByQueueNumberAsc(storeId, target).stream()
			.filter(entry -> status == null || entry.getStatus() == status)
			.filter(entry -> serviceId == null || entry.getServiceId().equals(serviceId))
			.filter(entry -> staffId == null || staffId.equals(entry.getPreferredStaffId()))
			.map(this::adminSummary)
			.toList();
	}

	@Transactional
	public WalkInSummary cancelMine(UUID userId, UUID walkInId, String key, ReasonCommand command) {
		return idempotencyService.execute(userId, "walk-in:cancel:" + walkInId, key, command, WalkInSummary.class,
			() -> {
				CustomerProfile customer = requireCustomer(userId);
				WalkInEntry entry = requireEntryForUpdate(walkInId);
				if (!customer.getId().equals(entry.getCustomerId())) throw new BusinessException(ErrorCode.RESOURCE_NOT_OWNED_BY_USER);
				return transition(entry, WalkInStatus.CANCELLED, WalkInActorType.CUSTOMER, userId, command.reason());
			});
	}

	@Transactional
	public WalkInSummary respondCall(UUID userId, UUID walkInId, String key, CallResponseCommand command) {
		return idempotencyService.execute(userId, "walk-in:respond-call:" + walkInId, key, command,
			WalkInSummary.class, () -> {
				CustomerProfile customer = requireCustomer(userId);
				WalkInEntry entry = requireEntryForUpdate(walkInId);
				if (!customer.getId().equals(entry.getCustomerId())) throw new BusinessException(ErrorCode.RESOURCE_NOT_OWNED_BY_USER);
				if (entry.getStatus() != WalkInStatus.CALLED) throw new BusinessException(ErrorCode.WALK_IN_INVALID_STATE);
				if (entry.getCallExpiresAt().isBefore(Instant.now())) throw new BusinessException(ErrorCode.WALK_IN_CALL_EXPIRED);
				return switch (command.response()) {
					case ENTERING -> checkIn(entry, CheckInMethod.CUSTOMER_APP, null, WalkInActorType.CUSTOMER, userId);
					case DELAYED -> respondAndTransition(entry, CallResponseStatus.MISSED, WalkInStatus.SKIPPED,
						WalkInActorType.CUSTOMER, userId, "고객 지연 응답");
					case CANCEL -> respondAndTransition(entry, CallResponseStatus.CANCELLED, WalkInStatus.CANCELLED,
						WalkInActorType.CUSTOMER, userId, "고객 호출 취소");
				};
			});
	}

	@Transactional
	public WalkInSummary call(UUID userId, UUID storeId, UUID walkInId, String key, CallCommand command,
		boolean recall) {
		String operation = recall ? "walk-in:recall:" : "walk-in:call:";
		return idempotencyService.execute(userId, operation + walkInId, key, command, WalkInSummary.class, () -> {
			StoreMember member = requireStaff(userId, storeId);
			WalkInEntry entry = requireStoreEntryForUpdate(storeId, walkInId);
			if (!recall && entry.getStatus() != WalkInStatus.WAITING) {
				throw new BusinessException(entry.getStatus() == WalkInStatus.CALLED ? ErrorCode.WALK_IN_ALREADY_CALLED : ErrorCode.WALK_IN_INVALID_STATE);
			}
			if (recall && entry.getStatus() != WalkInStatus.CALLED && entry.getStatus() != WalkInStatus.SKIPPED) {
				throw new BusinessException(ErrorCode.WALK_IN_INVALID_STATE);
			}
			callHistoryRepository.findFirstByWalkInEntryIdAndResponseStatus(entry.getId(), CallResponseStatus.WAITING)
				.ifPresent(history -> history.respond(CallResponseStatus.CANCELLED, Instant.now(), "재호출"));
			callHistoryRepository.flush();
			int timeout = command.responseTimeoutMinutes() == null
				? requirePolicy(storeId).getWalkInCallTimeoutMinutes() : command.responseTimeoutMinutes();
			if (timeout < 1 || timeout > 60) throw new BusinessException(ErrorCode.BAD_REQUEST);
			Instant now = Instant.now();
			WalkInStatus previous = entry.getStatus();
			if (previous == WalkInStatus.CALLED) {
				entry.recall(now, now.plusSeconds(timeout * 60L));
			} else {
				entry.call(now, now.plusSeconds(timeout * 60L));
			}
			callHistoryRepository.save(new CallHistory(entry.getId(), (int) callHistoryRepository.countByWalkInEntryId(entry.getId()) + 1,
				member.getId(), now, entry.getCallExpiresAt()));
			if (previous != WalkInStatus.CALLED) {
				history(entry, previous, WalkInStatus.CALLED, WalkInActorType.STORE_MEMBER, member.getId(), recall ? "재호출" : "고객 호출");
			}
			return summary(entry, waitingAhead(entry));
		});
	}

	@Transactional
	public WalkInSummary checkInAdmin(UUID userId, UUID storeId, UUID walkInId, String key) {
		return idempotencyService.execute(userId, "walk-in:check-in:" + walkInId, key, new EmptyCommand(),
			WalkInSummary.class, () -> {
				StoreMember member = requireStaff(userId, storeId);
				WalkInEntry entry = requireStoreEntryForUpdate(storeId, walkInId);
				return checkIn(entry, CheckInMethod.STAFF_MANUAL, member.getId(), WalkInActorType.STORE_MEMBER,
					member.getId());
			});
	}

	@Transactional
	public WalkInSummary skip(UUID userId, UUID storeId, UUID walkInId, String key, ReasonCommand command) {
		return adminTransition(userId, storeId, walkInId, key, command, "skip", WalkInStatus.SKIPPED);
	}

	@Transactional
	public WalkInSummary cancelAdmin(UUID userId, UUID storeId, UUID walkInId, String key, ReasonCommand command) {
		return adminTransition(userId, storeId, walkInId, key, command, "cancel", WalkInStatus.CANCELLED);
	}

	@Transactional
	public WalkInSummary restore(UUID userId, UUID storeId, UUID walkInId, String key) {
		return adminTransition(userId, storeId, walkInId, key, new ReasonCommand("대기열 복귀"), "restore", WalkInStatus.WAITING);
	}

	@Transactional
	public WalkInSummary markNoShow(UUID userId, UUID storeId, UUID walkInId, String key, ReasonCommand command) {
		return idempotencyService.execute(userId, "walk-in:no-show:" + walkInId, key, command, WalkInSummary.class,
			() -> {
				StoreMember member = requireStaff(userId, storeId);
				WalkInEntry entry = requireStoreEntryForUpdate(storeId, walkInId);
				if (entry.getStatus() != WalkInStatus.CALLED || entry.getCallExpiresAt().isAfter(Instant.now())) {
					throw new BusinessException(ErrorCode.WALK_IN_INVALID_STATE);
				}
				return respondAndTransition(entry, CallResponseStatus.MISSED, WalkInStatus.NO_SHOW,
					WalkInActorType.STORE_MEMBER, member.getId(), command.reason());
			});
	}

	private WalkInSummary adminTransition(UUID userId, UUID storeId, UUID walkInId, String key, ReasonCommand command,
		String operation, WalkInStatus target) {
		return idempotencyService.execute(userId, "walk-in:" + operation + ":" + walkInId, key, command,
			WalkInSummary.class, () -> {
				StoreMember member = requireStaff(userId, storeId);
				WalkInEntry entry = requireStoreEntryForUpdate(storeId, walkInId);
				if (target == WalkInStatus.SKIPPED) {
					return respondAndTransition(entry, CallResponseStatus.MISSED, target, WalkInActorType.STORE_MEMBER,
						member.getId(), command.reason());
				}
				return transition(entry, target, WalkInActorType.STORE_MEMBER, member.getId(), command.reason());
			});
	}

	@Transactional
	public StartServiceResult startService(UUID userId, UUID storeId, UUID walkInId, String key,
		StartServiceCommand command) {
		return idempotencyService.execute(userId, "walk-in:start-service:" + walkInId, key, command,
			StartServiceResult.class, () -> {
				StoreMember actor = requireStaff(userId, storeId);
				WalkInEntry entry = requireStoreEntryForUpdate(storeId, walkInId);
				requireAvailableStaff(storeId, entry.getServiceId(), command.staffId());
				if (serviceSessionRepository.findByWalkInEntryIdAndStatus(walkInId, ServiceSessionStatus.IN_PROGRESS).isPresent()) {
					throw new BusinessException(ErrorCode.SERVICE_SESSION_ALREADY_STARTED);
				}
				Instant now = Instant.now();
				WalkInStatus previous = entry.getStatus();
				entry.transitionTo(WalkInStatus.IN_SERVICE, now);
				ServiceSession session = serviceSessionRepository.save(new ServiceSession(storeId, entry.getCustomerId(),
					walkInId, entry.getServiceId(), command.staffId(), now));
				history(entry, previous, WalkInStatus.IN_SERVICE, WalkInActorType.STORE_MEMBER, actor.getId(), "서비스 시작");
				return StartServiceResult.from(session);
			});
	}

	@Transactional
	public CompleteServiceResult completeService(UUID userId, UUID storeId, UUID sessionId, String key,
		CompleteServiceCommand command) {
		return idempotencyService.execute(userId, "walk-in:complete-service:" + sessionId, key, command,
			CompleteServiceResult.class, () -> {
				StoreMember actor = requireStaff(userId, storeId);
				ServiceSession session = serviceSessionRepository.findByIdForUpdate(sessionId)
					.orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_SESSION_NOT_FOUND));
				if (!storeId.equals(session.getStoreId())) throw new BusinessException(ErrorCode.SERVICE_SESSION_NOT_FOUND);
				WalkInEntry entry = requireStoreEntryForUpdate(storeId, session.getWalkInEntryId());
				Instant now = Instant.now();
				session.complete(now, command.completionNote());
				WalkInStatus previous = entry.getStatus();
				entry.transitionTo(WalkInStatus.COMPLETED, now);
				history(entry, previous, WalkInStatus.COMPLETED, WalkInActorType.STORE_MEMBER, actor.getId(), "서비스 완료");
				return CompleteServiceResult.from(session);
			});
	}

	private WalkInSummary checkIn(WalkInEntry entry, CheckInMethod method, UUID memberId, WalkInActorType actorType,
		UUID actorId) {
		if (checkInRepository.findByWalkInEntryIdAndStatus(entry.getId(), com.example.jariyo_backend.domain.walkin.entity.CheckInStatus.VALID).isPresent()) {
			throw new BusinessException(ErrorCode.CHECK_IN_ALREADY_COMPLETED);
		}
		Instant now = Instant.now();
		WalkInStatus previous = entry.getStatus();
		entry.transitionTo(WalkInStatus.CHECKED_IN, now);
		checkInRepository.save(new CheckIn(entry.getStoreId(), entry.getCustomerId(), entry.getId(), method, now, memberId));
		callHistoryRepository.findFirstByWalkInEntryIdAndResponseStatus(entry.getId(), CallResponseStatus.WAITING)
			.ifPresent(history -> history.respond(CallResponseStatus.RESPONDED, now, "체크인"));
		history(entry, previous, WalkInStatus.CHECKED_IN, actorType, actorId, "체크인");
		return summary(entry, waitingAhead(entry));
	}

	private WalkInSummary respondAndTransition(WalkInEntry entry, CallResponseStatus responseStatus, WalkInStatus target,
		WalkInActorType actorType, UUID actorId, String reason) {
		CallHistory call = callHistoryRepository.findFirstByWalkInEntryIdAndResponseStatus(entry.getId(), CallResponseStatus.WAITING)
			.orElseThrow(() -> new BusinessException(ErrorCode.WALK_IN_INVALID_STATE));
		call.respond(responseStatus, Instant.now(), reason);
		return transition(entry, target, actorType, actorId, reason);
	}

	private WalkInSummary transition(WalkInEntry entry, WalkInStatus target, WalkInActorType actorType, UUID actorId,
		String reason) {
		WalkInStatus previous = entry.getStatus();
		if (target == WalkInStatus.CANCELLED && previous == WalkInStatus.CALLED) {
			callHistoryRepository.findFirstByWalkInEntryIdAndResponseStatus(entry.getId(), CallResponseStatus.WAITING)
				.ifPresent(history -> history.respond(CallResponseStatus.CANCELLED, Instant.now(), reason));
		}
		entry.transitionTo(target, Instant.now());
		history(entry, previous, target, actorType, actorId, reason);
		return summary(entry, waitingAhead(entry));
	}

	private void history(WalkInEntry entry, WalkInStatus previous, WalkInStatus next, WalkInActorType actorType,
		UUID actorId, String reason) {
		historyRepository.save(new WalkInStatusHistory(entry.getId(), previous, next, actorType, actorId, reason,
			Instant.now()));
	}

	private int waitingAhead(WalkInEntry entry) {
		return (int) walkInEntryRepository.findAllByStoreIdAndOperationDateOrderByQueueNumberAsc(entry.getStoreId(),
			entry.getOperationDate()).stream()
			.filter(candidate -> QUEUE_STATUSES.contains(candidate.getStatus()))
			.filter(candidate -> candidate.getQueueNumber() < entry.getQueueNumber())
			.count();
	}

	private List<WalkInEntry> queueEntries(UUID storeId, LocalDate date, UUID serviceId, UUID staffId) {
		return walkInEntryRepository.findAllByStoreIdAndOperationDateOrderByQueueNumberAsc(storeId, date).stream()
			.filter(entry -> QUEUE_STATUSES.contains(entry.getStatus()))
			.filter(entry -> serviceId == null || serviceId.equals(entry.getServiceId()))
			.filter(entry -> staffId == null || staffId.equals(entry.getPreferredStaffId()))
			.toList();
	}

	private int estimateWait(ServiceOffering service, UUID serviceId, UUID staffId, long waitingCount) {
		if (service == null) return (int) waitingCount * 30;
		long staffCount = staffId == null
			? staffServiceRepository.findAllByServiceIdAndActiveTrueOrderByStoreMemberIdAsc(serviceId).size() : 1;
		return (int) Math.ceil((double) waitingCount * (service.getDurationMinutes() + service.getCleanupMinutes())
			/ Math.max(staffCount, 1));
	}

	private BusinessWindow businessWindow(UUID storeId, LocalDate date, LocalTime time) {
		List<ScheduleException> exceptions = scheduleExceptionRepository.findAllByStoreIdOrderByTargetDateAscCreatedAtAsc(storeId)
			.stream().filter(exception -> exception.getTargetDate().equals(date)).toList();
		if (exceptions.stream().anyMatch(exception -> exception.getType() == ScheduleExceptionType.CLOSED_ALL_DAY)) {
			return new BusinessWindow(false, null);
		}
		ScheduleException special = exceptions.stream()
			.filter(exception -> exception.getType() == ScheduleExceptionType.SPECIAL_OPENING_HOURS).findFirst().orElse(null);
		LocalTime open;
		LocalTime close;
		if (special != null) {
			open = special.getStartTime();
			close = special.getEndTime();
		} else {
			BusinessHour hours = businessHourRepository.findAllByStoreIdOrderByDayOfWeekAsc(storeId).stream()
				.filter(hour -> hour.getDayOfWeek() == DayOfWeekValue.valueOf(date.getDayOfWeek().name())).findFirst()
				.orElse(null);
			if (hours == null || hours.isClosed()) return new BusinessWindow(false, null);
			open = hours.getOpenTime();
			close = hours.getCloseTime();
		}
		boolean blocked = exceptions.stream().filter(exception -> exception.getType() == ScheduleExceptionType.BLOCKED_PERIOD)
			.anyMatch(exception -> !time.isBefore(exception.getStartTime()) && time.isBefore(exception.getEndTime()));
		return new BusinessWindow(!blocked && open != null && close != null && !time.isBefore(open) && time.isBefore(close), close);
	}

	private Store requireStore(UUID storeId) {
		return storeRepository.findById(storeId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
	}

	private StorePolicy requirePolicy(UUID storeId) {
		return storePolicyRepository.findByStore_Id(storeId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
	}

	private ServiceOffering requireActiveService(UUID storeId, UUID serviceId) {
		ServiceOffering service = serviceRepository.findById(serviceId)
			.orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_NOT_ACTIVE));
		if (!storeId.equals(service.getStoreId()) || service.getStatus() != ServiceStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.SERVICE_NOT_ACTIVE);
		}
		return service;
	}

	private void requireAvailableStaff(UUID storeId, UUID serviceId, UUID staffId) {
		StoreMember member = storeMemberRepository.findById(staffId)
			.orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_NOT_ACTIVE));
		StaffService link = staffServiceRepository.findByStoreMemberIdAndServiceId(staffId, serviceId)
			.orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_NOT_ACTIVE));
		if (!storeId.equals(member.getStoreId()) || member.getStatus() != StoreMemberStatus.ACTIVE || !link.isActive()) {
			throw new BusinessException(ErrorCode.SERVICE_NOT_ACTIVE);
		}
	}

	private StoreMember requireStaff(UUID userId, UUID storeId) {
		return storeAuthorizationService.requireRole(userId, storeId, StoreMemberRole.STAFF);
	}

	private CustomerProfile requireCustomer(UUID userId) {
		return customerProfileRepository.findByUser_Id(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
	}

	private WalkInEntry requireEntry(UUID id) {
		return walkInEntryRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.WALK_IN_NOT_FOUND));
	}

	private WalkInEntry requireEntryForUpdate(UUID id) {
		return walkInEntryRepository.findByIdForUpdate(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.WALK_IN_NOT_FOUND));
	}

	private WalkInEntry requireStoreEntryForUpdate(UUID storeId, UUID id) {
		WalkInEntry entry = requireEntryForUpdate(id);
		if (!storeId.equals(entry.getStoreId())) throw new BusinessException(ErrorCode.WALK_IN_NOT_FOUND);
		return entry;
	}

	private WalkInSummary summary(WalkInEntry entry, long waitingAhead) {
		return new WalkInSummary(entry.getId(), entry.getStoreId(), entry.getServiceId(), entry.getQueueNumber(),
			entry.getStatus(), waitingAhead, entry.getEstimatedWaitMinutes(), entry.getCreatedAt());
	}

	private WalkInDetail detail(WalkInEntry entry) {
		Store store = requireStore(entry.getStoreId());
		ServiceOffering service = serviceRepository.findById(entry.getServiceId())
			.orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_NOT_ACTIVE));
		return new WalkInDetail(entry.getId(), new NamedResource(store.getId(), store.getName()),
			new NamedResource(service.getId(), service.getName()), entry.getQueueNumber(), entry.getStatus(),
			waitingAhead(entry), entry.getEstimatedWaitMinutes(), entry.getCalledAt(), entry.getCallExpiresAt(),
			entry.getCreatedAt());
	}

	private AdminWalkInSummary adminSummary(WalkInEntry entry) {
		String customerName = entry.getCustomerId() == null ? entry.getGuestName()
			: customerProfileRepository.findById(entry.getCustomerId()).map(CustomerProfile::getDisplayName).orElse("-");
		String serviceName = serviceRepository.findById(entry.getServiceId()).map(ServiceOffering::getName).orElse("-");
		String staffName = entry.getPreferredStaffId() == null ? null
			: storeMemberRepository.findById(entry.getPreferredStaffId()).map(StoreMember::getDisplayName).orElse(null);
		long waitingMinutes = entry.getCreatedAt() == null ? 0 : java.time.Duration.between(entry.getCreatedAt(), Instant.now()).toMinutes();
		return new AdminWalkInSummary(entry.getId(), entry.getQueueNumber(), customerName, serviceName, staffName,
			entry.getStatus(), Math.max(waitingMinutes, 0), entry.getEstimatedWaitMinutes());
	}

	private record BusinessWindow(boolean open, LocalTime lastEntryAt) { }
	public record RegisterCustomerCommand(UUID storeId, UUID serviceId, UUID preferredStaffId, int partySize) { }
	public record RegisterGuestCommand(String guestName, String guestPhoneNumber, UUID serviceId, UUID preferredStaffId,
		int partySize) { }
	public record ReasonCommand(String reason) { }
	public record CallCommand(Integer responseTimeoutMinutes) { }
	public record CallResponseCommand(CallResponse response) { }
	public record StartServiceCommand(UUID staffId) { }
	public record CompleteServiceCommand(String completionNote) { }
	public record EmptyCommand() { }
	public enum CallResponse { ENTERING, DELAYED, CANCEL }
	public record WalkInAvailability(UUID storeId, boolean walkInEnabled, boolean acceptingEntries, long waitingCount,
		int estimatedWaitMinutes, String lastEntryAt) { }
	public record WalkInSummary(UUID id, UUID storeId, UUID serviceId, int queueNumber, WalkInStatus status,
		long waitingAhead, int estimatedWaitMinutes, Instant createdAt) { }
	public record NamedResource(UUID id, String name) { }
	public record WalkInDetail(UUID id, NamedResource store, NamedResource service, int queueNumber, WalkInStatus status,
		long waitingAhead, int estimatedWaitMinutes, Instant calledAt, Instant callExpiresAt, Instant createdAt) { }
	public record AdminWalkInSummary(UUID id, int queueNumber, String customerName, String serviceName,
		String preferredStaffName, WalkInStatus status, long waitingMinutes, int estimatedWaitMinutes) { }
	public record StartServiceResult(UUID serviceSessionId, UUID walkInId, ServiceSessionStatus status,
		Instant actualStartAt) {
		static StartServiceResult from(ServiceSession session) {
			return new StartServiceResult(session.getId(), session.getWalkInEntryId(), session.getStatus(),
				session.getActualStartAt());
		}
	}
	public record CompleteServiceResult(UUID id, ServiceSessionStatus status, Instant actualStartAt,
		Instant actualEndAt, long actualDurationMinutes) {
		static CompleteServiceResult from(ServiceSession session) {
			return new CompleteServiceResult(session.getId(), session.getStatus(), session.getActualStartAt(),
				session.getActualEndAt(), session.getActualDurationMinutes());
		}
	}
}
