package com.example.jariyo_backend.domain.admin.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.domain.admin.entity.AuditActorType;
import com.example.jariyo_backend.domain.admin.entity.AuditLog;
import com.example.jariyo_backend.domain.admin.repository.AuditLogRepository;
import com.example.jariyo_backend.domain.reservation.entity.Reservation;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.repository.ReservationRepository;
import com.example.jariyo_backend.domain.store.entity.ServiceOffering;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.entity.StorePolicy;
import com.example.jariyo_backend.domain.store.repository.ServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StorePolicyRepository;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.user.entity.CustomerProfile;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.entity.StoreMemberRole;
import com.example.jariyo_backend.domain.user.repository.CustomerProfileRepository;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import com.example.jariyo_backend.domain.user.service.StoreAuthorizationService;
import com.example.jariyo_backend.domain.waitlist.entity.SlotOffer;
import com.example.jariyo_backend.domain.waitlist.entity.SlotOfferStatus;
import com.example.jariyo_backend.domain.waitlist.entity.WaitlistEntry;
import com.example.jariyo_backend.domain.waitlist.entity.WaitlistStatus;
import com.example.jariyo_backend.domain.waitlist.repository.SlotOfferRepository;
import com.example.jariyo_backend.domain.waitlist.repository.WaitlistEntryRepository;
import com.example.jariyo_backend.domain.walkin.entity.WalkInEntry;
import com.example.jariyo_backend.domain.walkin.entity.WalkInStatus;
import com.example.jariyo_backend.domain.walkin.repository.WalkInEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOperationQueryService {
	private static final EnumSet<ReservationStatus> RESERVATION_DASHBOARD_STATUSES = EnumSet.of(
		ReservationStatus.HELD, ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN,
		ReservationStatus.IN_SERVICE, ReservationStatus.COMPLETED, ReservationStatus.NO_SHOW);
	private static final EnumSet<WalkInStatus> WAITING_WALK_IN_STATUSES = EnumSet.of(
		WalkInStatus.WAITING, WalkInStatus.CALLED, WalkInStatus.SKIPPED);

	private final StoreAuthorizationService storeAuthorizationService;
	private final StoreRepository storeRepository;
	private final StorePolicyRepository storePolicyRepository;
	private final ReservationRepository reservationRepository;
	private final WaitlistEntryRepository waitlistEntryRepository;
	private final SlotOfferRepository slotOfferRepository;
	private final WalkInEntryRepository walkInEntryRepository;
	private final AuditLogRepository auditLogRepository;
	private final CustomerProfileRepository customerProfileRepository;
	private final ServiceRepository serviceRepository;
	private final StoreMemberRepository storeMemberRepository;
	private final Clock clock;

	public AdminOperationQueryService(StoreAuthorizationService storeAuthorizationService, StoreRepository storeRepository,
		StorePolicyRepository storePolicyRepository, ReservationRepository reservationRepository,
		WaitlistEntryRepository waitlistEntryRepository, SlotOfferRepository slotOfferRepository,
		WalkInEntryRepository walkInEntryRepository, AuditLogRepository auditLogRepository,
		CustomerProfileRepository customerProfileRepository,
		ServiceRepository serviceRepository, StoreMemberRepository storeMemberRepository, Clock clock) {
		this.storeAuthorizationService = storeAuthorizationService;
		this.storeRepository = storeRepository;
		this.storePolicyRepository = storePolicyRepository;
		this.reservationRepository = reservationRepository;
		this.waitlistEntryRepository = waitlistEntryRepository;
		this.slotOfferRepository = slotOfferRepository;
		this.walkInEntryRepository = walkInEntryRepository;
		this.auditLogRepository = auditLogRepository;
		this.customerProfileRepository = customerProfileRepository;
		this.serviceRepository = serviceRepository;
		this.storeMemberRepository = storeMemberRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public TodayDashboard getTodayDashboard(UUID userId, UUID storeId) {
		storeAuthorizationService.requireRole(userId, storeId, StoreMemberRole.STAFF);
		Store store = requireStore(storeId);
		StorePolicy policy = requirePolicy(storeId);
		ZoneId zoneId = ZoneId.of(store.getTimezone());
		LocalDate today = LocalDate.now(clock.withZone(zoneId));
		Instant rangeStart = today.atStartOfDay(zoneId).toInstant();
		Instant rangeEnd = today.plusDays(1).atStartOfDay(zoneId).toInstant();
		Instant now = clock.instant();
		List<Reservation> reservations = reservationRepository.findDailyReservations(storeId, rangeStart, rangeEnd);
		List<WalkInEntry> walkIns = walkInEntryRepository.findAllByStoreIdAndOperationDateOrderByQueueNumberAsc(storeId, today);
		List<SlotOffer> pendingOffers = slotOfferRepository.findActiveByStoreIdAndStatus(storeId, SlotOfferStatus.PENDING, now);

		long reservationCount = reservations.stream()
			.filter(reservation -> RESERVATION_DASHBOARD_STATUSES.contains(reservation.getStatus()))
			.count();
		long waitingWalkInCount = walkIns.stream()
			.filter(walkIn -> WAITING_WALK_IN_STATUSES.contains(walkIn.getStatus()))
			.count();
		long checkedInCount = reservations.stream().filter(reservation -> reservation.getStatus() == ReservationStatus.CHECKED_IN).count()
			+ walkIns.stream().filter(walkIn -> walkIn.getStatus() == WalkInStatus.CHECKED_IN).count();
		long inServiceCount = reservations.stream().filter(reservation -> reservation.getStatus() == ReservationStatus.IN_SERVICE).count()
			+ walkIns.stream().filter(walkIn -> walkIn.getStatus() == WalkInStatus.IN_SERVICE).count();
		List<Reservation> noShowCandidates = reservations.stream()
			.filter(reservation -> reservation.getStatus() == ReservationStatus.CONFIRMED)
			.filter(reservation -> !reservation.getStartAt().isAfter(now.minusSeconds(policy.getNoShowAfterMinutes() * 60L)))
			.sorted(Comparator.comparing(Reservation::getStartAt))
			.toList();

		return new TodayDashboard(today, new TodaySummary(reservationCount, waitingWalkInCount, checkedInCount,
			inServiceCount, noShowCandidates.size(), pendingOffers.size()),
			buildAlerts(zoneId, noShowCandidates, pendingOffers), List.of());
	}

	@Transactional(readOnly = true)
	public List<AdminReservationItem> listReservations(UUID userId, UUID storeId, LocalDate from, LocalDate to,
		UUID staffId, UUID serviceId, ReservationStatus status, String customerQuery) {
		storeAuthorizationService.requireRole(userId, storeId, StoreMemberRole.STAFF);
		Store store = requireStore(storeId);
		LocalDate targetFrom = from == null ? LocalDate.now(clock.withZone(ZoneId.of(store.getTimezone()))) : from;
		LocalDate targetTo = to == null ? targetFrom : to;
		if (targetTo.isBefore(targetFrom)) {
			throw new BusinessException(ErrorCode.INVALID_AVAILABILITY_RANGE);
		}
		Instant rangeStart = targetFrom.atStartOfDay(ZoneId.of(store.getTimezone())).toInstant();
		Instant rangeEnd = targetTo.plusDays(1).atStartOfDay(ZoneId.of(store.getTimezone())).toInstant();
		List<Reservation> reservations = reservationRepository.findDailyReservations(storeId, rangeStart, rangeEnd);
		Map<UUID, String> customerNames = customerNamesByProfileId(reservations.stream()
			.map(Reservation::getCustomerId)
			.collect(Collectors.toSet()));
		Map<UUID, String> serviceNames = serviceNames(reservations.stream()
			.map(Reservation::getServiceId)
			.collect(Collectors.toSet()));
		Map<UUID, String> staffNames = storeMemberNames(reservations.stream()
			.map(Reservation::getAssignedStaffId)
			.filter(id -> id != null)
			.collect(Collectors.toSet()));
		String normalizedCustomerQuery = normalize(customerQuery);
		return reservations.stream()
			.filter(reservation -> staffId == null || staffId.equals(reservation.getAssignedStaffId()))
			.filter(reservation -> serviceId == null || serviceId.equals(reservation.getServiceId()))
			.filter(reservation -> status == null || status == reservation.getStatus())
			.map(reservation -> toReservationItem(reservation, customerNames, serviceNames, staffNames))
			.filter(item -> normalizedCustomerQuery == null || item.customerName().toLowerCase().contains(normalizedCustomerQuery))
			.toList();
	}

	@Transactional(readOnly = true)
	public AdminReservationDetail getReservation(UUID userId, UUID storeId, UUID reservationId) {
		storeAuthorizationService.requireRole(userId, storeId, StoreMemberRole.STAFF);
		Reservation reservation = reservationRepository.findById(reservationId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
		if (!storeId.equals(reservation.getStoreId())) {
			throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
		}
		String customerName = customerNamesByProfileId(Set.of(reservation.getCustomerId()))
			.getOrDefault(reservation.getCustomerId(), "-");
		String serviceName = serviceNames(Set.of(reservation.getServiceId()))
			.getOrDefault(reservation.getServiceId(), "-");
		String staffName = reservation.getAssignedStaffId() == null ? null
			: storeMemberNames(Set.of(reservation.getAssignedStaffId())).get(reservation.getAssignedStaffId());
		return new AdminReservationDetail(reservation.getId(), customerName, serviceName, staffName,
			reservation.getStartAt(), reservation.getServiceEndAt(), reservation.getStatus(),
			toCheckInStatus(reservation.getStatus()), reservation.getPartySize(), reservation.getCustomerNote(),
			reservation.getCancellationReason(), reservation.getCancelledAt(), reservation.getCheckedInAt(),
			reservation.getServiceStartedAt(), reservation.getCompletedAt());
	}

	@Transactional(readOnly = true)
	public List<AdminWaitlistItem> listWaitlists(UUID userId, UUID storeId, LocalDate date, UUID serviceId, UUID staffId,
		WaitlistStatus status) {
		storeAuthorizationService.requireRole(userId, storeId, StoreMemberRole.STAFF);
		Store store = requireStore(storeId);
		LocalDate targetDate = date == null ? LocalDate.now(clock.withZone(ZoneId.of(store.getTimezone()))) : date;
		List<WaitlistEntry> waitlists = waitlistEntryRepository.findAllByStoreIdAndDesiredDateOrderBySequenceNumberAscCreatedAtAsc(
			storeId, targetDate);
		Map<UUID, String> customerNames = customerNamesByProfileId(waitlists.stream()
			.map(WaitlistEntry::getCustomerId)
			.collect(Collectors.toSet()));
		Map<UUID, String> serviceNames = serviceNames(waitlists.stream()
			.map(WaitlistEntry::getServiceId)
			.collect(Collectors.toSet()));
		Map<UUID, String> staffNames = storeMemberNames(waitlists.stream()
			.map(WaitlistEntry::getPreferredStaffId)
			.filter(id -> id != null)
			.collect(Collectors.toSet()));
		Map<UUID, SlotOffer> latestPendingOffers = slotOfferRepository.findActiveByStoreIdAndStatus(storeId, SlotOfferStatus.PENDING,
			clock.instant()).stream()
			.collect(Collectors.toMap(SlotOffer::getWaitlistEntryId, Function.identity(), (left, right) -> left));
		return waitlists.stream()
			.filter(entry -> serviceId == null || serviceId.equals(entry.getServiceId()))
			.filter(entry -> staffId == null || staffId.equals(entry.getPreferredStaffId()))
			.filter(entry -> status == null || status == entry.getStatus())
			.map(entry -> new AdminWaitlistItem(entry.getId(), customerNames.getOrDefault(entry.getCustomerId(), "-"),
				serviceNames.getOrDefault(entry.getServiceId(), "-"),
				entry.getPreferredStaffId() == null ? null : staffNames.get(entry.getPreferredStaffId()),
				entry.getDesiredDate(), entry.getAcceptableStartTime(), entry.getAcceptableEndTime(), entry.getStatus(),
				entry.getSequenceNumber(), latestPendingOffers.containsKey(entry.getId()),
				latestPendingOffers.containsKey(entry.getId()) ? latestPendingOffers.get(entry.getId()).getExpiresAt() : null))
			.toList();
	}

	@Transactional(readOnly = true)
	public AdminWaitlistDetail getWaitlist(UUID userId, UUID storeId, UUID waitlistId) {
		storeAuthorizationService.requireRole(userId, storeId, StoreMemberRole.STAFF);
		WaitlistEntry entry = waitlistEntryRepository.findById(waitlistId)
			.orElseThrow(() -> new BusinessException(ErrorCode.WAITLIST_NOT_FOUND));
		if (!storeId.equals(entry.getStoreId())) {
			throw new BusinessException(ErrorCode.WAITLIST_NOT_FOUND);
		}
		String customerName = customerNamesByProfileId(Set.of(entry.getCustomerId()))
			.getOrDefault(entry.getCustomerId(), "-");
		String serviceName = serviceNames(Set.of(entry.getServiceId()))
			.getOrDefault(entry.getServiceId(), "-");
		String staffName = entry.getPreferredStaffId() == null ? null
			: storeMemberNames(Set.of(entry.getPreferredStaffId())).get(entry.getPreferredStaffId());
		SlotOffer pendingOffer = slotOfferRepository.findFirstByWaitlistEntryIdAndStatusOrderByCreatedAtDesc(waitlistId,
			SlotOfferStatus.PENDING).filter(offer -> !offer.getExpiresAt().isBefore(clock.instant())).orElse(null);
		return new AdminWaitlistDetail(entry.getId(), customerName, serviceName, staffName,
			entry.getStaffPreferenceType(), entry.getDesiredDate(), entry.getAcceptableStartTime(),
			entry.getAcceptableEndTime(), entry.getPartySize(), entry.getStatus(), entry.getSequenceNumber(),
			entry.getExpiresAt(), entry.getReservedAt(), entry.getCancelledAt(),
			pendingOffer == null ? null : new PendingSlotOfferSummary(pendingOffer.getId(), pendingOffer.getStartAt(),
				pendingOffer.getServiceEndAt(), pendingOffer.getExpiresAt()));
	}

	@Transactional(readOnly = true)
	public AuditLogListResult listAuditLogs(UUID userId, UUID storeId, UUID actorId, String action, String targetType,
		UUID targetId, Instant from, Instant to, String cursor, Integer limit) {
		storeAuthorizationService.requireManager(userId, storeId);
		int max = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
		List<AuditLog> logs = auditLogRepository.findAllByStoreIdAndFilters(storeId, actorId, normalizeExact(action),
			normalizeExact(targetType), targetId, from, to);
		int startIndex = resolveCursorIndex(logs, cursor);
		List<AuditLog> page = logs.stream()
			.skip(startIndex)
			.limit(max)
			.toList();
		boolean hasNext = startIndex + page.size() < logs.size();
		String nextCursor = hasNext && !page.isEmpty() ? page.get(page.size() - 1).getId().toString() : null;
		Map<UUID, String> customerNames = customerNamesByProfileId(page.stream()
			.filter(log -> log.getActorType() == AuditActorType.CUSTOMER && log.getActorId() != null)
			.map(AuditLog::getActorId)
			.collect(Collectors.toSet()));
		Map<UUID, String> memberNames = storeMemberNamesById(storeId, page.stream()
			.filter(log -> log.getActorType() == AuditActorType.STORE_MEMBER && log.getActorId() != null)
			.map(AuditLog::getActorId)
			.collect(Collectors.toSet()));
		List<AuditLogItem> items = page.stream()
			.map(log -> new AuditLogItem(log.getId(),
				new AuditActor(log.getActorType(), log.getActorId(), resolveActorDisplayName(log, customerNames, memberNames)),
				log.getAction(), log.getTargetType(), log.getTargetId(), log.getReason(), log.getOccurredAt()))
			.toList();
		return new AuditLogListResult(items, new ApiResponse.PageBody(nextCursor, hasNext));
	}

	private List<DashboardAlert> buildAlerts(ZoneId zoneId, List<Reservation> noShowCandidates, List<SlotOffer> pendingOffers) {
		List<DashboardAlert> noShowAlerts = noShowCandidates.stream()
			.limit(5)
			.map(reservation -> new DashboardAlert("NO_SHOW_CANDIDATE", reservation.getId().toString(),
				String.format("%s 예약이 아직 체크인되지 않았어요.",
					ZonedDateTime.ofInstant(reservation.getStartAt(), zoneId).toLocalTime())))
			.toList();
		List<DashboardAlert> offerAlerts = pendingOffers.stream()
			.limit(Math.max(0, 5 - noShowAlerts.size()))
			.map(offer -> new DashboardAlert("PENDING_SLOT_OFFER", offer.getId().toString(),
				"응답 대기 중인 빈자리 제안이 있어요."))
			.toList();
		return java.util.stream.Stream.concat(noShowAlerts.stream(), offerAlerts.stream()).toList();
	}

	private AdminReservationItem toReservationItem(Reservation reservation, Map<UUID, String> customerNames,
		Map<UUID, String> serviceNames, Map<UUID, String> staffNames) {
		return new AdminReservationItem(reservation.getId(),
			customerNames.getOrDefault(reservation.getCustomerId(), "-"),
			serviceNames.getOrDefault(reservation.getServiceId(), "-"),
			reservation.getAssignedStaffId() == null ? null : staffNames.get(reservation.getAssignedStaffId()),
			reservation.getStartAt(), reservation.getServiceEndAt(), reservation.getStatus(),
			toCheckInStatus(reservation.getStatus()), reservation.getPartySize());
	}

	private String toCheckInStatus(ReservationStatus status) {
		return switch (status) {
			case CHECKED_IN -> "CHECKED_IN";
			case IN_SERVICE -> "IN_SERVICE";
			case COMPLETED -> "COMPLETED";
			case NO_SHOW -> "NO_SHOW";
			case CANCELLED -> "CANCELLED";
			default -> "NOT_CHECKED_IN";
		};
	}

	private Map<UUID, String> customerNamesByProfileId(Set<UUID> profileIds) {
		if (profileIds.isEmpty()) return Map.of();
		return customerProfileRepository.findAllById(profileIds).stream()
			.collect(Collectors.toMap(CustomerProfile::getId, CustomerProfile::getDisplayName));
	}

	private Map<UUID, String> serviceNames(Set<UUID> serviceIds) {
		if (serviceIds.isEmpty()) return Map.of();
		return serviceRepository.findAllById(serviceIds).stream()
			.collect(Collectors.toMap(ServiceOffering::getId, ServiceOffering::getName));
	}

	private Map<UUID, String> storeMemberNames(Set<UUID> memberIds) {
		if (memberIds.isEmpty()) return Map.of();
		return storeMemberRepository.findAllById(memberIds).stream()
			.collect(Collectors.toMap(StoreMember::getId, StoreMember::getDisplayName));
	}

	private Map<UUID, String> storeMemberNamesById(UUID storeId, Set<UUID> memberIds) {
		if (memberIds.isEmpty()) return Map.of();
		return storeMemberRepository.findAllByStoreIdAndIdInOrderByCreatedAtAsc(storeId, memberIds).stream()
			.collect(Collectors.toMap(StoreMember::getId, StoreMember::getDisplayName));
	}

	private String normalize(String value) {
		if (value == null || value.isBlank()) return null;
		return value.trim().toLowerCase();
	}

	private String normalizeExact(String value) {
		if (value == null || value.isBlank()) return null;
		return value.trim();
	}

	private int resolveCursorIndex(List<AuditLog> logs, String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return 0;
		}
		UUID cursorId;
		try {
			cursorId = UUID.fromString(cursor.trim());
		}
		catch (IllegalArgumentException exception) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "cursor 형식이 올바르지 않습니다.");
		}
		for (int index = 0; index < logs.size(); index++) {
			if (cursorId.equals(logs.get(index).getId())) {
				return index + 1;
			}
		}
		return logs.size();
	}

	private String resolveActorDisplayName(AuditLog log, Map<UUID, String> customerNames, Map<UUID, String> memberNames) {
		return switch (log.getActorType()) {
			case CUSTOMER -> log.getActorId() == null ? null : customerNames.getOrDefault(log.getActorId(), "-");
			case STORE_MEMBER -> log.getActorId() == null ? null : memberNames.getOrDefault(log.getActorId(), "-");
			case SYSTEM -> "SYSTEM";
		};
	}

	private Store requireStore(UUID storeId) {
		return storeRepository.findById(storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
	}

	private StorePolicy requirePolicy(UUID storeId) {
		return storePolicyRepository.findByStoreId(storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_POLICY_NOT_FOUND));
	}

	public record TodayDashboard(LocalDate date, TodaySummary summary, List<DashboardAlert> alerts,
		List<DashboardTimelineItem> timeline) { }
	public record TodaySummary(long reservationCount, long waitingWalkInCount, long checkedInCount, long inServiceCount,
		long noShowCandidateCount, long pendingSlotOfferCount) { }
	public record DashboardAlert(String type, String referenceId, String message) { }
	public record DashboardTimelineItem(String type, String referenceId, LocalDateTime scheduledAt, String label) { }
	public record AdminReservationItem(UUID id, String customerName, String serviceName, String assignedStaffName,
		Instant startAt, Instant serviceEndAt, ReservationStatus status, String checkInStatus, int partySize) { }
	public record AdminReservationDetail(UUID id, String customerName, String serviceName, String assignedStaffName,
		Instant startAt, Instant serviceEndAt, ReservationStatus status, String checkInStatus, int partySize,
		String customerNote, String cancellationReason, Instant cancelledAt, Instant checkedInAt,
		Instant serviceStartedAt, Instant completedAt) { }
	public record AdminWaitlistItem(UUID id, String customerName, String serviceName, String preferredStaffName,
		LocalDate desiredDate, LocalTime acceptableStartTime, LocalTime acceptableEndTime, WaitlistStatus status,
		int sequenceNumber, boolean pendingOffer, Instant pendingOfferExpiresAt) { }
	public record PendingSlotOfferSummary(UUID id, Instant startAt, Instant serviceEndAt, Instant expiresAt) { }
	public record AdminWaitlistDetail(UUID id, String customerName, String serviceName, String preferredStaffName,
		com.example.jariyo_backend.domain.waitlist.entity.StaffPreferenceType staffPreferenceType, LocalDate desiredDate,
		LocalTime acceptableStartTime, LocalTime acceptableEndTime, int partySize, WaitlistStatus status,
		int sequenceNumber, Instant expiresAt, Instant reservedAt, Instant cancelledAt,
		PendingSlotOfferSummary pendingOffer) { }
	public record AuditActor(AuditActorType type, UUID id, String displayName) { }
	public record AuditLogItem(UUID id, AuditActor actor, String action, String targetType, UUID targetId, String reason,
		Instant occurredAt) { }
	public record AuditLogListResult(List<AuditLogItem> items, ApiResponse.PageBody page) { }
}
