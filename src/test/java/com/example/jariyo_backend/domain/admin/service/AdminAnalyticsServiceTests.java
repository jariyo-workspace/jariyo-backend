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
import com.example.jariyo_backend.domain.store.repository.ServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.entity.StoreMemberRole;
import com.example.jariyo_backend.domain.user.entity.UserAccount;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import com.example.jariyo_backend.domain.user.service.StoreAuthorizationService;
import com.example.jariyo_backend.domain.waitlist.entity.SlotOffer;
import com.example.jariyo_backend.domain.waitlist.entity.SlotOfferStatus;
import com.example.jariyo_backend.domain.waitlist.repository.SlotOfferRepository;
import com.example.jariyo_backend.domain.walkin.entity.ServiceSession;
import com.example.jariyo_backend.domain.walkin.repository.ServiceSessionRepository;
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
	@Mock ServiceRepository serviceRepository;
	@Mock StoreMemberRepository storeMemberRepository;
	@Mock SlotOfferRepository slotOfferRepository;
	@Mock ServiceSessionRepository serviceSessionRepository;
	@Mock WalkInEntryRepository walkInEntryRepository;

	@Test
	void computesSummaryFromReservationWaitlistAndWalkInData() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		Store store = new Store(storeId, "자리요", null, "0212345678", "서울", "Asia/Seoul", StoreStatus.ACTIVE);
		Reservation completed = reservation(storeId, ReservationStatus.COMPLETED, UUID.randomUUID(),
			Instant.parse("2026-07-26T01:00:00Z"));
		Reservation cancelled = reservation(storeId, ReservationStatus.CANCELLED, UUID.randomUUID(),
			Instant.parse("2026-07-26T02:00:00Z"));
		Reservation noShow = reservation(storeId, ReservationStatus.NO_SHOW, UUID.randomUUID(),
			Instant.parse("2026-07-26T03:00:00Z"));
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

	@Test
	void returnsDailyReservationAnalyticsByStatus() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		Store store = new Store(storeId, "자리요", null, "0212345678", "서울", "Asia/Seoul", StoreStatus.ACTIVE);
		Reservation completed = reservation(storeId, ReservationStatus.COMPLETED, UUID.randomUUID(),
			Instant.parse("2026-07-25T01:00:00Z"));
		Reservation cancelled = reservation(storeId, ReservationStatus.CANCELLED, UUID.randomUUID(),
			Instant.parse("2026-07-25T03:00:00Z"));
		Reservation confirmed = reservation(storeId, ReservationStatus.CONFIRMED, UUID.randomUUID(),
			Instant.parse("2026-07-26T02:00:00Z"));
		when(storeRepository.findById(storeId)).thenReturn(Optional.of(store));
		when(reservationRepository.findAllByStoreIdAndStartAtBetween(storeId,
			LocalDate.of(2026, 7, 25).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
			LocalDate.of(2026, 7, 27).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()))
			.thenReturn(List.of(completed, cancelled, confirmed));

		List<AdminAnalyticsService.DailyReservationAnalytics> result = analyticsService()
			.getDailyReservationAnalytics(userId, storeId, LocalDate.of(2026, 7, 25), LocalDate.of(2026, 7, 26));

		assertEquals(2, result.size());
		assertEquals(2, result.get(0).reservationCount());
		assertEquals(1, result.get(0).completedCount());
		assertEquals(1, result.get(0).cancelledCount());
		assertEquals(1, result.get(1).confirmedCount());
	}

	@Test
	void returnsStaffAnalyticsWithCompletedWalkInDurations() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UUID staffId = UUID.randomUUID();
		Store store = new Store(storeId, "자리요", null, "0212345678", "서울", "Asia/Seoul", StoreStatus.ACTIVE);
		Reservation completed = reservation(storeId, ReservationStatus.COMPLETED, staffId,
			Instant.parse("2026-07-25T01:00:00Z"));
		Reservation noShow = reservation(storeId, ReservationStatus.NO_SHOW, staffId,
			Instant.parse("2026-07-25T03:00:00Z"));
		ServiceSession session = ServiceSession.forWalkIn(storeId, UUID.randomUUID(), UUID.randomUUID(),
			UUID.randomUUID(), staffId, Instant.parse("2026-07-25T04:00:00Z"));
		session.complete(Instant.parse("2026-07-25T04:45:00Z"), "완료");
		when(storeRepository.findById(storeId)).thenReturn(Optional.of(store));
		when(reservationRepository.findAllByStoreIdAndStartAtBetween(storeId,
			LocalDate.of(2026, 7, 25).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
			LocalDate.of(2026, 7, 27).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()))
			.thenReturn(List.of(completed, noShow));
		when(serviceSessionRepository.findAllByStoreIdAndActualStartAtBetween(storeId,
			LocalDate.of(2026, 7, 25).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
			LocalDate.of(2026, 7, 27).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()))
			.thenReturn(List.of(session));
		when(storeMemberRepository.findAllByStoreIdOrderByCreatedAtAsc(storeId)).thenReturn(List.of(
			new StoreMember(staffId, storeId, new UserAccount("staff@example.com", "+821011111111", "hash"),
				StoreMemberRole.STAFF, "민지", true)));

		List<AdminAnalyticsService.StaffAnalytics> result = analyticsService()
			.getStaffAnalytics(userId, storeId, LocalDate.of(2026, 7, 25), LocalDate.of(2026, 7, 26));

		assertEquals(1, result.size());
		assertEquals("민지", result.get(0).staffName());
		assertEquals(2, result.get(0).reservationCount());
		assertEquals(1, result.get(0).completedReservationCount());
		assertEquals(1, result.get(0).noShowReservationCount());
		assertEquals(1, result.get(0).completedWalkInServiceCount());
		assertEquals(45, result.get(0).averageCompletedWalkInServiceMinutes());
	}

	@Test
	void returnsServiceDurationAnalyticsAgainstExpectedDuration() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UUID serviceId = UUID.randomUUID();
		Store store = new Store(storeId, "자리요", null, "0212345678", "서울", "Asia/Seoul", StoreStatus.ACTIVE);
		ServiceSession first = ServiceSession.forWalkIn(storeId, UUID.randomUUID(), UUID.randomUUID(), serviceId,
			UUID.randomUUID(), Instant.parse("2026-07-25T04:00:00Z"));
		first.complete(Instant.parse("2026-07-25T04:20:00Z"), "완료");
		ServiceSession second = ServiceSession.forWalkIn(storeId, UUID.randomUUID(), UUID.randomUUID(), serviceId,
			UUID.randomUUID(), Instant.parse("2026-07-25T05:00:00Z"));
		second.complete(Instant.parse("2026-07-25T05:40:00Z"), "완료");
		com.example.jariyo_backend.domain.store.entity.ServiceOffering serviceOffering =
			serviceOffering(serviceId, storeId, "커트", 25);
		when(storeRepository.findById(storeId)).thenReturn(Optional.of(store));
		when(serviceSessionRepository.findAllByStoreIdAndActualStartAtBetween(storeId,
			LocalDate.of(2026, 7, 25).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
			LocalDate.of(2026, 7, 27).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()))
			.thenReturn(List.of(first, second));
		when(serviceRepository.findAllByStoreIdOrderByCreatedAtAsc(storeId)).thenReturn(List.of(serviceOffering));

		List<AdminAnalyticsService.ServiceDurationAnalytics> result = analyticsService()
			.getServiceDurationAnalytics(userId, storeId, LocalDate.of(2026, 7, 25), LocalDate.of(2026, 7, 26));

		assertEquals(1, result.size());
		assertEquals("커트", result.get(0).serviceName());
		assertEquals(2, result.get(0).sessionCount());
		assertEquals(25, result.get(0).expectedDurationMinutes());
		assertEquals(30, result.get(0).averageActualDurationMinutes());
		assertEquals(5, result.get(0).averageDurationDeltaMinutes());
	}

	private AdminAnalyticsService analyticsService() {
		return new AdminAnalyticsService(storeAuthorizationService, storeRepository, reservationRepository,
			serviceRepository, storeMemberRepository, slotOfferRepository, serviceSessionRepository,
			walkInEntryRepository);
	}

	private Reservation reservation(UUID storeId, ReservationStatus status, UUID staffId, Instant startAt) throws Exception {
		Reservation reservation = new Reservation(UUID.randomUUID(), storeId, UUID.randomUUID(), UUID.randomUUID(),
			staffId, ReservationSource.CUSTOMER_BOOKING, status, startAt,
			startAt.plusSeconds(30 * 60L), startAt.plusSeconds(40 * 60L), 1);
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

	private com.example.jariyo_backend.domain.store.entity.ServiceOffering serviceOffering(UUID serviceId, UUID storeId,
		String name, int durationMinutes) {
		com.example.jariyo_backend.domain.store.entity.ServiceOffering service =
			org.mockito.Mockito.mock(com.example.jariyo_backend.domain.store.entity.ServiceOffering.class);
		org.mockito.Mockito.when(service.getId()).thenReturn(serviceId);
		org.mockito.Mockito.when(service.getName()).thenReturn(name);
		org.mockito.Mockito.when(service.getDurationMinutes()).thenReturn(durationMinutes);
		return service;
	}

	private void setField(Object target, String fieldName, Object value) throws Exception {
		var field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}
}
