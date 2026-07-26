package com.example.jariyo_backend.domain.waitlist.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.common.async.AsyncEventRecorder;
import com.example.jariyo_backend.common.async.AsyncEventType;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.common.idempotency.PersistentIdempotencyService;
import com.example.jariyo_backend.domain.reservation.entity.Reservation;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.repository.ReservationRepository;
import com.example.jariyo_backend.domain.store.entity.ServiceOffering;
import com.example.jariyo_backend.domain.store.entity.ServiceStatus;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.entity.StorePolicy;
import com.example.jariyo_backend.domain.store.entity.StoreStatus;
import com.example.jariyo_backend.domain.store.repository.ServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StaffServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StorePolicyRepository;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.user.entity.CustomerProfile;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.entity.StoreMemberStatus;
import com.example.jariyo_backend.domain.user.repository.CustomerProfileRepository;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import com.example.jariyo_backend.domain.waitlist.entity.SlotOffer;
import com.example.jariyo_backend.domain.waitlist.entity.SlotOfferActorType;
import com.example.jariyo_backend.domain.waitlist.entity.SlotOfferStatus;
import com.example.jariyo_backend.domain.waitlist.entity.SlotOfferStatusHistory;
import com.example.jariyo_backend.domain.waitlist.entity.StaffPreferenceType;
import com.example.jariyo_backend.domain.waitlist.entity.WaitlistEntry;
import com.example.jariyo_backend.domain.waitlist.entity.WaitlistStatus;
import com.example.jariyo_backend.domain.waitlist.repository.SlotOfferRepository;
import com.example.jariyo_backend.domain.waitlist.repository.SlotOfferStatusHistoryRepository;
import com.example.jariyo_backend.domain.waitlist.repository.WaitlistEntryRepository;
import jakarta.persistence.EntityManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WaitlistService {
	private static final EnumSet<WaitlistStatus> ACTIVE_WAITLIST_STATUSES = EnumSet.of(
		WaitlistStatus.WAITING, WaitlistStatus.OFFERED);
	private static final EnumSet<ReservationStatus> ACTIVE_RESERVATION_STATUSES = EnumSet.of(
		ReservationStatus.HELD, ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN, ReservationStatus.IN_SERVICE);

	private final WaitlistEntryRepository waitlistEntryRepository;
	private final SlotOfferRepository slotOfferRepository;
	private final SlotOfferStatusHistoryRepository slotOfferStatusHistoryRepository;
	private final CustomerProfileRepository customerProfileRepository;
	private final StoreRepository storeRepository;
	private final StorePolicyRepository storePolicyRepository;
	private final ServiceRepository serviceRepository;
	private final StaffServiceRepository staffServiceRepository;
	private final StoreMemberRepository storeMemberRepository;
	private final ReservationRepository reservationRepository;
	private final PersistentIdempotencyService idempotencyService;
	private final AsyncEventRecorder asyncEventRecorder;
	private final EntityManager entityManager;
	private final Clock clock;

	public WaitlistService(WaitlistEntryRepository waitlistEntryRepository, SlotOfferRepository slotOfferRepository,
		SlotOfferStatusHistoryRepository slotOfferStatusHistoryRepository,
		CustomerProfileRepository customerProfileRepository, StoreRepository storeRepository,
		StorePolicyRepository storePolicyRepository, ServiceRepository serviceRepository,
		StaffServiceRepository staffServiceRepository,
		StoreMemberRepository storeMemberRepository, ReservationRepository reservationRepository,
		PersistentIdempotencyService idempotencyService, AsyncEventRecorder asyncEventRecorder,
		EntityManager entityManager, Clock clock) {
		this.waitlistEntryRepository = waitlistEntryRepository;
		this.slotOfferRepository = slotOfferRepository;
		this.slotOfferStatusHistoryRepository = slotOfferStatusHistoryRepository;
		this.customerProfileRepository = customerProfileRepository;
		this.storeRepository = storeRepository;
		this.storePolicyRepository = storePolicyRepository;
		this.serviceRepository = serviceRepository;
		this.staffServiceRepository = staffServiceRepository;
		this.storeMemberRepository = storeMemberRepository;
		this.reservationRepository = reservationRepository;
		this.idempotencyService = idempotencyService;
		this.asyncEventRecorder = asyncEventRecorder;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	@Transactional
	public WaitlistSummary create(UUID userId, String key, CreateWaitlistCommand command) {
		return idempotencyService.execute(userId, "waitlist:create", key, command, WaitlistSummary.class, () -> {
			CustomerProfile customer = requireCustomer(userId);
			Store store = requireStore(command.storeId());
			StorePolicy policy = requirePolicy(command.storeId());
			ServiceOffering service = requireActiveService(command.storeId(), command.serviceId());
			validateCreateCommand(store, policy, service, command);
			if (command.preferredStaffId() != null) {
				requireStaffForService(command.storeId(), command.serviceId(), command.preferredStaffId());
			}
			List<WaitlistEntry> duplicates = waitlistEntryRepository.findDuplicates(customer.getId(), command.storeId(),
				command.serviceId(), command.desiredDate(), ACTIVE_WAITLIST_STATUSES);
			if (!duplicates.isEmpty()) throw new BusinessException(ErrorCode.WAITLIST_DUPLICATED);
			int sequenceNumber = issueSequence(command.storeId());
			Instant expiresAt = expiresAt(store, command.desiredDate(), command.acceptableEndTime());
			WaitlistEntry entry = waitlistEntryRepository.save(new WaitlistEntry(command.storeId(), customer.getId(),
				command.serviceId(), command.preferredStaffId(), command.staffPreferenceType(), command.desiredDate(),
				command.acceptableStartTime(), command.acceptableEndTime(), command.partySize(), sequenceNumber,
				expiresAt));
			return summary(entry);
		});
	}

	@Transactional
	public List<WaitlistSummary> listMine(UUID userId, WaitlistStatus status, LocalDate from, LocalDate to) {
		expirePendingOffers();
		expireStaleWaitlists();
		CustomerProfile customer = requireCustomer(userId);
		return waitlistEntryRepository.findAllByCustomerIdOrderByCreatedAtDesc(customer.getId()).stream()
			.filter(entry -> status == null || entry.getStatus() == status)
			.filter(entry -> from == null || !entry.getDesiredDate().isBefore(from))
			.filter(entry -> to == null || !entry.getDesiredDate().isAfter(to))
			.map(this::summary)
			.toList();
	}

	@Transactional
	public WaitlistDetail getMine(UUID userId, UUID waitlistId) {
		expirePendingOffers();
		expireStaleWaitlists();
		CustomerProfile customer = requireCustomer(userId);
		WaitlistEntry entry = requireOwnedWaitlist(customer.getId(), waitlistId);
		return detail(entry);
	}

	@Transactional
	public WaitlistCancelResult cancelMine(UUID userId, UUID waitlistId, String key, CancelWaitlistCommand command) {
		return idempotencyService.execute(userId, "waitlist:cancel:" + waitlistId, key, command,
			WaitlistCancelResult.class, () -> {
				CustomerProfile customer = requireCustomer(userId);
				WaitlistEntry entry = requireOwnedWaitlistForUpdate(customer.getId(), waitlistId);
				if (!entry.canBeCancelled()) {
					throw new BusinessException(ErrorCode.WAITLIST_INVALID_STATE);
				}
				Instant now = clock.instant();
				if (entry.isOffered()) {
					for (SlotOffer offer : slotOfferRepository.findAllByWaitlistEntryIdAndStatus(entry.getId(), SlotOfferStatus.PENDING)) {
						revokeOffer(offer, SlotOfferActorType.CUSTOMER, customer.getId(), "WAITLIST_CANCELLED");
					}
				}
				entry.markCancelled(now);
				return new WaitlistCancelResult(entry.getId(), entry.getStatus(), entry.getCancelledAt());
			});
	}

	@Transactional
	public List<SlotOfferSummary> listOffers(UUID userId, SlotOfferStatus status) {
		expirePendingOffers();
		expireStaleWaitlists();
		CustomerProfile customer = requireCustomer(userId);
		return slotOfferRepository.findMine(customer.getId(), status).stream()
			.map(this::slotOfferSummary)
			.toList();
	}

	@Transactional
	public SlotOfferDetail getOffer(UUID userId, UUID offerId) {
		expirePendingOffers();
		expireStaleWaitlists();
		CustomerProfile customer = requireCustomer(userId);
		SlotOffer offer = slotOfferRepository.findById(offerId)
			.orElseThrow(() -> new BusinessException(ErrorCode.SLOT_OFFER_NOT_FOUND));
		WaitlistEntry entry = requireOwnedWaitlist(customer.getId(), offer.getWaitlistEntryId());
		return slotOfferDetail(offer, entry);
	}

	@Transactional
	public AcceptSlotOfferResult accept(UUID userId, UUID offerId, String key) {
		return idempotencyService.execute(userId, "slot-offer:accept:" + offerId, key, new EmptyCommand(),
			AcceptSlotOfferResult.class, () -> {
				CustomerProfile customer = requireCustomer(userId);
				SlotOffer offer = slotOfferRepository.findByIdForUpdate(offerId)
					.orElseThrow(() -> new BusinessException(ErrorCode.SLOT_OFFER_NOT_FOUND));
				WaitlistEntry entry = requireOwnedWaitlistForUpdate(customer.getId(), offer.getWaitlistEntryId());
				Instant now = clock.instant();
				if (offer.isAlreadyAccepted()) throw new BusinessException(ErrorCode.SLOT_OFFER_ALREADY_ACCEPTED);
				if (offer.isAlreadyDeclinedOrRevoked()) {
					throw new BusinessException(ErrorCode.SLOT_OFFER_ALREADY_DECLINED);
				}
				if (!offer.isPending()) throw new BusinessException(ErrorCode.SLOT_OFFER_EXPIRED);
				if (offer.isExpiredAt(now)) {
					expireOffer(offer, entry, now, "SLOT_OFFER_EXPIRED");
					throw new BusinessException(ErrorCode.SLOT_OFFER_EXPIRED);
				}
				if (!entry.isOffered()) throw new BusinessException(ErrorCode.WAITLIST_INVALID_STATE);
				if (reservationRepository.existsOverlappingReservation(offer.getStoreId(), offer.getStaffId(),
					ACTIVE_RESERVATION_STATUSES, offer.getStartAt(), offer.getOccupiedUntil())) {
					expireOffer(offer, entry, now, "SLOT_TAKEN");
					throw new BusinessException(ErrorCode.SLOT_OFFER_NO_LONGER_AVAILABLE);
				}
				Reservation reservation = reservationRepository.save(Reservation.confirmedFromWaitlist(
					offer.getStoreId(), customer.getId(), offer.getServiceId(), offer.getStaffId(), offer.getStartAt(),
					offer.getServiceEndAt(), offer.getOccupiedUntil(), entry.getPartySize(), now));
				offer.accept(reservation.getId(), now);
				entry.markReserved(now);
				slotOfferStatusHistoryRepository.save(new SlotOfferStatusHistory(offer.getId(), SlotOfferStatus.PENDING,
					SlotOfferStatus.ACCEPTED, SlotOfferActorType.CUSTOMER, customer.getId(), "ACCEPT_SLOT_OFFER", now));
				asyncEventRecorder.record(AsyncEventType.SLOT_OFFER_ACCEPTED, offer.getStoreId(), "SLOT_OFFER",
					offer.getId(), new SlotOfferAcceptedPayload(offer.getId(), offer.getWaitlistEntryId(),
						reservation.getId(), offer.getStoreId(), customer.getId(), offer.getStartAt(), now));
				return new AcceptSlotOfferResult(
					new OfferAcceptance(offer.getId(), offer.getStatus(), offer.getAcceptedAt()),
					new ResultingReservation(reservation.getId(), reservation.getSource(), reservation.getStatus(),
						reservation.getStartAt()),
					new ResultingWaitlist(entry.getId(), entry.getStatus()));
			});
	}

	@Transactional
	public void offerCancelledReservation(Reservation reservation, Instant now) {
		if (reservation.getAssignedStaffId() == null) return;
		StorePolicy policy = storePolicyRepository.findByStoreId(reservation.getStoreId())
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_POLICY_NOT_FOUND));
		if (!policy.isWaitlistEnabled()) return;
		Store store = requireStore(reservation.getStoreId());
		LocalDate date = ZonedDateTime.ofInstant(reservation.getStartAt(), ZoneId.of(store.getTimezone())).toLocalDate();
		LocalTime time = ZonedDateTime.ofInstant(reservation.getStartAt(), ZoneId.of(store.getTimezone())).toLocalTime();
		if (slotOfferRepository.existsByStoreIdAndServiceIdAndStaffIdAndStartAtAndStatus(reservation.getStoreId(),
			reservation.getServiceId(), reservation.getAssignedStaffId(), reservation.getStartAt(), SlotOfferStatus.PENDING)) {
			return;
		}
		for (WaitlistEntry candidate : waitlistEntryRepository.findOfferCandidates(reservation.getStoreId(),
			reservation.getServiceId(), date)) {
			if (candidate.isExpiredAt(now)) {
				candidate.markExpired();
				continue;
			}
			if (candidate.getAcceptableStartTime().isAfter(time) || !candidate.getAcceptableEndTime().isAfter(time)) continue;
			if (!candidate.matchesStaff(reservation.getAssignedStaffId())) continue;
			WaitlistEntry locked = waitlistEntryRepository.findByIdForUpdate(candidate.getId())
				.orElseThrow(() -> new BusinessException(ErrorCode.WAITLIST_NOT_FOUND));
			if (locked.getStatus() != WaitlistStatus.WAITING || locked.isExpiredAt(now)) continue;
			SlotOffer offer = slotOfferRepository.save(new SlotOffer(locked.getId(), locked.getStoreId(),
				locked.getServiceId(), reservation.getAssignedStaffId(), reservation.getStartAt(),
				reservation.getServiceEndAt(), reservation.getOccupiedUntil(), reservation.getId(),
				now.plusSeconds(policy.getSlotOfferExpirationMinutes() * 60L)));
			locked.markOffered();
			slotOfferStatusHistoryRepository.save(new SlotOfferStatusHistory(offer.getId(), null, SlotOfferStatus.PENDING,
				SlotOfferActorType.SYSTEM, null, "SLOT_OFFER_CREATED", now));
			asyncEventRecorder.record(AsyncEventType.SLOT_OFFER_CREATED, offer.getStoreId(), "SLOT_OFFER", offer.getId(),
				new SlotOfferCreatedPayload(offer.getId(), locked.getId(), reservation.getId(), offer.getStoreId(),
					offer.getServiceId(), offer.getStaffId(), offer.getStartAt(), offer.getExpiresAt()));
			return;
		}
	}

	@Scheduled(fixedDelay = 30000)
	@Transactional
	public void expirePendingOffers() {
		Instant now = clock.instant();
		for (SlotOffer candidate : slotOfferRepository.findAllExpiredPendingOffers(SlotOfferStatus.PENDING, now)) {
			SlotOffer offer = slotOfferRepository.findByIdForUpdate(candidate.getId()).orElse(null);
			if (offer == null || offer.getStatus() != SlotOfferStatus.PENDING || !offer.isExpiredAt(now)) continue;
			WaitlistEntry entry = waitlistEntryRepository.findByIdForUpdate(offer.getWaitlistEntryId()).orElse(null);
			if (entry == null) continue;
			expireOffer(offer, entry, now, "SLOT_OFFER_EXPIRED");
		}
	}

	@Scheduled(fixedDelay = 60000)
	@Transactional
	public void expireStaleWaitlists() {
		Instant now = clock.instant();
		for (WaitlistEntry candidate : waitlistEntryRepository.findExpiredEntries(
			List.of(WaitlistStatus.WAITING, WaitlistStatus.OFFERED), now)) {
			WaitlistEntry entry = waitlistEntryRepository.findByIdForUpdate(candidate.getId()).orElse(null);
			if (entry == null || !entry.isExpiredAt(now)) continue;
			if (entry.getStatus() == WaitlistStatus.OFFERED) {
				for (SlotOffer offer : slotOfferRepository.findAllByWaitlistEntryIdAndStatus(entry.getId(), SlotOfferStatus.PENDING)) {
					expireOffer(offer, entry, now, "WAITLIST_EXPIRED");
				}
			}
			if (entry.getStatus() == WaitlistStatus.WAITING) {
				entry.markExpired();
			}
		}
	}

	private void expireOffer(SlotOffer offer, WaitlistEntry entry, Instant now, String reasonCode) {
		offer.expire();
		if (entry.getStatus() == WaitlistStatus.OFFERED) {
			if (entry.isExpiredAt(now)) {
				entry.markExpired();
			} else {
				entry.restoreWaiting();
			}
		}
		slotOfferStatusHistoryRepository.save(new SlotOfferStatusHistory(offer.getId(), SlotOfferStatus.PENDING,
			SlotOfferStatus.EXPIRED, SlotOfferActorType.SYSTEM, null, reasonCode, now));
	}

	private void revokeOffer(SlotOffer offer, SlotOfferActorType actorType, UUID actorId, String reasonCode) {
		if (offer.getStatus() != SlotOfferStatus.PENDING) return;
		offer.revoke();
		slotOfferStatusHistoryRepository.save(new SlotOfferStatusHistory(offer.getId(), SlotOfferStatus.PENDING,
			SlotOfferStatus.REVOKED, actorType, actorId, reasonCode, clock.instant()));
	}

	private int issueSequence(UUID storeId) {
		entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:lockKey))")
			.setParameter("lockKey", "waitlist-seq:" + storeId)
			.getSingleResult();
		return waitlistEntryRepository.findMaxSequenceNumberByStoreId(storeId) + 1;
	}

	private void validateCreateCommand(Store store, StorePolicy policy, ServiceOffering service,
		CreateWaitlistCommand command) {
		if (store.getStatus() != StoreStatus.ACTIVE || !policy.isWaitlistEnabled()) {
			throw new BusinessException(ErrorCode.WAITLIST_NOT_ENABLED);
		}
		if (command.partySize() < 1 || command.partySize() > service.getCapacity()) {
			throw new BusinessException(ErrorCode.INVALID_PARTY_SIZE);
		}
		if (!command.acceptableStartTime().isBefore(command.acceptableEndTime())) {
			throw new BusinessException(ErrorCode.INVALID_WAITLIST_TIME_RANGE);
		}
		if ((command.staffPreferenceType() == StaffPreferenceType.ANY_STAFF && command.preferredStaffId() != null)
			|| (command.staffPreferenceType() != StaffPreferenceType.ANY_STAFF && command.preferredStaffId() == null)) {
			throw new BusinessException(ErrorCode.BAD_REQUEST);
		}
		LocalDate today = ZonedDateTime.ofInstant(clock.instant(), ZoneId.of(store.getTimezone())).toLocalDate();
		LocalDate maxDate = today.plusDays(policy.getBookingOpenDays());
		if (command.desiredDate().isBefore(today) || command.desiredDate().isAfter(maxDate)) {
			throw new BusinessException(ErrorCode.WAITLIST_DATE_OUT_OF_RANGE);
		}
		Instant expiresAt = expiresAt(store, command.desiredDate(), command.acceptableEndTime());
		Instant minimum = clock.instant().plusSeconds(policy.getMinimumBookingNoticeMinutes() * 60L);
		if (!expiresAt.isAfter(minimum)) throw new BusinessException(ErrorCode.WAITLIST_DATE_OUT_OF_RANGE);
	}

	private Instant expiresAt(Store store, LocalDate desiredDate, LocalTime acceptableEndTime) {
		return ZonedDateTime.of(desiredDate, acceptableEndTime, ZoneId.of(store.getTimezone())).toInstant();
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

	private ServiceOffering requireActiveService(UUID storeId, UUID serviceId) {
		ServiceOffering service = requireService(storeId, serviceId);
		if (service.getStatus() != ServiceStatus.ACTIVE) throw new BusinessException(ErrorCode.SERVICE_NOT_ACTIVE);
		return service;
	}

	private ServiceOffering requireService(UUID storeId, UUID serviceId) {
		ServiceOffering service = serviceRepository.findById(serviceId)
			.orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_NOT_FOUND));
		if (!service.getStoreId().equals(storeId)) throw new BusinessException(ErrorCode.SERVICE_NOT_FOUND);
		return service;
	}

	private StoreMember requireStaffForService(UUID storeId, UUID serviceId, UUID staffId) {
		StoreMember staff = storeMemberRepository.findByIdAndStoreId(staffId, storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));
		if (staff.getStatus() != StoreMemberStatus.ACTIVE || !staff.isBookingEnabled()) {
			throw new BusinessException(ErrorCode.STAFF_NOT_AVAILABLE);
		}
		boolean available = staffServiceRepository.findByStoreMemberIdAndServiceId(staffId, serviceId)
			.filter(com.example.jariyo_backend.domain.store.entity.StaffService::isActive)
			.isPresent();
		if (!available) throw new BusinessException(ErrorCode.STAFF_NOT_AVAILABLE);
		return staff;
	}

	private WaitlistEntry requireOwnedWaitlist(UUID customerId, UUID waitlistId) {
		WaitlistEntry entry = waitlistEntryRepository.findById(waitlistId)
			.orElseThrow(() -> new BusinessException(ErrorCode.WAITLIST_NOT_FOUND));
		if (!entry.belongsToCustomer(customerId)) {
			throw new BusinessException(ErrorCode.WAITLIST_NOT_OWNED_BY_USER);
		}
		return entry;
	}

	private WaitlistEntry requireOwnedWaitlistForUpdate(UUID customerId, UUID waitlistId) {
		WaitlistEntry entry = waitlistEntryRepository.findByIdForUpdate(waitlistId)
			.orElseThrow(() -> new BusinessException(ErrorCode.WAITLIST_NOT_FOUND));
		if (!entry.belongsToCustomer(customerId)) {
			throw new BusinessException(ErrorCode.WAITLIST_NOT_OWNED_BY_USER);
		}
		return entry;
	}

	private WaitlistSummary summary(WaitlistEntry entry) {
		return new WaitlistSummary(entry.getId(), entry.getStoreId(), entry.getServiceId(), entry.getPreferredStaffId(),
			entry.getStaffPreferenceType(), entry.getDesiredDate(), entry.getAcceptableStartTime(),
			entry.getAcceptableEndTime(), entry.getStatus(), entry.getSequenceNumber(), entry.getCreatedAt());
	}

	private WaitlistDetail detail(WaitlistEntry entry) {
		Store store = requireStore(entry.getStoreId());
		ServiceOffering service = requireService(entry.getStoreId(), entry.getServiceId());
		StoreMember staff = entry.getPreferredStaffId() == null ? null
			: storeMemberRepository.findById(entry.getPreferredStaffId()).orElse(null);
		SlotOffer offer = slotOfferRepository.findFirstByWaitlistEntryIdAndStatusOrderByCreatedAtDesc(entry.getId(),
			SlotOfferStatus.PENDING).orElse(null);
		return new WaitlistDetail(entry.getId(), new NamedRef(store.getId(), store.getName()),
			new NamedRef(service.getId(), service.getName()),
			staff == null ? null : new NamedRef(staff.getId(), staff.getDisplayName()), entry.getStaffPreferenceType(),
			entry.getDesiredDate(), entry.getAcceptableStartTime(), entry.getAcceptableEndTime(), entry.getStatus(),
			entry.getSequenceNumber(), offer == null ? null : new ActiveOffer(offer.getId(), offer.getStatus(),
				offer.getStartAt(), offer.getExpiresAt()), entry.getCreatedAt());
	}

	private SlotOfferSummary slotOfferSummary(SlotOffer offer) {
		WaitlistEntry entry = waitlistEntryRepository.findById(offer.getWaitlistEntryId())
			.orElseThrow(() -> new BusinessException(ErrorCode.WAITLIST_NOT_FOUND));
		return new SlotOfferSummary(offer.getId(), entry.getId(), offer.getStatus(), offer.getStartAt(),
			offer.getExpiresAt(), remainingSeconds(offer.getExpiresAt()));
	}

	private SlotOfferDetail slotOfferDetail(SlotOffer offer, WaitlistEntry entry) {
		Store store = requireStore(offer.getStoreId());
		ServiceOffering service = requireService(offer.getStoreId(), offer.getServiceId());
		StoreMember staff = offer.getStaffId() == null ? null : storeMemberRepository.findById(offer.getStaffId()).orElse(null);
		return new SlotOfferDetail(offer.getId(), entry.getId(), new NamedRef(store.getId(), store.getName()),
			new NamedRef(service.getId(), service.getName()),
			staff == null ? null : new NamedRef(staff.getId(), staff.getDisplayName()), offer.getStartAt(),
			offer.getServiceEndAt(), offer.getStatus(), offer.getExpiresAt(), remainingSeconds(offer.getExpiresAt()));
	}

	private long remainingSeconds(Instant expiresAt) {
		return Math.max(0, expiresAt.getEpochSecond() - clock.instant().getEpochSecond());
	}

	public record CreateWaitlistCommand(UUID storeId, UUID serviceId, UUID preferredStaffId,
		StaffPreferenceType staffPreferenceType, LocalDate desiredDate, LocalTime acceptableStartTime,
		LocalTime acceptableEndTime, int partySize) { }

	public record CancelWaitlistCommand(String reason) { }

	public record EmptyCommand() { }

	public record NamedRef(UUID id, String name) { }

	public record WaitlistSummary(UUID id, UUID storeId, UUID serviceId, UUID preferredStaffId,
		StaffPreferenceType staffPreferenceType, LocalDate desiredDate, LocalTime acceptableStartTime,
		LocalTime acceptableEndTime, WaitlistStatus status, int sequenceNumber, Instant createdAt) { }

	public record ActiveOffer(UUID id, SlotOfferStatus status, Instant startAt, Instant expiresAt) { }

	public record WaitlistDetail(UUID id, NamedRef store, NamedRef service, NamedRef preferredStaff,
		StaffPreferenceType staffPreferenceType, LocalDate desiredDate, LocalTime acceptableStartTime,
		LocalTime acceptableEndTime, WaitlistStatus status, int sequenceNumber, ActiveOffer activeOffer,
		Instant createdAt) { }

	public record WaitlistCancelResult(UUID id, WaitlistStatus status, Instant cancelledAt) { }

	public record SlotOfferSummary(UUID id, UUID waitlistId, SlotOfferStatus status, Instant startAt,
		Instant expiresAt, long remainingSeconds) { }

	public record SlotOfferDetail(UUID id, UUID waitlistId, NamedRef store, NamedRef service, NamedRef staff,
		Instant startAt, Instant serviceEndAt, SlotOfferStatus status, Instant expiresAt, long remainingSeconds) { }

	public record OfferAcceptance(UUID id, SlotOfferStatus status, Instant acceptedAt) { }

	public record ResultingReservation(UUID id, com.example.jariyo_backend.domain.reservation.entity.ReservationSource source,
		ReservationStatus status, Instant startAt) { }

	public record ResultingWaitlist(UUID id, WaitlistStatus status) { }

	public record AcceptSlotOfferResult(OfferAcceptance offer, ResultingReservation reservation,
		ResultingWaitlist waitlist) { }

	private record SlotOfferCreatedPayload(UUID slotOfferId, UUID waitlistId, UUID reservationId, UUID storeId,
		UUID serviceId, UUID staffId, Instant startAt, Instant expiresAt) { }

	private record SlotOfferAcceptedPayload(UUID slotOfferId, UUID waitlistId, UUID reservationId, UUID storeId,
		UUID customerId, Instant startAt, Instant acceptedAt) { }
}
