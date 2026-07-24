package com.example.jariyo_backend.domain.admin.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.reservation.entity.Reservation;
import com.example.jariyo_backend.domain.reservation.entity.ReservationSource;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.repository.ReservationRepository;
import com.example.jariyo_backend.domain.store.entity.ServiceOffering;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.entity.StorePolicy;
import com.example.jariyo_backend.domain.store.entity.StoreStatus;
import com.example.jariyo_backend.domain.store.repository.ServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StorePolicyRepository;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.user.entity.CustomerProfile;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.entity.StoreMemberRole;
import com.example.jariyo_backend.domain.user.entity.UserAccount;
import com.example.jariyo_backend.domain.user.repository.CustomerProfileRepository;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import com.example.jariyo_backend.domain.user.service.StoreAuthorizationService;
import com.example.jariyo_backend.domain.waitlist.entity.SlotOffer;
import com.example.jariyo_backend.domain.waitlist.entity.WaitlistEntry;
import com.example.jariyo_backend.domain.waitlist.entity.WaitlistStatus;
import com.example.jariyo_backend.domain.waitlist.entity.StaffPreferenceType;
import com.example.jariyo_backend.domain.waitlist.repository.SlotOfferRepository;
import com.example.jariyo_backend.domain.waitlist.repository.WaitlistEntryRepository;
import com.example.jariyo_backend.domain.walkin.entity.WalkInEntry;
import com.example.jariyo_backend.domain.walkin.entity.WalkInStatus;
import com.example.jariyo_backend.domain.walkin.repository.WalkInEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOperationQueryServiceTests {
	@Mock StoreAuthorizationService storeAuthorizationService;
	@Mock StoreRepository storeRepository;
	@Mock StorePolicyRepository storePolicyRepository;
	@Mock ReservationRepository reservationRepository;
	@Mock WaitlistEntryRepository waitlistEntryRepository;
	@Mock SlotOfferRepository slotOfferRepository;
	@Mock WalkInEntryRepository walkInEntryRepository;
	@Mock CustomerProfileRepository customerProfileRepository;
	@Mock ServiceRepository serviceRepository;
	@Mock StoreMemberRepository storeMemberRepository;

	@Test
	void getTodayDashboardAggregatesCounts() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		Clock clock = Clock.fixed(Instant.parse("2026-07-24T03:00:00Z"), ZoneOffset.UTC);
		AdminOperationQueryService service = service(clock);
		when(storeRepository.findById(storeId)).thenReturn(Optional.of(
			new Store(storeId, "자리요", "설명", "02-1234-5678", "서울", "Asia/Seoul", StoreStatus.ACTIVE)));
		when(storePolicyRepository.findByStoreId(storeId)).thenReturn(Optional.of(
			new StorePolicy(UUID.randomUUID(), storeId, 30, 60, 120, 30, 10, 15, 5, 3, 3, true, true, true)));
		UUID reservationCustomerId = UUID.randomUUID();
		Reservation confirmed = new Reservation(UUID.randomUUID(), storeId, reservationCustomerId, UUID.randomUUID(),
			null, ReservationSource.CUSTOMER_BOOKING, ReservationStatus.CONFIRMED, Instant.parse("2026-07-24T02:00:00Z"),
			Instant.parse("2026-07-24T02:30:00Z"), Instant.parse("2026-07-24T02:40:00Z"), 1);
		Reservation checkedIn = new Reservation(UUID.randomUUID(), storeId, UUID.randomUUID(), UUID.randomUUID(),
			null, ReservationSource.CUSTOMER_BOOKING, ReservationStatus.CHECKED_IN, Instant.parse("2026-07-24T04:00:00Z"),
			Instant.parse("2026-07-24T04:30:00Z"), Instant.parse("2026-07-24T04:40:00Z"), 1);
		when(reservationRepository.findDailyReservations(eq(storeId), any(), any())).thenReturn(List.of(confirmed, checkedIn));
		WalkInEntry waiting = WalkInEntry.forGuest(storeId, "손님", "01012345678", UUID.randomUUID(), null, 1,
			LocalDate.of(2026, 7, 24), 1, 10);
		WalkInEntry checkedWalkIn = WalkInEntry.forGuest(storeId, "손님2", "01000000000", UUID.randomUUID(), null, 1,
			LocalDate.of(2026, 7, 24), 2, 5);
		checkedWalkIn.call(Instant.parse("2026-07-24T02:58:00Z"), Instant.parse("2026-07-24T03:03:00Z"));
		checkedWalkIn.transitionTo(WalkInStatus.CHECKED_IN, Instant.parse("2026-07-24T03:00:00Z"));
		when(walkInEntryRepository.findAllByStoreIdAndOperationDateOrderByQueueNumberAsc(storeId, LocalDate.of(2026, 7, 24)))
			.thenReturn(List.of(waiting, checkedWalkIn));
		SlotOffer pendingOffer = new SlotOffer(UUID.randomUUID(), storeId, UUID.randomUUID(), null,
			Instant.parse("2026-07-24T05:00:00Z"), Instant.parse("2026-07-24T05:30:00Z"),
			Instant.parse("2026-07-24T05:40:00Z"), UUID.randomUUID(), Instant.parse("2026-07-24T03:30:00Z"));
		setField(pendingOffer, "id", UUID.randomUUID());
		when(slotOfferRepository.findActiveByStoreIdAndStatus(storeId, com.example.jariyo_backend.domain.waitlist.entity.SlotOfferStatus.PENDING,
			Instant.parse("2026-07-24T03:00:00Z"))).thenReturn(List.of(pendingOffer));

		AdminOperationQueryService.TodayDashboard result = service.getTodayDashboard(userId, storeId);

		verify(storeAuthorizationService).requireRole(userId, storeId, StoreMemberRole.STAFF);
		assertEquals(LocalDate.of(2026, 7, 24), result.date());
		assertEquals(2, result.summary().reservationCount());
		assertEquals(1, result.summary().waitingWalkInCount());
		assertEquals(2, result.summary().checkedInCount());
		assertEquals(0, result.summary().inServiceCount());
		assertEquals(1, result.summary().noShowCandidateCount());
		assertEquals(1, result.summary().pendingSlotOfferCount());
		assertEquals("NO_SHOW_CANDIDATE", result.alerts().get(0).type());
	}

	@Test
	void listWaitlistsReturnsResolvedNames() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		Clock clock = Clock.fixed(Instant.parse("2026-07-24T00:00:00Z"), ZoneOffset.UTC);
		AdminOperationQueryService service = service(clock);
		when(storeRepository.findById(storeId)).thenReturn(Optional.of(
			new Store(storeId, "자리요", "설명", "02-1234-5678", "서울", "Asia/Seoul", StoreStatus.ACTIVE)));
		UserAccount user = new UserAccount("user@example.com", "+821012345678", "hash");
		CustomerProfile profile = new CustomerProfile(user, "고객", true, true);
		UUID profileId = UUID.randomUUID();
		setField(profile, "id", profileId);
		UUID serviceId = UUID.randomUUID();
		UUID staffId = UUID.randomUUID();
		WaitlistEntry entry = new WaitlistEntry(storeId, profileId, serviceId, staffId, StaffPreferenceType.SPECIFIC_ONLY,
			LocalDate.of(2026, 7, 24), LocalTime.of(14, 0), LocalTime.of(16, 0), 1, 3, Instant.parse("2026-07-24T09:00:00Z"));
		when(waitlistEntryRepository.findAllByStoreIdAndDesiredDateOrderBySequenceNumberAscCreatedAtAsc(storeId,
			LocalDate.of(2026, 7, 24))).thenReturn(List.of(entry));
		when(customerProfileRepository.findAllById(anyIterable())).thenReturn(List.of(profile));
		ServiceOffering serviceOffering = serviceOffering(serviceId, storeId, "펌");
		when(serviceRepository.findAllById(anyIterable())).thenReturn(List.of(serviceOffering));
		when(storeMemberRepository.findAllById(anyIterable())).thenReturn(List.of(
			new StoreMember(staffId, storeId, user, StoreMemberRole.STAFF, "수진", true)));
		when(slotOfferRepository.findActiveByStoreIdAndStatus(storeId,
			com.example.jariyo_backend.domain.waitlist.entity.SlotOfferStatus.PENDING, Instant.parse("2026-07-24T00:00:00Z")))
			.thenReturn(List.of());

		List<AdminOperationQueryService.AdminWaitlistItem> result = service.listWaitlists(userId, storeId,
			LocalDate.of(2026, 7, 24), null, null, WaitlistStatus.WAITING);

		assertEquals(1, result.size());
		assertEquals("고객", result.get(0).customerName());
		assertEquals("펌", result.get(0).serviceName());
		assertEquals("수진", result.get(0).preferredStaffName());
	}

	private AdminOperationQueryService service(Clock clock) {
		return new AdminOperationQueryService(storeAuthorizationService, storeRepository, storePolicyRepository,
			reservationRepository, waitlistEntryRepository, slotOfferRepository, walkInEntryRepository,
			customerProfileRepository, serviceRepository, storeMemberRepository, clock);
	}

	private com.example.jariyo_backend.domain.store.entity.ServiceOffering serviceOffering(UUID serviceId, UUID storeId,
		String name) {
		ServiceOffering service = org.mockito.Mockito.mock(ServiceOffering.class);
		org.mockito.Mockito.when(service.getId()).thenReturn(serviceId);
		org.mockito.Mockito.when(service.getName()).thenReturn(name);
		return service;
	}

	private void setField(Object target, String name, Object value) {
		try {
			java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			field.set(target, value);
		}
		catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
