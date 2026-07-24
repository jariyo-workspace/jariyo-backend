package com.example.jariyo_backend.domain.reservation.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.UUID;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.domain.availability.dto.AvailabilitySlotResponse;
import com.example.jariyo_backend.domain.availability.service.AvailabilityService;
import com.example.jariyo_backend.domain.reservation.entity.Reservation;
import com.example.jariyo_backend.domain.reservation.entity.ReservationActorType;
import com.example.jariyo_backend.domain.reservation.entity.ReservationSource;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatusHistory;
import com.example.jariyo_backend.domain.reservation.repository.ReservationRepository;
import com.example.jariyo_backend.domain.reservation.repository.ReservationStatusHistoryRepository;
import com.example.jariyo_backend.domain.store.entity.ServiceStatus;
import com.example.jariyo_backend.domain.store.entity.StaffService;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.entity.StorePolicy;
import com.example.jariyo_backend.domain.store.entity.StoreServiceDefinition;
import com.example.jariyo_backend.domain.store.entity.StoreStatus;
import com.example.jariyo_backend.domain.store.repository.StaffServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StorePolicyRepository;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.store.repository.StoreServiceDefinitionRepository;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.entity.StoreMemberStatus;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationBookingService {
	private static final EnumSet<ReservationStatus> ACTIVE_STATUSES = EnumSet.of(
		ReservationStatus.HELD, ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN,
		ReservationStatus.IN_SERVICE);

	private final ReservationRepository reservationRepository;
	private final ReservationStatusHistoryRepository historyRepository;
	private final StoreRepository storeRepository;
	private final StorePolicyRepository storePolicyRepository;
	private final StoreServiceDefinitionRepository serviceRepository;
	private final StoreMemberRepository storeMemberRepository;
	private final StaffServiceRepository staffServiceRepository;
	private final AvailabilityService availabilityService;
	private final EntityManager entityManager;
	private final Clock clock;

	public ReservationBookingService(ReservationRepository reservationRepository,
		ReservationStatusHistoryRepository historyRepository, StoreRepository storeRepository,
		StorePolicyRepository storePolicyRepository, StoreServiceDefinitionRepository serviceRepository,
		StoreMemberRepository storeMemberRepository, StaffServiceRepository staffServiceRepository,
		AvailabilityService availabilityService, EntityManager entityManager, Clock clock) {
		this.reservationRepository = reservationRepository;
		this.historyRepository = historyRepository;
		this.storeRepository = storeRepository;
		this.storePolicyRepository = storePolicyRepository;
		this.serviceRepository = serviceRepository;
		this.storeMemberRepository = storeMemberRepository;
		this.staffServiceRepository = staffServiceRepository;
		this.availabilityService = availabilityService;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Reservation bookCustomer(UUID customerId, CustomerBookingCommand command) {
		Store store = requireActiveStore(command.storeId());
		StorePolicy policy = requirePolicy(command.storeId());
		StoreServiceDefinition service = requireActiveService(command.storeId(), command.serviceId());
		StoreMember staff = requireAvailableStaff(command.storeId(), command.serviceId(), command.staffId());
		validatePartySize(command.partySize(), service);
		validateBookingTime(command.startAt().toInstant(), store, policy);

		ZoneId zoneId = ZoneId.of(store.getTimezone());
		LocalDate date = command.startAt().atZoneSameInstant(zoneId).toLocalDate();
		AvailabilitySlotResponse slot = availabilityService
			.getAvailability(command.storeId(), command.serviceId(), command.staffId(), date, date, command.partySize())
			.dates().stream()
			.flatMap(value -> value.slots().stream())
			.filter(value -> value.staffId().equals(staff.getId()))
			.filter(value -> value.startAt().toInstant().equals(command.startAt().toInstant()))
			.findFirst()
			.orElseGet(() -> unavailableSlot(command, service));

		return createConfirmed(new ConfirmedBooking(command.storeId(), customerId, command.serviceId(),
			command.staffId(), ReservationSource.CUSTOMER_BOOKING, slot.startAt().toInstant(),
			slot.serviceEndAt().toInstant(), slot.occupiedUntil().toInstant(), command.partySize(),
			command.customerNote(), ErrorCode.RESERVATION_SLOT_ALREADY_TAKEN,
			ErrorCode.CUSTOMER_HAS_OVERLAPPING_RESERVATION));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Reservation bookFromWaitlist(UUID customerId, UUID storeId, UUID serviceId, UUID staffId, Instant startAt,
		Instant serviceEndAt, Instant occupiedUntil, int partySize) {
		return createConfirmed(new ConfirmedBooking(storeId, customerId, serviceId, staffId,
			ReservationSource.WAITLIST_OFFER, startAt, serviceEndAt, occupiedUntil, partySize, null,
			ErrorCode.SLOT_OFFER_NO_LONGER_AVAILABLE, ErrorCode.SLOT_OFFER_NO_LONGER_AVAILABLE));
	}

	private Reservation createConfirmed(ConfirmedBooking booking) {
		lock("reservation:staff:" + booking.staffId());
		lock("reservation:customer:" + booking.customerId());
		if (reservationRepository.existsOverlappingReservation(booking.storeId(), booking.staffId(), ACTIVE_STATUSES,
			booking.startAt(), booking.occupiedUntil())) {
			throw new BusinessException(booking.staffConflictCode());
		}
		if (reservationRepository.existsOverlappingCustomerReservation(booking.customerId(), ACTIVE_STATUSES,
			booking.startAt(), booking.occupiedUntil())) {
			throw new BusinessException(booking.customerConflictCode());
		}
		Instant now = clock.instant();
		Reservation reservation = reservationRepository.saveAndFlush(Reservation.confirmed(
			booking.storeId(), booking.customerId(), booking.serviceId(), booking.staffId(), booking.source(),
			booking.startAt(), booking.serviceEndAt(), booking.occupiedUntil(), booking.partySize(),
			booking.customerNote(), now));
		historyRepository.save(new ReservationStatusHistory(reservation.getId(), null, ReservationStatus.CONFIRMED,
			ReservationActorType.CUSTOMER, booking.customerId(), "CREATED", null, now));
		return reservation;
	}

	private AvailabilitySlotResponse unavailableSlot(CustomerBookingCommand command, StoreServiceDefinition service) {
		StaffService staffService = staffServiceRepository
			.findByStoreMemberIdAndServiceId(command.staffId(), command.serviceId())
			.orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_AVAILABLE));
		int duration = staffService.getCustomDurationMinutes() == null
			? service.getDurationMinutes()
			: staffService.getCustomDurationMinutes();
		Instant startAt = command.startAt().toInstant();
		Instant occupiedUntil = startAt.plusSeconds((duration + service.getCleanupMinutes()) * 60L);
		if (reservationRepository.existsOverlappingReservation(command.storeId(), command.staffId(), ACTIVE_STATUSES,
			startAt, occupiedUntil)) {
			throw new BusinessException(ErrorCode.RESERVATION_SLOT_ALREADY_TAKEN);
		}
		throw new BusinessException(ErrorCode.STAFF_NOT_AVAILABLE);
	}

	private Store requireActiveStore(UUID storeId) {
		Store store = storeRepository.findById(storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
		if (store.getStatus() != StoreStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.STORE_NOT_ACTIVE);
		}
		return store;
	}

	private StorePolicy requirePolicy(UUID storeId) {
		return storePolicyRepository.findByStoreId(storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_POLICY_NOT_FOUND));
	}

	private StoreServiceDefinition requireActiveService(UUID storeId, UUID serviceId) {
		StoreServiceDefinition service = serviceRepository.findById(serviceId)
			.filter(value -> value.getStoreId().equals(storeId))
			.orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_NOT_FOUND));
		if (service.getStatus() != ServiceStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.SERVICE_NOT_ACTIVE);
		}
		return service;
	}

	private StoreMember requireAvailableStaff(UUID storeId, UUID serviceId, UUID staffId) {
		StoreMember staff = storeMemberRepository.findByIdAndStoreId(staffId, storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_AVAILABLE));
		boolean supportsService = staffServiceRepository.findByStoreMemberIdAndServiceId(staffId, serviceId)
			.filter(StaffService::isActive)
			.isPresent();
		if (staff.getStatus() != StoreMemberStatus.ACTIVE || !staff.isBookingEnabled() || !supportsService) {
			throw new BusinessException(ErrorCode.STAFF_NOT_AVAILABLE);
		}
		return staff;
	}

	private void validatePartySize(int partySize, StoreServiceDefinition service) {
		if (partySize < 1 || partySize > service.getCapacity()) {
			throw new BusinessException(ErrorCode.INVALID_PARTY_SIZE);
		}
	}

	private void validateBookingTime(Instant startAt, Store store, StorePolicy policy) {
		Instant now = clock.instant();
		ZoneId zoneId = ZoneId.of(store.getTimezone());
		LocalDate date = startAt.atZone(zoneId).toLocalDate();
		LocalDate today = now.atZone(zoneId).toLocalDate();
		if (date.isBefore(today) || date.isAfter(today.plusDays(policy.getBookingOpenDays()))) {
			throw new BusinessException(ErrorCode.RESERVATION_OUTSIDE_BOOKING_WINDOW);
		}
		if (startAt.isBefore(now.plusSeconds(policy.getMinimumBookingNoticeMinutes() * 60L))) {
			throw new BusinessException(ErrorCode.RESERVATION_TOO_CLOSE_TO_START);
		}
	}

	private void lock(String lockKey) {
		entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:lockKey))")
			.setParameter("lockKey", lockKey)
			.getSingleResult();
	}

	public record CustomerBookingCommand(UUID storeId, UUID serviceId, UUID staffId, OffsetDateTime startAt,
		int partySize, String customerNote) { }

	private record ConfirmedBooking(UUID storeId, UUID customerId, UUID serviceId, UUID staffId,
		ReservationSource source, Instant startAt, Instant serviceEndAt, Instant occupiedUntil, int partySize,
		String customerNote, ErrorCode staffConflictCode, ErrorCode customerConflictCode) { }
}
