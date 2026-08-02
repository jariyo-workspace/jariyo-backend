package com.example.jariyo_backend.domain.admin.service;

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
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.service.StoreAuthorizationService;
import com.example.jariyo_backend.domain.walkin.entity.ServiceSession;
import com.example.jariyo_backend.domain.walkin.entity.ServiceSessionStatus;
import com.example.jariyo_backend.domain.walkin.entity.WalkInActorType;
import com.example.jariyo_backend.domain.walkin.entity.WalkInEntry;
import com.example.jariyo_backend.domain.walkin.entity.WalkInStatus;
import com.example.jariyo_backend.domain.walkin.entity.WalkInStatusHistory;
import com.example.jariyo_backend.domain.walkin.repository.ServiceSessionRepository;
import com.example.jariyo_backend.domain.walkin.repository.WalkInEntryRepository;
import com.example.jariyo_backend.domain.walkin.repository.WalkInStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceSessionCommandService {
	private final ServiceSessionRepository serviceSessionRepository;
	private final WalkInEntryRepository walkInEntryRepository;
	private final WalkInStatusHistoryRepository walkInStatusHistoryRepository;
	private final ReservationRepository reservationRepository;
	private final ReservationStatusHistoryRepository reservationStatusHistoryRepository;
	private final StoreRepository storeRepository;
	private final StoreAuthorizationService storeAuthorizationService;
	private final PersistentIdempotencyService idempotencyService;
	private final AuditLogRepository auditLogRepository;
	private final Clock clock;

	public ServiceSessionCommandService(ServiceSessionRepository serviceSessionRepository,
		WalkInEntryRepository walkInEntryRepository, WalkInStatusHistoryRepository walkInStatusHistoryRepository,
		ReservationRepository reservationRepository,
		ReservationStatusHistoryRepository reservationStatusHistoryRepository, StoreRepository storeRepository,
		StoreAuthorizationService storeAuthorizationService, PersistentIdempotencyService idempotencyService,
		AuditLogRepository auditLogRepository, Clock clock) {
		this.serviceSessionRepository = serviceSessionRepository;
		this.walkInEntryRepository = walkInEntryRepository;
		this.walkInStatusHistoryRepository = walkInStatusHistoryRepository;
		this.reservationRepository = reservationRepository;
		this.reservationStatusHistoryRepository = reservationStatusHistoryRepository;
		this.storeRepository = storeRepository;
		this.storeAuthorizationService = storeAuthorizationService;
		this.idempotencyService = idempotencyService;
		this.auditLogRepository = auditLogRepository;
		this.clock = clock;
	}

	@Transactional
	public CompleteServiceResult completeService(UUID userId, UUID storeId, UUID sessionId, String key,
		CompleteServiceCommand command) {
		return idempotencyService.execute(userId, "service-session:complete:" + sessionId, key, command,
			CompleteServiceResult.class, () -> {
				StoreMember actor = storeAuthorizationService.requireStaff(userId, storeId);
				Store store = requireStore(storeId);
				ServiceSession session = serviceSessionRepository.findByIdForUpdate(sessionId)
					.orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_SESSION_NOT_FOUND));
				if (!storeId.equals(session.getStoreId())) {
					throw new BusinessException(ErrorCode.SERVICE_SESSION_NOT_FOUND);
				}
				Instant now = clock.instant();
				session.complete(now, command.completionNote());
				if (session.getWalkInEntryId() != null) {
					completeWalkIn(actor, storeId, session, now);
				}
				if (session.getReservationId() != null) {
					completeReservation(actor, storeId, session, key, now);
				}
				return CompleteServiceResult.from(session, ZoneId.of(store.getTimezone()));
			});
	}

	private void completeWalkIn(StoreMember actor, UUID storeId, ServiceSession session, Instant now) {
		WalkInEntry entry = walkInEntryRepository.findByIdForUpdate(session.getWalkInEntryId())
			.orElseThrow(() -> new BusinessException(ErrorCode.WALK_IN_NOT_FOUND));
		if (!storeId.equals(entry.getStoreId())) {
			throw new BusinessException(ErrorCode.WALK_IN_NOT_FOUND);
		}
		WalkInStatus previous = entry.getStatus();
		entry.transitionTo(WalkInStatus.COMPLETED, now);
		walkInStatusHistoryRepository.save(new WalkInStatusHistory(entry.getId(), previous, WalkInStatus.COMPLETED,
			WalkInActorType.STORE_MEMBER, actor.getId(), "서비스 완료", now));
	}

	private void completeReservation(StoreMember actor, UUID storeId, ServiceSession session, String key, Instant now) {
		Reservation reservation = reservationRepository.findByIdForUpdate(session.getReservationId())
			.orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
		if (!storeId.equals(reservation.getStoreId())) {
			throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
		}
		ReservationStatus previous = reservation.getStatus();
		if (previous != ReservationStatus.IN_SERVICE) {
			throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE);
		}
		reservation.completeService(now);
		reservationStatusHistoryRepository.save(new ReservationStatusHistory(reservation.getId(), previous,
			ReservationStatus.COMPLETED, ReservationActorType.STORE_MEMBER, actor.getId(), "SERVICE_COMPLETED", null,
			now));
		auditLogRepository.save(new AuditLog(storeId, AuditActorType.STORE_MEMBER, actor.getId(),
			"RESERVATION_SERVICE_COMPLETED", "RESERVATION", reservation.getId(), null, previous.name(),
			ReservationStatus.COMPLETED.name(), key, now));
	}

	private Store requireStore(UUID storeId) {
		return storeRepository.findById(storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
	}

	public record CompleteServiceCommand(String completionNote) { }

	public record CompleteServiceResult(UUID id, ServiceSessionStatus status, OffsetDateTime actualStartAt,
		OffsetDateTime actualEndAt, long actualDurationMinutes) {
		private static CompleteServiceResult from(ServiceSession session, ZoneId zoneId) {
			return new CompleteServiceResult(session.getId(), session.getStatus(),
				session.getActualStartAt().atZone(zoneId).toOffsetDateTime(),
				session.getActualEndAt().atZone(zoneId).toOffsetDateTime(), session.getActualDurationMinutes());
		}
	}
}
