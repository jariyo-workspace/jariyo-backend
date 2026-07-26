package com.example.jariyo_backend.domain.reservation.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.UUID;
import com.example.jariyo_backend.common.async.AsyncEventRecorder;
import com.example.jariyo_backend.common.async.AsyncEventType;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.common.idempotency.PersistentIdempotencyService;
import com.example.jariyo_backend.domain.reservation.entity.Reservation;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.repository.ReservationRepository;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.entity.StorePolicy;
import com.example.jariyo_backend.domain.store.repository.StorePolicyRepository;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.user.entity.CustomerProfile;
import com.example.jariyo_backend.domain.user.repository.CustomerProfileRepository;
import com.example.jariyo_backend.domain.waitlist.service.WaitlistService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {
	private final ReservationRepository reservationRepository;
	private final CustomerProfileRepository customerProfileRepository;
	private final StoreRepository storeRepository;
	private final StorePolicyRepository storePolicyRepository;
	private final PersistentIdempotencyService idempotencyService;
	private final WaitlistService waitlistService;
	private final AsyncEventRecorder asyncEventRecorder;
	private final Clock clock;

	public ReservationService(ReservationRepository reservationRepository,
		CustomerProfileRepository customerProfileRepository, StoreRepository storeRepository,
		StorePolicyRepository storePolicyRepository, PersistentIdempotencyService idempotencyService,
		WaitlistService waitlistService, AsyncEventRecorder asyncEventRecorder, Clock clock) {
		this.reservationRepository = reservationRepository;
		this.customerProfileRepository = customerProfileRepository;
		this.storeRepository = storeRepository;
		this.storePolicyRepository = storePolicyRepository;
		this.idempotencyService = idempotencyService;
		this.waitlistService = waitlistService;
		this.asyncEventRecorder = asyncEventRecorder;
		this.clock = clock;
	}

	@Transactional
	public CancelReservationResult cancelMine(UUID userId, UUID reservationId, String key, CancelReservationCommand command) {
		return idempotencyService.execute(userId, "reservation:cancel:" + reservationId, key, command,
			CancelReservationResult.class, () -> {
				CustomerProfile customer = requireCustomer(userId);
				Reservation reservation = requireReservationForCancellation(customer.getId(), reservationId);
				Store store = requireStore(reservation.getStoreId());
				StorePolicy policy = requireStorePolicy(store.getId());
				Instant now = clock.instant();
				validateCancellationDeadline(reservation, store, policy, now);
				reservation.cancelByCustomer(cancellationReason(command), now, customer.getUserId());
				waitlistService.offerCancelledReservation(reservation, now);
				asyncEventRecorder.record(AsyncEventType.RESERVATION_CANCELLED, reservation.getStoreId(), "RESERVATION",
					reservation.getId(), new ReservationCancelledPayload(reservation.getId(), reservation.getStoreId(),
						customer.getId(), reservation.getStatus(), reservation.getCancelledAt(),
						reservation.getCancelledByType()));
				return new CancelReservationResult(reservation.getId(), reservation.getStatus(),
					reservation.getCancelledAt(), reservation.getCancelledByType());
			});
	}

	private CustomerProfile requireCustomer(UUID userId) {
		return customerProfileRepository.findByUser_Id(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
	}

	private Reservation requireReservationForCancellation(UUID customerId, UUID reservationId) {
		Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
		if (!reservation.belongsToCustomer(customerId)) {
			throw new BusinessException(ErrorCode.RESERVATION_NOT_OWNED_BY_USER);
		}
		if (reservation.isCancelled()) {
			throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CANCELLED);
		}
		if (!reservation.canBeCancelled()) {
			throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE);
		}
		return reservation;
	}

	private Store requireStore(UUID storeId) {
		return storeRepository.findById(storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
	}

	private StorePolicy requireStorePolicy(UUID storeId) {
		return storePolicyRepository.findByStoreId(storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_POLICY_NOT_FOUND));
	}

	private void validateCancellationDeadline(Reservation reservation, Store store, StorePolicy policy, Instant now) {
		if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
			return;
		}
		ZonedDateTime startAt = ZonedDateTime.ofInstant(reservation.getStartAt(), ZoneId.of(store.getTimezone()));
		ZonedDateTime deadline = startAt.minusMinutes(policy.getCancellationDeadlineMinutes());
		if (now.isAfter(deadline.toInstant())) {
			throw new BusinessException(ErrorCode.RESERVATION_CANCELLATION_DEADLINE_PASSED);
		}
	}

	private String cancellationReason(CancelReservationCommand command) {
		return command.reasonCode() + ": " + command.reason();
	}

	public record CancelReservationCommand(String reasonCode, String reason) { }

	public record CancelReservationResult(UUID id, ReservationStatus status, Instant cancelledAt,
		String cancelledByType) { }

	private record ReservationCancelledPayload(UUID reservationId, UUID storeId, UUID customerId,
		ReservationStatus status, Instant cancelledAt, String cancelledByType) { }
}
