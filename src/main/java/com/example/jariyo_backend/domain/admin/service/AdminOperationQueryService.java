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
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
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
	private final CustomerProfileRepository customerProfileRepository;
	private final ServiceRepository serviceRepository;
	private final StoreMemberRepository storeMemberRepository;
	private final Clock clock;

	public AdminOperationQueryService(StoreAuthorizationService storeAuthorizationService, StoreRepository storeRepository,
		StorePolicyRepository storePolicyRepository, ReservationRepository reservationRepository,
		WaitlistEntryRepository waitlistEntryRepository, SlotOfferRepository slotOfferRepository,
		WalkInEntryRepository walkInEntryRepository, CustomerProfileRepository customerProfileRepository,
		ServiceRepository serviceRepository, StoreMemberRepository storeMemberRepository, Clock clock) {
		this.storeAuthorizationService = storeAuthorizationService;
		this.storeRepository = storeRepository;
		this.storePolicyRepository = storePolicyRepository;
		this.reservationRepository = reservationRepository;
		this.waitlistEntryRepository = waitlistEntryRepository;
		this.slotOfferRepository = slotOfferRepository;
		this.walkInEntryRepository = walkInEntryRepository;
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
		Map<UUID, String> customerNames = customerNamesByUserId(reservations.stream()
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

	private Map<UUID, String> customerNamesByUserId(Set<UUID> userIds) {
		if (userIds.isEmpty()) return Map.of();
		return customerProfileRepository.findAllByUser_IdIn(userIds).stream()
			.collect(Collectors.toMap(CustomerProfile::getUserId, CustomerProfile::getDisplayName));
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

	private String normalize(String value) {
		if (value == null || value.isBlank()) return null;
		return value.trim().toLowerCase();
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
	public record AdminWaitlistItem(UUID id, String customerName, String serviceName, String preferredStaffName,
		LocalDate desiredDate, LocalTime acceptableStartTime, LocalTime acceptableEndTime, WaitlistStatus status,
		int sequenceNumber, boolean pendingOffer, Instant pendingOfferExpiresAt) { }
}
