package com.example.jariyo_backend.domain.reservation.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.common.async.AsyncEventRecorder;
import com.example.jariyo_backend.common.async.AsyncEventType;
import com.example.jariyo_backend.domain.reservation.entity.Reservation;
import com.example.jariyo_backend.domain.reservation.entity.ReservationSource;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.repository.ReservationRepository;
import com.example.jariyo_backend.domain.reservation.repository.ReservationStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationHoldExpirationServiceTests {
	private static final Instant NOW = Instant.parse("2026-07-26T00:10:00Z");

	@Mock ReservationRepository reservationRepository;
	@Mock ReservationStatusHistoryRepository historyRepository;
	@Mock AsyncEventRecorder asyncEventRecorder;

	@Test
	void expiresDueHoldAndRecordsHistoryAndEvent() throws Exception {
		Reservation held = held("2026-07-26T00:09:59Z");
		when(reservationRepository.findExpiredHoldIds(ReservationStatus.HELD, NOW)).thenReturn(List.of(held.getId()));
		when(reservationRepository.findByIdForUpdate(held.getId())).thenReturn(Optional.of(held));
		ReservationHoldExpirationService service = service();

		service.expireHolds();

		assertEquals(ReservationStatus.EXPIRED, held.getStatus());
		verify(historyRepository).save(any());
		verify(asyncEventRecorder).record(eq(AsyncEventType.RESERVATION_HOLD_EXPIRED), eq(held.getStoreId()),
			eq("RESERVATION"), eq(held.getId()), any());
	}

	@Test
	void skipsCandidateWhenConfirmationWonTheRowLock() throws Exception {
		Reservation candidate = held("2026-07-26T00:09:59Z");
		Reservation confirmed = Reservation.confirmed(candidate.getStoreId(), candidate.getCustomerId(),
			candidate.getServiceId(), candidate.getAssignedStaffId(), ReservationSource.CUSTOMER_BOOKING,
			candidate.getStartAt(), candidate.getServiceEndAt(), candidate.getOccupiedUntil(), 1, null,
			Instant.parse("2026-07-26T00:09:58Z"));
		setField(confirmed, "id", candidate.getId());
		when(reservationRepository.findExpiredHoldIds(ReservationStatus.HELD, NOW))
			.thenReturn(List.of(candidate.getId()));
		when(reservationRepository.findByIdForUpdate(candidate.getId())).thenReturn(Optional.of(confirmed));
		ReservationHoldExpirationService service = service();

		service.expireHolds();

		assertEquals(ReservationStatus.CONFIRMED, confirmed.getStatus());
		verify(historyRepository, never()).save(any());
		verify(asyncEventRecorder, never()).record(any(), any(), any(), any(), any());
	}

	private ReservationHoldExpirationService service() {
		return new ReservationHoldExpirationService(reservationRepository, historyRepository, asyncEventRecorder,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private Reservation held(String expiresAt) throws Exception {
		Reservation held = Reservation.held(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
			ReservationSource.CUSTOMER_BOOKING, Instant.parse("2026-07-27T05:00:00Z"),
			Instant.parse("2026-07-27T05:30:00Z"), Instant.parse("2026-07-27T05:40:00Z"), 1, null,
			Instant.parse(expiresAt));
		setField(held, "id", UUID.randomUUID());
		return held;
	}

	private void setField(Object target, String name, Object value) throws Exception {
		var field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
