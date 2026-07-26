package com.example.jariyo_backend.domain.admin.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.domain.reservation.entity.Reservation;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.repository.ReservationRepository;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.user.service.StoreAuthorizationService;
import com.example.jariyo_backend.domain.waitlist.entity.SlotOffer;
import com.example.jariyo_backend.domain.waitlist.entity.SlotOfferStatus;
import com.example.jariyo_backend.domain.waitlist.repository.SlotOfferRepository;
import com.example.jariyo_backend.domain.walkin.entity.WalkInEntry;
import com.example.jariyo_backend.domain.walkin.entity.WalkInStatus;
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
	private final SlotOfferRepository slotOfferRepository;
	private final WalkInEntryRepository walkInEntryRepository;

	public AdminAnalyticsService(StoreAuthorizationService storeAuthorizationService, StoreRepository storeRepository,
		ReservationRepository reservationRepository, SlotOfferRepository slotOfferRepository,
		WalkInEntryRepository walkInEntryRepository) {
		this.storeAuthorizationService = storeAuthorizationService;
		this.storeRepository = storeRepository;
		this.reservationRepository = reservationRepository;
		this.slotOfferRepository = slotOfferRepository;
		this.walkInEntryRepository = walkInEntryRepository;
	}

	@Transactional(readOnly = true)
	public AnalyticsSummary getSummary(UUID userId, UUID storeId, LocalDate from, LocalDate to) {
		storeAuthorizationService.requireManager(userId, storeId);
		Range range = range(storeId, from, to);
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

	private Range range(UUID storeId, LocalDate from, LocalDate to) {
		Store store = storeRepository.findById(storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
		LocalDate start = from == null ? LocalDate.now(ZoneId.of(store.getTimezone())).minusDays(7) : from;
		LocalDate end = to == null ? start.plusDays(7) : to;
		if (end.isBefore(start)) {
			throw new BusinessException(ErrorCode.INVALID_AVAILABILITY_RANGE);
		}
		ZoneId zoneId = ZoneId.of(store.getTimezone());
		return new Range(start.atStartOfDay(zoneId).toInstant(), end.plusDays(1).atStartOfDay(zoneId).toInstant());
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

	private record Range(Instant from, Instant toExclusive) { }

	public record AnalyticsSummary(long reservationCount, double completionRate, double cancellationRate,
		double noShowRate, double waitlistOfferAcceptanceRate, double cancelledSlotRefillRate,
		int averageWalkInWaitMinutes, double walkInAbandonmentRate) { }
}
