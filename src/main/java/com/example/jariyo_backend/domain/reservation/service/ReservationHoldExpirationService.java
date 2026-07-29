package com.example.jariyo_backend.domain.reservation.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import com.example.jariyo_backend.common.async.AsyncEventRecorder;
import com.example.jariyo_backend.common.async.AsyncEventType;
import com.example.jariyo_backend.domain.reservation.entity.Reservation;
import com.example.jariyo_backend.domain.reservation.entity.ReservationActorType;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatusHistory;
import com.example.jariyo_backend.domain.reservation.repository.ReservationRepository;
import com.example.jariyo_backend.domain.reservation.repository.ReservationStatusHistoryRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationHoldExpirationService {
	private final ReservationRepository reservationRepository;
	private final ReservationStatusHistoryRepository historyRepository;
	private final AsyncEventRecorder asyncEventRecorder;
	private final Clock clock;

	public ReservationHoldExpirationService(ReservationRepository reservationRepository,
		ReservationStatusHistoryRepository historyRepository, AsyncEventRecorder asyncEventRecorder, Clock clock) {
		this.reservationRepository = reservationRepository;
		this.historyRepository = historyRepository;
		this.asyncEventRecorder = asyncEventRecorder;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${reservation.hold-expiration-scan-delay-ms:30000}")
	@Transactional
	public void expireHolds() {
		Instant now = clock.instant();
		for (UUID reservationId : reservationRepository.findExpiredHoldIds(ReservationStatus.HELD, now)) {
			Reservation reservation = reservationRepository.findByIdForUpdate(reservationId).orElse(null);
			if (reservation == null || !reservation.isHoldExpiredAt(now)) {
				continue;
			}
			reservation.expireHold();
			historyRepository.save(new ReservationStatusHistory(reservation.getId(), ReservationStatus.HELD,
				ReservationStatus.EXPIRED, ReservationActorType.SYSTEM, null, "HOLD_EXPIRED", null, now));
			asyncEventRecorder.record(AsyncEventType.RESERVATION_HOLD_EXPIRED, reservation.getStoreId(),
				"RESERVATION", reservation.getId(), new ReservationHoldExpiredPayload(reservation.getId(),
					reservation.getStoreId(), reservation.getCustomerId(), now));
		}
	}

	private record ReservationHoldExpiredPayload(UUID reservationId, UUID storeId, UUID customerId,
		Instant expiredAt) { }
}
