package com.example.jariyo_backend.domain.reservation.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.common.async.AsyncEventRecorder;
import com.example.jariyo_backend.common.async.AsyncEventType;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.common.idempotency.PersistentIdempotencyService;
import com.example.jariyo_backend.domain.reservation.repository.ReservationStatusHistoryRepository;
import com.example.jariyo_backend.domain.store.entity.StoreServiceDefinition;
import com.example.jariyo_backend.domain.reservation.entity.Reservation;
import com.example.jariyo_backend.domain.reservation.entity.ReservationSource;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.repository.ReservationRepository;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.entity.StorePolicy;
import com.example.jariyo_backend.domain.store.entity.StoreStatus;
import com.example.jariyo_backend.domain.store.repository.StorePolicyRepository;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.store.repository.StoreServiceDefinitionRepository;
import com.example.jariyo_backend.domain.user.entity.CustomerProfile;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.entity.UserAccount;
import com.example.jariyo_backend.domain.user.repository.CustomerProfileRepository;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import com.example.jariyo_backend.domain.waitlist.service.WaitlistService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTests {
	@Mock ReservationRepository reservationRepository;
	@Mock ReservationStatusHistoryRepository historyRepository;
	@Mock ReservationBookingService bookingService;
	@Mock CustomerProfileRepository customerProfileRepository;
	@Mock StoreRepository storeRepository;
	@Mock StorePolicyRepository storePolicyRepository;
	@Mock StoreServiceDefinitionRepository serviceRepository;
	@Mock StoreMemberRepository storeMemberRepository;
	@Mock PersistentIdempotencyService idempotencyService;
	@Mock WaitlistService waitlistService;
	@Mock AsyncEventRecorder asyncEventRecorder;

	@Test
	void cancelsOwnedReservationUsingCustomerProfileId() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID customerProfileId = UUID.randomUUID();
		UUID reservationId = UUID.randomUUID();
		CustomerProfile customer = new CustomerProfile(new UserAccount("user@example.com", "+821012345678", "hash"),
			"류승엽", false, true);
		Reservation reservation = new Reservation(reservationId, UUID.randomUUID(), customerProfileId, UUID.randomUUID(),
			UUID.randomUUID(), ReservationSource.CUSTOMER_BOOKING, ReservationStatus.CONFIRMED,
			Instant.parse("2026-07-27T05:00:00Z"), Instant.parse("2026-07-27T05:30:00Z"),
			Instant.parse("2026-07-27T05:40:00Z"), 1);
		Store store = new Store(reservation.getStoreId(), "자리요", null, "0212345678", "서울", "Asia/Seoul", StoreStatus.ACTIVE);
		StorePolicy policy = new StorePolicy(UUID.randomUUID(), store.getId(), 14, 60, 1440, 10, 10, 10, 5, 3, 3,
			true, true, true);
		setId(customer, customerProfileId);
		when(customerProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(customer));
		when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
		when(storeRepository.findById(store.getId())).thenReturn(Optional.of(store));
		when(storePolicyRepository.findByStoreId(store.getId())).thenReturn(Optional.of(policy));
		mockIdempotentExecution();
		ReservationService service = new ReservationService(reservationRepository, historyRepository, bookingService,
			customerProfileRepository, storeRepository, storePolicyRepository, serviceRepository,
			storeMemberRepository, idempotencyService, waitlistService, asyncEventRecorder,
			Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC));

		ReservationService.CancelReservationResult result = service.cancelMine(userId, reservationId, "key",
			new ReservationService.CancelReservationCommand("개인 일정이 생겼습니다."));

		assertEquals(ReservationStatus.CANCELLED, result.status());
		assertEquals("CUSTOMER", result.cancelledByType());
		assertEquals("개인 일정이 생겼습니다.", reservation.getCancellationReason());
		verify(waitlistService).offerCancelledReservation(eq(reservation), any());
		verify(asyncEventRecorder).record(eq(AsyncEventType.RESERVATION_CANCELLED), eq(reservation.getStoreId()),
			eq("RESERVATION"), eq(reservationId), any());
	}

	@Test
	void rejectsReservationOwnedByAnotherCustomerProfile() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID customerProfileId = UUID.randomUUID();
		UUID reservationId = UUID.randomUUID();
		CustomerProfile customer = new CustomerProfile(new UserAccount("user@example.com", "+821012345678", "hash"),
			"류승엽", false, true);
		setId(customer, customerProfileId);
		when(customerProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(customer));
		when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(new Reservation(
			reservationId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
			ReservationSource.CUSTOMER_BOOKING, ReservationStatus.CONFIRMED,
			Instant.parse("2026-07-27T05:00:00Z"), Instant.parse("2026-07-27T05:30:00Z"),
			Instant.parse("2026-07-27T05:40:00Z"), 1)));
		mockIdempotentExecution();
		ReservationService service = new ReservationService(reservationRepository, historyRepository, bookingService,
			customerProfileRepository, storeRepository, storePolicyRepository, serviceRepository,
			storeMemberRepository, idempotencyService, waitlistService, asyncEventRecorder,
			Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC));

		BusinessException exception = assertThrows(BusinessException.class,
			() -> service.cancelMine(userId, reservationId, "key",
				new ReservationService.CancelReservationCommand("개인 일정이 생겼습니다.")));

		assertEquals(ErrorCode.RESERVATION_NOT_OWNED_BY_USER, exception.getErrorCode());
		verify(waitlistService, never()).offerCancelledReservation(any(), any());
	}

	@Test
	void rejectsConfirmedReservationAfterCancellationDeadline() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID customerProfileId = UUID.randomUUID();
		UUID reservationId = UUID.randomUUID();
		CustomerProfile customer = new CustomerProfile(new UserAccount("user@example.com", "+821012345678", "hash"),
			"류승엽", false, true);
		Reservation reservation = new Reservation(reservationId, UUID.randomUUID(), customerProfileId, UUID.randomUUID(),
			UUID.randomUUID(), ReservationSource.CUSTOMER_BOOKING, ReservationStatus.CONFIRMED,
			Instant.parse("2026-07-27T01:00:00Z"), Instant.parse("2026-07-27T01:30:00Z"),
			Instant.parse("2026-07-27T01:40:00Z"), 1);
		Store store = new Store(reservation.getStoreId(), "자리요", null, "0212345678", "서울", "Asia/Seoul", StoreStatus.ACTIVE);
		StorePolicy policy = new StorePolicy(UUID.randomUUID(), store.getId(), 14, 60, 60, 10, 10, 10, 5, 3, 3,
			true, true, true);
		setId(customer, customerProfileId);
		when(customerProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(customer));
		when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
		when(storeRepository.findById(store.getId())).thenReturn(Optional.of(store));
		when(storePolicyRepository.findByStoreId(store.getId())).thenReturn(Optional.of(policy));
		mockIdempotentExecution();
		ReservationService service = new ReservationService(reservationRepository, historyRepository, bookingService,
			customerProfileRepository, storeRepository, storePolicyRepository, serviceRepository,
			storeMemberRepository, idempotencyService, waitlistService, asyncEventRecorder,
			Clock.fixed(Instant.parse("2026-07-27T00:30:01Z"), ZoneOffset.UTC));

		BusinessException exception = assertThrows(BusinessException.class,
			() -> service.cancelMine(userId, reservationId, "key",
				new ReservationService.CancelReservationCommand("개인 일정이 생겼습니다.")));

		assertEquals(ErrorCode.RESERVATION_CANCELLATION_DEADLINE_PASSED, exception.getErrorCode());
	}

	private void mockIdempotentExecution() {
		doAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			java.util.function.Supplier<ReservationService.CancelReservationResult> action = invocation.getArgument(5);
			return action.get();
		}).when(idempotencyService).execute(any(), any(), any(), any(), eq(ReservationService.CancelReservationResult.class), any());
	}

	private void setId(CustomerProfile customerProfile, UUID id) throws Exception {
		var field = CustomerProfile.class.getDeclaredField("id");
		field.setAccessible(true);
		field.set(customerProfile, id);
	}
}
