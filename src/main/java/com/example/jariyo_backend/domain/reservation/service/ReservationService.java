package com.example.jariyo_backend.domain.reservation.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.example.jariyo_backend.common.async.AsyncEventRecorder;
import com.example.jariyo_backend.common.async.AsyncEventType;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.common.idempotency.PersistentIdempotencyService;
import com.example.jariyo_backend.domain.reservation.entity.Reservation;
import com.example.jariyo_backend.domain.reservation.entity.ReservationActorType;
import com.example.jariyo_backend.domain.reservation.entity.ReservationSource;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatusHistory;
import com.example.jariyo_backend.domain.reservation.repository.ReservationRepository;
import com.example.jariyo_backend.domain.reservation.repository.ReservationStatusHistoryRepository;
import com.example.jariyo_backend.domain.reservation.service.ReservationBookingService.CustomerBookingCommand;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.entity.StorePolicy;
import com.example.jariyo_backend.domain.store.entity.StoreServiceDefinition;
import com.example.jariyo_backend.domain.store.repository.StorePolicyRepository;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.store.repository.StoreServiceDefinitionRepository;
import com.example.jariyo_backend.domain.user.entity.CustomerProfile;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.repository.CustomerProfileRepository;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import com.example.jariyo_backend.domain.waitlist.service.WaitlistService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {
	private static final EnumSet<ReservationStatus> CANCELLABLE_STATUSES = EnumSet.of(
		ReservationStatus.HELD, ReservationStatus.CONFIRMED);

	private final ReservationRepository reservationRepository;
	private final ReservationStatusHistoryRepository historyRepository;
	private final ReservationBookingService bookingService;
	private final CustomerProfileRepository customerProfileRepository;
	private final StoreRepository storeRepository;
	private final StorePolicyRepository storePolicyRepository;
	private final StoreServiceDefinitionRepository serviceRepository;
	private final StoreMemberRepository storeMemberRepository;
	private final PersistentIdempotencyService idempotencyService;
	private final WaitlistService waitlistService;
	private final AsyncEventRecorder asyncEventRecorder;
	private final Clock clock;

	public ReservationService(ReservationRepository reservationRepository,
		ReservationStatusHistoryRepository historyRepository, ReservationBookingService bookingService,
		CustomerProfileRepository customerProfileRepository, StoreRepository storeRepository,
		StorePolicyRepository storePolicyRepository, StoreServiceDefinitionRepository serviceRepository,
		StoreMemberRepository storeMemberRepository, PersistentIdempotencyService idempotencyService,
		WaitlistService waitlistService, AsyncEventRecorder asyncEventRecorder, Clock clock) {
		this.reservationRepository = reservationRepository;
		this.historyRepository = historyRepository;
		this.bookingService = bookingService;
		this.customerProfileRepository = customerProfileRepository;
		this.storeRepository = storeRepository;
		this.storePolicyRepository = storePolicyRepository;
		this.serviceRepository = serviceRepository;
		this.storeMemberRepository = storeMemberRepository;
		this.idempotencyService = idempotencyService;
		this.waitlistService = waitlistService;
		this.asyncEventRecorder = asyncEventRecorder;
		this.clock = clock;
	}

	@Transactional
	public ReservationCreateResult create(UUID userId, String key, CreateReservationCommand command) {
		requireCustomer(userId);
		return idempotencyService.execute(userId, "reservation:create", key, command,
			ReservationCreateResult.class, () -> createResult(bookingService.bookCustomer(userId,
				new CustomerBookingCommand(command.storeId(), command.serviceId(), command.staffId(), command.startAt(),
					command.partySize(), command.customerNote()))));
	}

	@Transactional(readOnly = true)
	public List<ReservationSummary> listMine(UUID userId, ReservationStatus status, LocalDate from, LocalDate to) {
		requireCustomer(userId);
		if (from != null && to != null && from.isAfter(to)) {
			throw new BusinessException(ErrorCode.BAD_REQUEST);
		}
		List<Reservation> reservations = reservationRepository.findAllByCustomerIdOrderByStartAtDescIdDesc(userId);
		Map<UUID, Store> stores = stores(reservations);
		Map<UUID, StoreServiceDefinition> services = services(reservations);
		Map<UUID, StoreMember> staff = staff(reservations);
		return reservations.stream()
			.filter(reservation -> status == null || reservation.getStatus() == status)
			.filter(reservation -> inDateRange(reservation, stores.get(reservation.getStoreId()), from, to))
			.map(reservation -> summary(reservation, stores.get(reservation.getStoreId()),
				services.get(reservation.getServiceId()), staff.get(reservation.getAssignedStaffId())))
			.toList();
	}

	@Transactional(readOnly = true)
	public ReservationDetail getMine(UUID userId, UUID reservationId) {
		CustomerProfile customer = requireCustomer(userId);
		Reservation reservation = requireOwnedReservation(customer.getId(), reservationId);
		Store store = requireStore(reservation.getStoreId());
		StorePolicy policy = requirePolicy(store.getId());
		return detail(reservation, store, requireService(reservation.getServiceId()),
			findStaff(reservation.getAssignedStaffId()), policy);
	}

	@Transactional(readOnly = true)
	public List<ReservationHistoryResult> historyMine(UUID userId, UUID reservationId) {
		CustomerProfile customer = requireCustomer(userId);
		Reservation reservation = requireOwnedReservation(customer.getId(), reservationId);
		Store store = requireStore(reservation.getStoreId());
		ZoneId zoneId = ZoneId.of(store.getTimezone());
		return historyRepository.findAllByReservationIdOrderByOccurredAtAscIdAsc(reservationId).stream()
			.map(history -> new ReservationHistoryResult(history.getPreviousStatus(), history.getNextStatus(),
				history.getChangedByType(), history.getChangedById(), history.getReasonCode(), history.getNote(),
				atStore(history.getOccurredAt(), zoneId)))
			.toList();
	}

	@Transactional
	public CancelReservationResult cancelMine(UUID userId, UUID reservationId, String key, CancelReservationCommand command) {
		return idempotencyService.execute(userId, "reservation:cancel:" + reservationId, key, command,
			CancelReservationResult.class, () -> {
				CustomerProfile customer = requireCustomer(userId);
				Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
					.orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
				ensureOwned(customer.getId(), reservation);
				if (reservation.getStatus() == ReservationStatus.CANCELLED) {
					throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CANCELLED);
				}
				ReservationStatus previousStatus = reservation.getStatus();
				if (!CANCELLABLE_STATUSES.contains(previousStatus)) {
					throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE);
				}
				Store store = requireStore(reservation.getStoreId());
				StorePolicy policy = requirePolicy(store.getId());
				Instant now = clock.instant();
				if (!canCancel(reservation, policy, now)) {
					throw new BusinessException(ErrorCode.RESERVATION_CANCELLATION_DEADLINE_PASSED);
				}
				reservation.cancelByCustomer(command.reason(), now, customer.getUserId());
				historyRepository.save(new ReservationStatusHistory(reservation.getId(), previousStatus,
					ReservationStatus.CANCELLED, ReservationActorType.CUSTOMER, userId, "CUSTOMER_CANCELLED",
					command.reason(), now));
				waitlistService.offerCancelledReservation(reservation, now);
				asyncEventRecorder.record(AsyncEventType.RESERVATION_CANCELLED, reservation.getStoreId(), "RESERVATION",
					reservation.getId(), new ReservationCancelledPayload(reservation.getId(), reservation.getStoreId(),
						customer.getId(), reservation.getStatus(), reservation.getCancelledAt(),
						reservation.getCancelledByType()));
				return new CancelReservationResult(reservation.getId(), reservation.getStatus(),
					atStore(reservation.getCancelledAt(), ZoneId.of(store.getTimezone())), reservation.getCancelledByType());
			});
	}

	private ReservationCreateResult createResult(Reservation reservation) {
		Store store = requireStore(reservation.getStoreId());
		StoreServiceDefinition service = requireService(reservation.getServiceId());
		StoreMember staff = requireStaff(reservation.getAssignedStaffId());
		ZoneId zoneId = ZoneId.of(store.getTimezone());
		return new ReservationCreateResult(reservation.getId(), new NamedRef(store.getId(), store.getName()),
			new NamedRef(service.getId(), service.getName()), new NamedRef(staff.getId(), staff.getDisplayName()),
			reservation.getSource(), reservation.getStatus(), atStore(reservation.getStartAt(), zoneId),
			atStore(reservation.getServiceEndAt(), zoneId), atStore(reservation.getOccupiedUntil(), zoneId),
			atStore(reservation.getCreatedAt(), zoneId));
	}

	private ReservationSummary summary(Reservation reservation, Store store, StoreServiceDefinition service,
		StoreMember staff) {
		ZoneId zoneId = ZoneId.of(store.getTimezone());
		return new ReservationSummary(reservation.getId(), new NamedRef(store.getId(), store.getName()),
			new NamedRef(service.getId(), service.getName()), staffRef(staff),
			reservation.getStatus(), atStore(reservation.getStartAt(), zoneId),
			atStore(reservation.getServiceEndAt(), zoneId));
	}

	private ReservationDetail detail(Reservation reservation, Store store, StoreServiceDefinition service,
		StoreMember staff, StorePolicy policy) {
		ZoneId zoneId = ZoneId.of(store.getTimezone());
		Instant deadline = cancellationDeadline(reservation, policy);
		return new ReservationDetail(reservation.getId(),
			new StoreRef(store.getId(), store.getName(), store.getPhoneNumber(), store.getAddress()),
			new NamedRef(service.getId(), service.getName()), staffRef(staff),
			reservation.getSource(), reservation.getStatus(), atStore(reservation.getStartAt(), zoneId),
			atStore(reservation.getServiceEndAt(), zoneId), atStore(reservation.getOccupiedUntil(), zoneId),
			reservation.getPartySize(), reservation.getCustomerNote(), false,
			canCancel(reservation, policy, clock.instant()), atStore(deadline, zoneId),
			atStore(reservation.getCreatedAt(), zoneId));
	}

	private boolean canCancel(Reservation reservation, StorePolicy policy, Instant now) {
		return CANCELLABLE_STATUSES.contains(reservation.getStatus())
			&& (reservation.getStatus() == ReservationStatus.HELD
				|| !now.isAfter(cancellationDeadline(reservation, policy)));
	}

	private Instant cancellationDeadline(Reservation reservation, StorePolicy policy) {
		return reservation.getStartAt().minusSeconds(policy.getCancellationDeadlineMinutes() * 60L);
	}

	private boolean inDateRange(Reservation reservation, Store store, LocalDate from, LocalDate to) {
		LocalDate date = reservation.getStartAt().atZone(ZoneId.of(store.getTimezone())).toLocalDate();
		return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
	}

	private Reservation requireOwnedReservation(UUID customerId, UUID reservationId) {
		Reservation reservation = reservationRepository.findById(reservationId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
		ensureOwned(customerId, reservation);
		return reservation;
	}

	private void ensureOwned(UUID customerId, Reservation reservation) {
		if (!reservation.belongsToCustomer(customerId)) {
			throw new BusinessException(ErrorCode.RESERVATION_NOT_OWNED_BY_USER);
		}
	}

	private CustomerProfile requireCustomer(UUID userId) {
		return customerProfileRepository.findByUser_Id(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
	}

	private Store requireStore(UUID storeId) {
		return storeRepository.findById(storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
	}

	private StorePolicy requirePolicy(UUID storeId) {
		return storePolicyRepository.findByStoreId(storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_POLICY_NOT_FOUND));
	}

	private StoreServiceDefinition requireService(UUID serviceId) {
		return serviceRepository.findById(serviceId)
			.orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_NOT_FOUND));
	}

	private StoreMember requireStaff(UUID staffId) {
		return storeMemberRepository.findById(staffId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));
	}

	private StoreMember findStaff(UUID staffId) {
		return staffId == null ? null : storeMemberRepository.findById(staffId).orElse(null);
	}

	private NamedRef staffRef(StoreMember staff) {
		return staff == null ? null : new NamedRef(staff.getId(), staff.getDisplayName());
	}

	private Map<UUID, Store> stores(List<Reservation> reservations) {
		return storeRepository.findAllById(reservations.stream().map(Reservation::getStoreId).collect(Collectors.toSet()))
			.stream().collect(Collectors.toMap(Store::getId, Function.identity()));
	}

	private Map<UUID, StoreServiceDefinition> services(List<Reservation> reservations) {
		return serviceRepository.findAllById(reservations.stream().map(Reservation::getServiceId).collect(Collectors.toSet()))
			.stream().collect(Collectors.toMap(StoreServiceDefinition::getId, Function.identity()));
	}

	private Map<UUID, StoreMember> staff(List<Reservation> reservations) {
		return storeMemberRepository.findAllById(reservations.stream().map(Reservation::getAssignedStaffId)
			.filter(java.util.Objects::nonNull)
			.collect(Collectors.toSet())).stream().collect(Collectors.toMap(StoreMember::getId, Function.identity()));
	}

	private OffsetDateTime atStore(Instant instant, ZoneId zoneId) {
		return instant == null ? null : instant.atZone(zoneId).toOffsetDateTime();
	}

	public record CreateReservationCommand(UUID storeId, UUID serviceId, UUID staffId, OffsetDateTime startAt,
		int partySize, String customerNote) { }

	public record CancelReservationCommand(String reason) { }

	public record NamedRef(UUID id, String name) { }

	public record StoreRef(UUID id, String name, String phoneNumber, String address) { }

	public record ReservationCreateResult(UUID id, NamedRef store, NamedRef service, NamedRef staff,
		ReservationSource source, ReservationStatus status, OffsetDateTime startAt, OffsetDateTime serviceEndAt,
		OffsetDateTime occupiedUntil, OffsetDateTime createdAt) { }

	public record ReservationSummary(UUID id, NamedRef store, NamedRef service, NamedRef staff,
		ReservationStatus status, OffsetDateTime startAt, OffsetDateTime serviceEndAt) { }

	public record ReservationDetail(UUID id, StoreRef store, NamedRef service, NamedRef staff,
		ReservationSource source, ReservationStatus status, OffsetDateTime startAt, OffsetDateTime serviceEndAt,
		OffsetDateTime occupiedUntil, int partySize, String customerNote, boolean checkInAvailable, boolean canCancel,
		OffsetDateTime cancellationDeadlineAt, OffsetDateTime createdAt) { }

	public record ReservationHistoryResult(ReservationStatus previousStatus, ReservationStatus nextStatus,
		ReservationActorType changedByType, UUID changedById, String reasonCode, String note,
		OffsetDateTime occurredAt) { }

	public record CancelReservationResult(UUID id, ReservationStatus status, OffsetDateTime cancelledAt,
		String cancelledByType) { }

	private record ReservationCancelledPayload(UUID reservationId, UUID storeId, UUID customerId,
		ReservationStatus status, Instant cancelledAt, String cancelledByType) { }
}
