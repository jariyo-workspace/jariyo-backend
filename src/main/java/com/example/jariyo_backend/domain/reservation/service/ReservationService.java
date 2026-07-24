package com.example.jariyo_backend.domain.reservation.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.UUID;
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
	private static final EnumSet<ReservationStatus> CANCELLABLE_STATUSES = EnumSet.of(
		ReservationStatus.HELD, ReservationStatus.CONFIRMED);

	private final ReservationRepository reservationRepository;
	private final CustomerProfileRepository customerProfileRepository;
	private final StoreRepository storeRepository;
	private final StorePolicyRepository storePolicyRepository;
	private final PersistentIdempotencyService idempotencyService;
	private final WaitlistService waitlistService;
	private final Clock clock;

	public ReservationService(ReservationRepository reservationRepository,
		CustomerProfileRepository customerProfileRepository, StoreRepository storeRepository,
		StorePolicyRepository storePolicyRepository, PersistentIdempotencyService idempotencyService,
		WaitlistService waitlistService, Clock clock) {
		this.reservationRepository = reservationRepository;
		this.customerProfileRepository = customerProfileRepository;
		this.storeRepository = storeRepository;
		this.storePolicyRepository = storePolicyRepository;
		this.idempotencyService = idempotencyService;
		this.waitlistService = waitlistService;
		this.clock = clock;
	}

	@Transactional
	public CancelReservationResult cancelMine(UUID userId, UUID reservationId, String key, CancelReservationCommand command) {
		return idempotencyService.execute(userId, "reservation:cancel:" + reservationId, key, command,
			CancelReservationResult.class, () -> {
				CustomerProfile customer = customerProfileRepository.findByUser_Id(userId)
					.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
				Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
					.orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
				if (!customer.getUserId().equals(reservation.getCustomerId())) {
					throw new BusinessException(ErrorCode.RESOURCE_NOT_OWNED_BY_USER);
				}
				if (reservation.getStatus() == ReservationStatus.CANCELLED) {
					throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CANCELLED);
				}
				if (!CANCELLABLE_STATUSES.contains(reservation.getStatus())) {
					throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE);
				}
				Store store = storeRepository.findById(reservation.getStoreId())
					.orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
				StorePolicy policy = storePolicyRepository.findByStoreId(store.getId())
					.orElseThrow(() -> new BusinessException(ErrorCode.STORE_POLICY_NOT_FOUND));
				Instant now = clock.instant();
				if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
					ZonedDateTime startAt = ZonedDateTime.ofInstant(reservation.getStartAt(), ZoneId.of(store.getTimezone()));
					ZonedDateTime deadline = startAt.minusMinutes(policy.getCancellationDeadlineMinutes());
					if (now.isAfter(deadline.toInstant())) {
						throw new BusinessException(ErrorCode.RESERVATION_CANCELLATION_DEADLINE_PASSED);
					}
				}
				reservation.cancelByCustomer(command.reason(), now, customer.getUserId());
				waitlistService.offerCancelledReservation(reservation, now);
				return new CancelReservationResult(reservation.getId(), reservation.getStatus(),
					reservation.getCancelledAt(), reservation.getCancelledByType());
			});
	}

	public record CancelReservationCommand(String reasonCode, String reason) { }

	public record CancelReservationResult(UUID id, ReservationStatus status, Instant cancelledAt,
		String cancelledByType) { }
}
