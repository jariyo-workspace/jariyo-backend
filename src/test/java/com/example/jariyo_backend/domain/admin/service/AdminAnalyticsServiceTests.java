package com.example.jariyo_backend.domain.admin.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.reservation.entity.Reservation;
import com.example.jariyo_backend.domain.reservation.entity.ReservationSource;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.repository.ReservationRepository;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.entity.StoreStatus;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.user.service.StoreAuthorizationService;
import com.example.jariyo_backend.domain.waitlist.entity.SlotOffer;
import com.example.jariyo_backend.domain.waitlist.entity.SlotOfferStatus;
import com.example.jariyo_backend.domain.waitlist.repository.SlotOfferRepository;
import com.example.jariyo_backend.domain.walkin.entity.WalkInEntry;
import com.example.jariyo_backend.domain.walkin.repository.WalkInEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsServiceTests {
	@Mock StoreAuthorizationService storeAuthorizationService;
	@Mock StoreRepository storeRepository;
	@Mock ReservationRepository reservationRepository;
	@Mock SlotOfferRepository slotOfferRepository;
	@Mock WalkInEntryRepository walkInEntryRepository;

	@Test
	void computesSummaryFromReservationWaitlistAndWalkInData() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		Store store = new Store(storeId, "자리요", null, "0212345678", "서울", "Asia/Seoul", StoreStatus.ACTIVE);
		Reservation completed = reservation(storeId, ReservationStatus.COMPLETED, UUID.randomUUID());
		Reservation cancelled = reservation(storeId, ReservationStatus.CANCELLED, UUID.randomUUID());
		Reservation noShow = reservation(storeId, ReservationStatus.NO_SHOW, UUID.randomUUID());
		SlotOffer accepted = slotOffer(storeId, SlotOfferStatus.ACCEPTED, cancelled.getId());
		SlotOffer expired = slotOffer(storeId, SlotOfferStatus.EXPIRED, null);
		WalkInEntry checkedIn = WalkInEntry.forCustomer(storeId, UUID.randomUUID(), UUID.randomUUID(), null, 1,
			LocalDate.of(2026, 7, 26), 1, 15);
		WalkInEntry cancelledWalkIn = WalkInEntry.forCustomer(storeId, UUID.randomUUID(), UUID.randomUUID(), null, 1,
			LocalDate.of(2026, 7, 26), 2, 30);
		checkedIn.call(Instant.parse("2026-07-26T01:10:00Z"), Instant.parse("2026-07-26T01:30:00Z"));
		checkedIn.transitionTo(com.example.jariyo_backend.domain.walkin.entity.WalkInStatus.CHECKED_IN,
			Instant.parse("2026-07-26T01:20:00Z"));
		cancelledWalkIn.transitionTo(com.example.jariyo_backend.domain.walkin.entity.WalkInStatus.CANCELLED,
			Instant.parse("2026-07-26T02:30:00Z"));
		setField(checkedIn, "createdAt", Instant.parse("2026-07-26T01:00:00Z"));
		setField(cancelledWalkIn, "createdAt", Instant.parse("2026-07-26T02:00:00Z"));
		when(storeRepository.findById(storeId)).thenReturn(Optional.of(store));
		when(reservationRepository.findAllByStoreIdAndStartAtBetween(storeId,
			LocalDate.of(2026, 7, 20).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
			LocalDate.of(2026, 7, 27).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()))
			.thenReturn(List.of(completed, cancelled, noShow));
		when(slotOfferRepository.findAllByStoreIdAndCreatedAtBetween(storeId,
			LocalDate.of(2026, 7, 20).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
			LocalDate.of(2026, 7, 27).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()))
			.thenReturn(List.of(accepted, expired));
		when(walkInEntryRepository.findAllByStoreIdAndCreatedAtBetween(storeId,
			LocalDate.of(2026, 7, 20).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
			LocalDate.of(2026, 7, 27).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()))
			.thenReturn(List.of(checkedIn, cancelledWalkIn));

		AdminAnalyticsService.AnalyticsSummary summary = analyticsService().getSummary(userId, storeId,
			LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26));

		assertEquals(3, summary.reservationCount());
		assertEquals(1d / 3d, summary.completionRate());
		assertEquals(1d / 3d, summary.cancellationRate());
		assertEquals(1d / 3d, summary.noShowRate());
		assertEquals(0.5d, summary.waitlistOfferAcceptanceRate());
		assertEquals(1.0d, summary.cancelledSlotRefillRate());
		assertEquals(20, summary.averageWalkInWaitMinutes());
		assertEquals(0.5d, summary.walkInAbandonmentRate());
		verify(storeAuthorizationService).requireManager(userId, storeId);
	}

	private AdminAnalyticsService analyticsService() {
		return new AdminAnalyticsService(storeAuthorizationService, storeRepository, reservationRepository,
			slotOfferRepository, walkInEntryRepository);
	}

	private Reservation reservation(UUID storeId, ReservationStatus status, UUID staffId) throws Exception {
		Reservation reservation = new Reservation(UUID.randomUUID(), storeId, UUID.randomUUID(), UUID.randomUUID(),
			staffId, ReservationSource.CUSTOMER_BOOKING, status, Instant.parse("2026-07-26T01:00:00Z"),
			Instant.parse("2026-07-26T01:30:00Z"), Instant.parse("2026-07-26T01:40:00Z"), 1);
		setField(reservation, "createdAt", Instant.parse("2026-07-25T10:00:00Z"));
		return reservation;
	}

	private SlotOffer slotOffer(UUID storeId, SlotOfferStatus status, UUID sourceReservationId) throws Exception {
		SlotOffer offer = new SlotOffer(UUID.randomUUID(), storeId, UUID.randomUUID(), UUID.randomUUID(),
			Instant.parse("2026-07-26T03:00:00Z"), Instant.parse("2026-07-26T03:30:00Z"),
			Instant.parse("2026-07-26T03:40:00Z"), sourceReservationId, Instant.parse("2026-07-26T02:30:00Z"));
		if (status == SlotOfferStatus.ACCEPTED) {
			offer.accept(UUID.randomUUID(), Instant.parse("2026-07-26T02:10:00Z"));
		}
		if (status == SlotOfferStatus.EXPIRED) {
			offer.expire();
		}
		setField(offer, "createdAt", Instant.parse("2026-07-25T11:00:00Z"));
		return offer;
	}

	private void setField(Object target, String fieldName, Object value) throws Exception {
		var field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}
}
