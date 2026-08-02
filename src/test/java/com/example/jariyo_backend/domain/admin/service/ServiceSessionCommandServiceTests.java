package com.example.jariyo_backend.domain.admin.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.common.idempotency.PersistentIdempotencyService;
import com.example.jariyo_backend.domain.admin.repository.AuditLogRepository;
import com.example.jariyo_backend.domain.reservation.entity.Reservation;
import com.example.jariyo_backend.domain.reservation.entity.ReservationSource;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.repository.ReservationRepository;
import com.example.jariyo_backend.domain.reservation.repository.ReservationStatusHistoryRepository;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.entity.StoreStatus;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.entity.StoreMemberRole;
import com.example.jariyo_backend.domain.user.entity.UserAccount;
import com.example.jariyo_backend.domain.user.service.StoreAuthorizationService;
import com.example.jariyo_backend.domain.walkin.entity.ServiceSession;
import com.example.jariyo_backend.domain.walkin.repository.ServiceSessionRepository;
import com.example.jariyo_backend.domain.walkin.repository.WalkInEntryRepository;
import com.example.jariyo_backend.domain.walkin.repository.WalkInStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceSessionCommandServiceTests {
	@Mock ServiceSessionRepository serviceSessionRepository;
	@Mock WalkInEntryRepository walkInEntryRepository;
	@Mock WalkInStatusHistoryRepository walkInStatusHistoryRepository;
	@Mock ReservationRepository reservationRepository;
	@Mock ReservationStatusHistoryRepository reservationStatusHistoryRepository;
	@Mock StoreRepository storeRepository;
	@Mock StoreAuthorizationService storeAuthorizationService;
	@Mock PersistentIdempotencyService idempotencyService;
	@Mock AuditLogRepository auditLogRepository;

	@Test
	void completesReservationSession() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		UUID reservationId = UUID.randomUUID();
		ServiceSession session = ServiceSession.forReservation(storeId, UUID.randomUUID(), reservationId, UUID.randomUUID(),
			UUID.randomUUID(), Instant.parse("2026-08-02T05:10:00Z"));
		setField(ServiceSession.class, session, "id", sessionId);
		Reservation reservation = new Reservation(reservationId, storeId, UUID.randomUUID(), UUID.randomUUID(),
			UUID.randomUUID(), ReservationSource.CUSTOMER_BOOKING, ReservationStatus.IN_SERVICE,
			Instant.parse("2026-08-02T05:00:00Z"), Instant.parse("2026-08-02T05:30:00Z"),
			Instant.parse("2026-08-02T05:40:00Z"), 1);
		when(storeAuthorizationService.requireStaff(userId, storeId)).thenReturn(storeMember(storeId));
		when(storeRepository.findById(storeId)).thenReturn(Optional.of(
			new Store(storeId, "자리요", null, "0212345678", "서울", "Asia/Seoul", StoreStatus.ACTIVE)));
		when(serviceSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
		when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
		mockIdempotent();
		ServiceSessionCommandService service = new ServiceSessionCommandService(serviceSessionRepository,
			walkInEntryRepository, walkInStatusHistoryRepository, reservationRepository,
			reservationStatusHistoryRepository, storeRepository, storeAuthorizationService, idempotencyService,
			auditLogRepository, Clock.fixed(Instant.parse("2026-08-02T05:35:00Z"), ZoneOffset.UTC));

		ServiceSessionCommandService.CompleteServiceResult result = service.completeService(userId, storeId, sessionId,
			"key", new ServiceSessionCommandService.CompleteServiceCommand("정상 완료"));

		assertEquals(sessionId, result.id());
		assertEquals(ReservationStatus.COMPLETED, reservation.getStatus());
		verify(reservationStatusHistoryRepository).save(any());
		verify(auditLogRepository).save(any());
	}

	private void mockIdempotent() {
		doAnswer(invocation -> invocation.<java.util.function.Supplier<?>>getArgument(5).get())
			.when(idempotencyService)
			.execute(any(), any(), any(), any(), any(), any());
	}

	private StoreMember storeMember(UUID storeId) {
		return new StoreMember(UUID.randomUUID(), storeId, new UserAccount("staff@example.com", "+821012345678", "hash"),
			StoreMemberRole.STAFF, "민지", true);
	}

	private void setField(Class<?> type, Object target, String name, Object value) throws Exception {
		var field = type.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
