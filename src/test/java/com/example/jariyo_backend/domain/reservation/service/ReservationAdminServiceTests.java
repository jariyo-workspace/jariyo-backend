package com.example.jariyo_backend.domain.reservation.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.common.idempotency.PersistentIdempotencyService;
import com.example.jariyo_backend.domain.admin.repository.AuditLogRepository;
import com.example.jariyo_backend.domain.reservation.entity.Reservation;
import com.example.jariyo_backend.domain.reservation.entity.ReservationSource;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.repository.ReservationRepository;
import com.example.jariyo_backend.domain.reservation.repository.ReservationStatusHistoryRepository;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.entity.StorePolicy;
import com.example.jariyo_backend.domain.store.entity.StoreStatus;
import com.example.jariyo_backend.domain.store.repository.StaffServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StorePolicyRepository;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.entity.StoreMemberRole;
import com.example.jariyo_backend.domain.user.entity.UserAccount;
import com.example.jariyo_backend.domain.user.service.StoreAuthorizationService;
import com.example.jariyo_backend.domain.walkin.entity.ServiceSession;
import com.example.jariyo_backend.domain.walkin.entity.ServiceSessionStatus;
import com.example.jariyo_backend.domain.walkin.repository.ServiceSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationAdminServiceTests {
	@Mock ReservationRepository reservationRepository;
	@Mock ReservationStatusHistoryRepository historyRepository;
	@Mock StoreRepository storeRepository;
	@Mock StorePolicyRepository storePolicyRepository;
	@Mock StaffServiceRepository staffServiceRepository;
	@Mock ServiceSessionRepository serviceSessionRepository;
	@Mock StoreAuthorizationService storeAuthorizationService;
	@Mock PersistentIdempotencyService idempotencyService;
	@Mock AuditLogRepository auditLogRepository;

	@Test
	void checkInTransitionsConfirmedReservationWithinWindow() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UUID reservationId = UUID.randomUUID();
		UUID staffId = UUID.randomUUID();
		Reservation reservation = reservation(reservationId, storeId, ReservationStatus.CONFIRMED);
		when(storeAuthorizationService.requireStaff(userId, storeId)).thenReturn(storeMember(staffId, storeId));
		when(storeRepository.findById(storeId)).thenReturn(Optional.of(store(storeId)));
		when(storePolicyRepository.findByStoreId(storeId)).thenReturn(Optional.of(policy(storeId)));
		when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
		mockIdempotent();
		ReservationAdminService service = serviceAt("2026-08-02T04:55:00Z");

		ReservationAdminService.ReservationCheckInResult result = service.checkIn(userId, storeId, reservationId, "key");

		assertEquals(ReservationStatus.CHECKED_IN, result.status());
		assertEquals(ReservationStatus.CHECKED_IN, reservation.getStatus());
		verify(historyRepository).save(any());
		verify(auditLogRepository).save(any());
	}

	@Test
	void markNoShowRejectsBeforeNoShowThreshold() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UUID reservationId = UUID.randomUUID();
		Reservation reservation = reservation(reservationId, storeId, ReservationStatus.CONFIRMED);
		when(storeAuthorizationService.requireStaff(userId, storeId)).thenReturn(storeMember(UUID.randomUUID(), storeId));
		when(storeRepository.findById(storeId)).thenReturn(Optional.of(store(storeId)));
		when(storePolicyRepository.findByStoreId(storeId)).thenReturn(Optional.of(policy(storeId)));
		when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
		mockIdempotent();
		ReservationAdminService service = serviceAt("2026-08-02T05:05:00Z");

		BusinessException exception = assertThrows(BusinessException.class,
			() -> service.markNoShow(userId, storeId, reservationId, "key",
				new ReservationAdminService.ReservationNoShowCommand("미방문")));

		assertEquals(ErrorCode.RESERVATION_NOT_NO_SHOW_CANDIDATE, exception.getErrorCode());
	}

	@Test
	void startServiceCreatesReservationServiceSession() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UUID reservationId = UUID.randomUUID();
		UUID staffId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		Reservation reservation = reservation(reservationId, storeId, ReservationStatus.CHECKED_IN);
		when(storeAuthorizationService.requireStaff(userId, storeId)).thenReturn(storeMember(UUID.randomUUID(), storeId));
		when(storeRepository.findById(storeId)).thenReturn(Optional.of(store(storeId)));
		when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
		when(staffServiceRepository.existsByStoreMemberIdAndServiceIdAndActiveTrue(staffId, reservation.getServiceId()))
			.thenReturn(true);
		when(serviceSessionRepository.findByReservationIdAndStatus(reservationId, ServiceSessionStatus.IN_PROGRESS))
			.thenReturn(Optional.empty());
		when(serviceSessionRepository.save(any(ServiceSession.class))).thenAnswer(invocation -> {
			ServiceSession session = invocation.getArgument(0);
			setField(ServiceSession.class, session, "id", sessionId);
			return session;
		});
		mockIdempotent();
		ReservationAdminService service = serviceAt("2026-08-02T05:15:00Z");

		ReservationAdminService.StartReservationServiceResult result = service.startService(userId, storeId, reservationId,
			"key", new ReservationAdminService.StartReservationServiceCommand(staffId));

		assertEquals(sessionId, result.serviceSessionId());
		assertEquals(ReservationStatus.IN_SERVICE, reservation.getStatus());
		ArgumentCaptor<ServiceSession> captor = ArgumentCaptor.forClass(ServiceSession.class);
		verify(serviceSessionRepository).save(captor.capture());
		assertEquals(reservationId, captor.getValue().getReservationId());
	}

	private ReservationAdminService serviceAt(String now) {
		return new ReservationAdminService(reservationRepository, historyRepository, storeRepository,
			storePolicyRepository, staffServiceRepository, serviceSessionRepository, storeAuthorizationService,
			idempotencyService, auditLogRepository, Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
	}

	private void mockIdempotent() {
		doAnswer(invocation -> invocation.<java.util.function.Supplier<?>>getArgument(5).get())
			.when(idempotencyService)
			.execute(any(), any(), any(), any(), any(), any());
	}

	private Reservation reservation(UUID reservationId, UUID storeId, ReservationStatus status) {
		return new Reservation(reservationId, storeId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
			ReservationSource.CUSTOMER_BOOKING, status, Instant.parse("2026-08-02T05:00:00Z"),
			Instant.parse("2026-08-02T05:30:00Z"), Instant.parse("2026-08-02T05:40:00Z"), 1);
	}

	private Store store(UUID storeId) {
		return new Store(storeId, "자리요", null, "0212345678", "서울", "Asia/Seoul", StoreStatus.ACTIVE);
	}

	private StorePolicy policy(UUID storeId) {
		return new StorePolicy(UUID.randomUUID(), storeId, 14, 60, 60, 10, 10, 15, 5, 3, 3, true, true, true);
	}

	private StoreMember storeMember(UUID memberId, UUID storeId) {
		return new StoreMember(memberId, storeId, new UserAccount("staff@example.com", "+821012345678", "hash"),
			StoreMemberRole.STAFF, "민지", true);
	}

	private void setField(Class<?> type, Object target, String name, Object value) throws Exception {
		var field = type.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
