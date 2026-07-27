package com.example.jariyo_backend.domain.admin.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
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
import com.example.jariyo_backend.domain.store.repository.ServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import com.example.jariyo_backend.domain.user.service.StoreAuthorizationService;
import com.example.jariyo_backend.domain.waitlist.entity.SlotOffer;
import com.example.jariyo_backend.domain.waitlist.entity.SlotOfferStatus;
import com.example.jariyo_backend.domain.waitlist.repository.SlotOfferRepository;
import com.example.jariyo_backend.domain.walkin.entity.ServiceSession;
import com.example.jariyo_backend.domain.walkin.entity.ServiceSessionStatus;
import com.example.jariyo_backend.domain.walkin.entity.WalkInEntry;
import com.example.jariyo_backend.domain.walkin.entity.WalkInStatus;
import com.example.jariyo_backend.domain.walkin.repository.ServiceSessionRepository;
import com.example.jariyo_backend.domain.walkin.repository.WalkInEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAnalyticsService {
	private static final EnumSet<ReservationStatus> RESERVATION_FINAL_STATUSES = EnumSet.of(
		ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN, ReservationStatus.IN_SERVICE,
		ReservationStatus.COMPLETED, ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW);
	private static final EnumSet<SlotOfferStatus> FINAL_OFFER_STATUSES = EnumSet.of(
		SlotOfferStatus.ACCEPTED, SlotOfferStatus.DECLINED, SlotOfferStatus.EXPIRED, SlotOfferStatus.REVOKED);
	private static final Set<WalkInStatus> ABANDONED_WALK_IN_STATUSES = Set.of(WalkInStatus.CANCELLED, WalkInStatus.NO_SHOW);
	private static final Set<WalkInStatus> COMPLETED_WALK_IN_STATUSES = Set.of(
		WalkInStatus.CHECKED_IN, WalkInStatus.IN_SERVICE, WalkInStatus.COMPLETED);

	private final StoreAuthorizationService storeAuthorizationService;
	private final StoreRepository storeRepository;
	private final ReservationRepository reservationRepository;
	private final ServiceRepository serviceRepository;
	private final StoreMemberRepository storeMemberRepository;
	private final SlotOfferRepository slotOfferRepository;
	private final ServiceSessionRepository serviceSessionRepository;
	private final WalkInEntryRepository walkInEntryRepository;

	public AdminAnalyticsService(StoreAuthorizationService storeAuthorizationService, StoreRepository storeRepository,
		ReservationRepository reservationRepository, ServiceRepository serviceRepository,
		StoreMemberRepository storeMemberRepository, SlotOfferRepository slotOfferRepository,
		ServiceSessionRepository serviceSessionRepository, WalkInEntryRepository walkInEntryRepository) {
		this.storeAuthorizationService = storeAuthorizationService;
		this.storeRepository = storeRepository;
		this.reservationRepository = reservationRepository;
		this.serviceRepository = serviceRepository;
		this.storeMemberRepository = storeMemberRepository;
		this.slotOfferRepository = slotOfferRepository;
		this.serviceSessionRepository = serviceSessionRepository;
		this.walkInEntryRepository = walkInEntryRepository;
	}

	@Transactional(readOnly = true)
	public AnalyticsSummary getSummary(UUID userId, UUID storeId, LocalDate from, LocalDate to) {
		storeAuthorizationService.requireManager(userId, storeId);
		Store store = requireStore(storeId);
		Range range = range(store, from, to);
		List<Reservation> reservations = reservationRepository.findAllByStoreIdAndStartAtBetween(storeId, range.from(),
			range.toExclusive());
		List<SlotOffer> slotOffers = slotOfferRepository.findAllByStoreIdAndCreatedAtBetween(storeId, range.from(),
			range.toExclusive());
		List<WalkInEntry> walkIns = walkInEntryRepository.findAllByStoreIdAndCreatedAtBetween(storeId, range.from(),
			range.toExclusive());
		long reservationBaseCount = reservations.stream()
			.filter(reservation -> RESERVATION_FINAL_STATUSES.contains(reservation.getStatus()))
			.count();
		long completedCount = reservations.stream().filter(reservation -> reservation.getStatus() == ReservationStatus.COMPLETED)
			.count();
		long cancelledCount = reservations.stream().filter(reservation -> reservation.getStatus() == ReservationStatus.CANCELLED)
			.count();
		long noShowCount = reservations.stream().filter(reservation -> reservation.getStatus() == ReservationStatus.NO_SHOW)
			.count();
		long finalOfferCount = slotOffers.stream().filter(offer -> FINAL_OFFER_STATUSES.contains(offer.getStatus())).count();
		long acceptedOfferCount = slotOffers.stream().filter(offer -> offer.getStatus() == SlotOfferStatus.ACCEPTED).count();
		long cancelledReservationCount = reservations.stream()
			.filter(reservation -> reservation.getStatus() == ReservationStatus.CANCELLED)
			.filter(reservation -> reservation.getAssignedStaffId() != null)
			.count();
		long refilledCancelledReservationCount = slotOffers.stream()
			.filter(offer -> offer.getStatus() == SlotOfferStatus.ACCEPTED)
			.map(SlotOffer::getSourceReservationId)
			.filter(java.util.Objects::nonNull)
			.distinct()
			.count();
		long walkInCount = walkIns.size();
		double averageWalkInWaitMinutes = walkIns.stream()
			.filter(entry -> COMPLETED_WALK_IN_STATUSES.contains(entry.getStatus()))
			.map(this::waitDurationMinutes)
			.filter(duration -> duration >= 0)
			.mapToLong(Long::longValue)
			.average()
			.orElse(0);
		long abandonedWalkInCount = walkIns.stream().filter(entry -> ABANDONED_WALK_IN_STATUSES.contains(entry.getStatus()))
			.count();
		return new AnalyticsSummary(
			reservationBaseCount,
			rate(completedCount, reservationBaseCount),
			rate(cancelledCount, reservationBaseCount),
			rate(noShowCount, reservationBaseCount),
			rate(acceptedOfferCount, finalOfferCount),
			rate(refilledCancelledReservationCount, cancelledReservationCount),
			(int) Math.round(averageWalkInWaitMinutes),
			rate(abandonedWalkInCount, walkInCount));
	}

	@Transactional(readOnly = true)
	public List<DailyReservationAnalytics> getDailyReservationAnalytics(UUID userId, UUID storeId, LocalDate from,
		LocalDate to) {
		storeAuthorizationService.requireManager(userId, storeId);
		Store store = requireStore(storeId);
		Range range = range(store, from, to);
		List<Reservation> reservations = reservationRepository.findAllByStoreIdAndStartAtBetween(storeId, range.from(),
			range.toExclusive());
		ZoneId zoneId = ZoneId.of(store.getTimezone());
		Map<LocalDate, List<Reservation>> reservationsByDate = reservations.stream()
			.filter(reservation -> RESERVATION_FINAL_STATUSES.contains(reservation.getStatus()))
			.collect(Collectors.groupingBy(reservation -> reservation.getStartAt().atZone(zoneId).toLocalDate()));
		List<DailyReservationAnalytics> results = new ArrayList<>();
		for (LocalDate date = range.startDate(); !date.isAfter(range.endDate()); date = date.plusDays(1)) {
			List<Reservation> dailyReservations = reservationsByDate.getOrDefault(date, List.of());
			results.add(new DailyReservationAnalytics(date, dailyReservations.size(),
				countReservations(dailyReservations, ReservationStatus.CONFIRMED),
				countReservations(dailyReservations, ReservationStatus.CHECKED_IN),
				countReservations(dailyReservations, ReservationStatus.IN_SERVICE),
				countReservations(dailyReservations, ReservationStatus.COMPLETED),
				countReservations(dailyReservations, ReservationStatus.CANCELLED),
				countReservations(dailyReservations, ReservationStatus.NO_SHOW)));
		}
		return results;
	}

	@Transactional(readOnly = true)
	public List<StaffAnalytics> getStaffAnalytics(UUID userId, UUID storeId, LocalDate from, LocalDate to) {
		storeAuthorizationService.requireManager(userId, storeId);
		Store store = requireStore(storeId);
		Range range = range(store, from, to);
		List<Reservation> reservations = reservationRepository.findAllByStoreIdAndStartAtBetween(storeId, range.from(),
			range.toExclusive());
		List<ServiceSession> sessions = serviceSessionRepository.findAllByStoreIdAndActualStartAtBetween(storeId, range.from(),
			range.toExclusive());
		Map<UUID, List<Reservation>> reservationsByStaffId = reservations.stream()
			.filter(reservation -> reservation.getAssignedStaffId() != null)
			.filter(reservation -> RESERVATION_FINAL_STATUSES.contains(reservation.getStatus()))
			.collect(Collectors.groupingBy(Reservation::getAssignedStaffId));
		Map<UUID, List<ServiceSession>> sessionsByStaffId = sessions.stream()
			.filter(session -> session.getStatus() == ServiceSessionStatus.COMPLETED)
			.collect(Collectors.groupingBy(ServiceSession::getStaffId));
		Map<UUID, StoreMember> staffMembers = storeMemberRepository.findAllByStoreIdOrderByCreatedAtAsc(storeId).stream()
			.collect(Collectors.toMap(StoreMember::getId, Function.identity()));
		return staffMembers.values().stream()
			.map(staff -> toStaffAnalytics(staff, reservationsByStaffId.getOrDefault(staff.getId(), List.of()),
				sessionsByStaffId.getOrDefault(staff.getId(), List.of())))
			.filter(stat -> stat.reservationCount() > 0 || stat.completedWalkInServiceCount() > 0)
			.sorted(Comparator.comparing(StaffAnalytics::reservationCount).reversed()
				.thenComparing(StaffAnalytics::staffName))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<ServiceDurationAnalytics> getServiceDurationAnalytics(UUID userId, UUID storeId, LocalDate from,
		LocalDate to) {
		storeAuthorizationService.requireManager(userId, storeId);
		Store store = requireStore(storeId);
		Range range = range(store, from, to);
		List<ServiceSession> sessions = serviceSessionRepository.findAllByStoreIdAndActualStartAtBetween(storeId, range.from(),
			range.toExclusive());
		Map<UUID, ServiceOffering> services = serviceRepository.findAllByStoreIdOrderByCreatedAtAsc(storeId).stream()
			.collect(Collectors.toMap(ServiceOffering::getId, Function.identity()));
		return sessions.stream()
			.filter(session -> session.getStatus() == ServiceSessionStatus.COMPLETED)
			.collect(Collectors.groupingBy(ServiceSession::getServiceId))
			.entrySet().stream()
			.map(entry -> toServiceDurationAnalytics(services.get(entry.getKey()), entry.getValue()))
			.filter(stat -> stat != null)
			.sorted(Comparator.comparing(ServiceDurationAnalytics::sessionCount).reversed()
				.thenComparing(ServiceDurationAnalytics::serviceName))
			.toList();
	}

	private long countReservations(List<Reservation> reservations, ReservationStatus status) {
		return reservations.stream().filter(reservation -> reservation.getStatus() == status).count();
	}

	private StaffAnalytics toStaffAnalytics(StoreMember staff, List<Reservation> reservations, List<ServiceSession> sessions) {
		long completedReservations = countReservations(reservations, ReservationStatus.COMPLETED);
		long cancelledReservations = countReservations(reservations, ReservationStatus.CANCELLED);
		long noShowReservations = countReservations(reservations, ReservationStatus.NO_SHOW);
		long completedWalkInServiceCount = sessions.size();
		double averageServiceMinutes = sessions.stream()
			.mapToLong(ServiceSession::getActualDurationMinutes)
			.average()
			.orElse(0);
		return new StaffAnalytics(staff.getId(), staff.getDisplayName(), reservations.size(), completedReservations,
			cancelledReservations, noShowReservations, completedWalkInServiceCount,
			(int) Math.round(averageServiceMinutes));
	}

	private ServiceDurationAnalytics toServiceDurationAnalytics(ServiceOffering service, List<ServiceSession> sessions) {
		if (service == null || sessions.isEmpty()) {
			return null;
		}
		double averageDuration = sessions.stream().mapToLong(ServiceSession::getActualDurationMinutes).average().orElse(0);
		return new ServiceDurationAnalytics(service.getId(), service.getName(), sessions.size(),
			service.getDurationMinutes(), (int) Math.round(averageDuration),
			(int) Math.round(averageDuration - service.getDurationMinutes()));
	}

	private Store requireStore(UUID storeId) {
		return storeRepository.findById(storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
	}

	private Range range(Store store, LocalDate from, LocalDate to) {
		LocalDate start = from == null ? LocalDate.now(ZoneId.of(store.getTimezone())).minusDays(7) : from;
		LocalDate end = to == null ? start.plusDays(7) : to;
		if (end.isBefore(start)) {
			throw new BusinessException(ErrorCode.INVALID_AVAILABILITY_RANGE);
		}
		ZoneId zoneId = ZoneId.of(store.getTimezone());
		return new Range(start, end, start.atStartOfDay(zoneId).toInstant(),
			end.plusDays(1).atStartOfDay(zoneId).toInstant());
	}

	private long waitDurationMinutes(WalkInEntry entry) {
		Instant completedAt = entry.getCheckedInAt() != null ? entry.getCheckedInAt()
			: entry.getServiceStartedAt() != null ? entry.getServiceStartedAt()
			: entry.getCompletedAt();
		if (entry.getCreatedAt() == null || completedAt == null) {
			return -1;
		}
		return Duration.between(entry.getCreatedAt(), completedAt).toMinutes();
	}

	private double rate(long numerator, long denominator) {
		if (denominator <= 0) {
			return 0;
		}
		return (double) numerator / denominator;
	}

	private record Range(LocalDate startDate, LocalDate endDate, Instant from, Instant toExclusive) { }

	public record AnalyticsSummary(long reservationCount, double completionRate, double cancellationRate,
		double noShowRate, double waitlistOfferAcceptanceRate, double cancelledSlotRefillRate,
		int averageWalkInWaitMinutes, double walkInAbandonmentRate) { }

	public record DailyReservationAnalytics(LocalDate date, long reservationCount, long confirmedCount,
		long checkedInCount, long inServiceCount, long completedCount, long cancelledCount, long noShowCount) { }

	public record StaffAnalytics(UUID staffId, String staffName, long reservationCount, long completedReservationCount,
		long cancelledReservationCount, long noShowReservationCount, long completedWalkInServiceCount,
		int averageCompletedWalkInServiceMinutes) { }

	public record ServiceDurationAnalytics(UUID serviceId, String serviceName, long sessionCount,
		int expectedDurationMinutes, int averageActualDurationMinutes, int averageDurationDeltaMinutes) { }
}
