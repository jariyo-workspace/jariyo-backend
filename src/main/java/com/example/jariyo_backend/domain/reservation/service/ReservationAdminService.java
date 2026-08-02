package com.example.jariyo_backend.domain.reservation.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.common.idempotency.PersistentIdempotencyService;
import com.example.jariyo_backend.domain.admin.entity.AuditActorType;
import com.example.jariyo_backend.domain.admin.entity.AuditLog;
import com.example.jariyo_backend.domain.admin.repository.AuditLogRepository;
import com.example.jariyo_backend.domain.reservation.entity.Reservation;
import com.example.jariyo_backend.domain.reservation.entity.ReservationActorType;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatusHistory;
import com.example.jariyo_backend.domain.reservation.repository.ReservationRepository;
import com.example.jariyo_backend.domain.reservation.repository.ReservationStatusHistoryRepository;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.entity.StorePolicy;
import com.example.jariyo_backend.domain.store.repository.StaffServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StorePolicyRepository;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.service.StoreAuthorizationService;
import com.example.jariyo_backend.domain.walkin.entity.ServiceSession;
import com.example.jariyo_backend.domain.walkin.entity.ServiceSessionStatus;
import com.example.jariyo_backend.domain.walkin.repository.ServiceSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationAdminService {
	private final ReservationRepository reservationRepository;
	private final ReservationStatusHistoryRepository historyRepository;
	private final StoreRepository storeRepository;
	private final StorePolicyRepository storePolicyRepository;
	private final StaffServiceRepository staffServiceRepository;
	private final ServiceSessionRepository serviceSessionRepository;
	private final StoreAuthorizationService storeAuthorizationService;
	private final PersistentIdempotencyService idempotencyService;
	private final AuditLogRepository auditLogRepository;
	private final Clock clock;

	public ReservationAdminService(ReservationRepository reservationRepository,
		ReservationStatusHistoryRepository historyRepository, StoreRepository storeRepository,
		StorePolicyRepository storePolicyRepository, StaffServiceRepository staffServiceRepository,
		ServiceSessionRepository serviceSessionRepository, StoreAuthorizationService storeAuthorizationService,
		PersistentIdempotencyService idempotencyService, AuditLogRepository auditLogRepository, Clock clock) {
		this.reservationRepository = reservationRepository;
		this.historyRepository = historyRepository;
		this.storeRepository = storeRepository;
		this.storePolicyRepository = storePolicyRepository;
		this.staffServiceRepository = staffServiceRepository;
		this.serviceSessionRepository = serviceSessionRepository;
		this.storeAuthorizationService = storeAuthorizationService;
		this.idempotencyService = idempotencyService;
		this.auditLogRepository = auditLogRepository;
		this.clock = clock;
	}

	@Transactional
	public ReservationCheckInResult checkIn(UUID userId, UUID storeId, UUID reservationId, String key) {
		return idempotencyService.execute(userId, "admin-reservation:check-in:" + reservationId, key, reservationId,
			ReservationCheckInResult.class, () -> {
				StoreMember actor = storeAuthorizationService.requireStaff(userId, storeId);
				Store store = requireStore(storeId);
				Reservation reservation = requireStoreReservation(storeId, reservationId);
				StorePolicy policy = requirePolicy(storeId);
				Instant now = clock.instant();
				if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
					throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE);
				}
				if (!isCheckInWindowOpen(reservation, policy, now)) {
					throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE);
				}
				ReservationStatus previous = reservation.getStatus();
				reservation.checkIn(now);
				historyRepository.save(new ReservationStatusHistory(reservationId, previous, ReservationStatus.CHECKED_IN,
					ReservationActorType.STORE_MEMBER, actor.getId(), "STAFF_CHECKED_IN", null, now));
				recordAudit(storeId, actor.getId(), "RESERVATION_CHECKED_IN", reservationId, null, previous,
					ReservationStatus.CHECKED_IN, key, now);
				return new ReservationCheckInResult(reservationId, ReservationStatus.CHECKED_IN, atStore(now, store));
			});
	}

	@Transactional
	public ReservationNoShowResult markNoShow(UUID userId, UUID storeId, UUID reservationId, String key,
		ReservationNoShowCommand command) {
		return idempotencyService.execute(userId, "admin-reservation:no-show:" + reservationId, key, command,
			ReservationNoShowResult.class, () -> {
				StoreMember actor = storeAuthorizationService.requireStaff(userId, storeId);
				Store store = requireStore(storeId);
				Reservation reservation = requireStoreReservation(storeId, reservationId);
				StorePolicy policy = requirePolicy(storeId);
				Instant now = clock.instant();
				if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
					throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE);
				}
				if (!isNoShowCandidate(reservation, policy, now)) {
					throw new BusinessException(ErrorCode.RESERVATION_NOT_NO_SHOW_CANDIDATE);
				}
				ReservationStatus previous = reservation.getStatus();
				reservation.markNoShow(now);
				historyRepository.save(new ReservationStatusHistory(reservationId, previous, ReservationStatus.NO_SHOW,
					ReservationActorType.STORE_MEMBER, actor.getId(), "MARKED_NO_SHOW", command.reason(), now));
				recordAudit(storeId, actor.getId(), "RESERVATION_MARKED_NO_SHOW", reservationId, command.reason(),
					previous, ReservationStatus.NO_SHOW, key, now);
				return new ReservationNoShowResult(reservationId, ReservationStatus.NO_SHOW, atStore(now, store));
			});
	}

	@Transactional
	public StartReservationServiceResult startService(UUID userId, UUID storeId, UUID reservationId, String key,
		StartReservationServiceCommand command) {
		return idempotencyService.execute(userId, "admin-reservation:start-service:" + reservationId, key, command,
			StartReservationServiceResult.class, () -> {
				StoreMember actor = storeAuthorizationService.requireStaff(userId, storeId);
				Store store = requireStore(storeId);
				Reservation reservation = requireStoreReservation(storeId, reservationId);
				if (reservation.getStatus() != ReservationStatus.CHECKED_IN) {
					throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE);
				}
				if (!staffServiceRepository.existsByStoreMemberIdAndServiceIdAndActiveTrue(command.staffId(),
					reservation.getServiceId())) {
					throw new BusinessException(ErrorCode.STAFF_NOT_AVAILABLE);
				}
				if (serviceSessionRepository.findByReservationIdAndStatus(reservationId, ServiceSessionStatus.IN_PROGRESS)
					.isPresent()) {
					throw new BusinessException(ErrorCode.SERVICE_SESSION_ALREADY_STARTED);
				}
				Instant now = clock.instant();
				ReservationStatus previous = reservation.getStatus();
				reservation.startService(now);
				ServiceSession session = serviceSessionRepository.save(ServiceSession.forReservation(storeId,
					reservation.getCustomerId(), reservationId, reservation.getServiceId(), command.staffId(), now));
				historyRepository.save(new ReservationStatusHistory(reservationId, previous, ReservationStatus.IN_SERVICE,
					ReservationActorType.STORE_MEMBER, actor.getId(), "SERVICE_STARTED", null, now));
				recordAudit(storeId, actor.getId(), "RESERVATION_SERVICE_STARTED", reservationId, null, previous,
					ReservationStatus.IN_SERVICE, key, now);
				return StartReservationServiceResult.from(session, ZoneId.of(store.getTimezone()));
			});
	}

	private Reservation requireStoreReservation(UUID storeId, UUID reservationId) {
		Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
		if (!storeId.equals(reservation.getStoreId())) {
			throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
		}
		return reservation;
	}

	private Store requireStore(UUID storeId) {
		return storeRepository.findById(storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
	}

	private StorePolicy requirePolicy(UUID storeId) {
		return storePolicyRepository.findByStoreId(storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_POLICY_NOT_FOUND));
	}

	private boolean isCheckInWindowOpen(Reservation reservation, StorePolicy policy, Instant now) {
		Instant availableFrom = reservation.getStartAt().minusSeconds(policy.getCheckInOpenBeforeMinutes() * 60L);
		Instant availableUntil = reservation.getStartAt().plusSeconds(policy.getLateToleranceMinutes() * 60L);
		return !now.isBefore(availableFrom) && !now.isAfter(availableUntil);
	}

	private boolean isNoShowCandidate(Reservation reservation, StorePolicy policy, Instant now) {
		Instant noShowAt = reservation.getStartAt().plusSeconds(policy.getNoShowAfterMinutes() * 60L);
		return !now.isBefore(noShowAt);
	}

	private void recordAudit(UUID storeId, UUID actorId, String action, UUID reservationId, String reason,
		ReservationStatus previousStatus, ReservationStatus nextStatus, String requestId, Instant occurredAt) {
		auditLogRepository.save(new AuditLog(storeId, AuditActorType.STORE_MEMBER, actorId, action, "RESERVATION",
			reservationId, reason, previousStatus == null ? null : previousStatus.name(), nextStatus.name(), requestId,
			occurredAt));
	}

	private OffsetDateTime atStore(Instant instant, Store store) {
		return instant.atZone(ZoneId.of(store.getTimezone())).toOffsetDateTime();
	}

	public record ReservationNoShowCommand(String reason) { }

	public record StartReservationServiceCommand(UUID staffId) { }

	public record ReservationCheckInResult(UUID reservationId, ReservationStatus status, OffsetDateTime checkedInAt) { }

	public record ReservationNoShowResult(UUID reservationId, ReservationStatus status, OffsetDateTime markedAt) { }

	public record StartReservationServiceResult(UUID serviceSessionId, UUID reservationId, ServiceSessionStatus status,
		OffsetDateTime actualStartAt) {
		private static StartReservationServiceResult from(ServiceSession session, ZoneId zoneId) {
			return new StartReservationServiceResult(session.getId(), session.getReservationId(), session.getStatus(),
				session.getActualStartAt().atZone(zoneId).toOffsetDateTime());
		}
	}
}
