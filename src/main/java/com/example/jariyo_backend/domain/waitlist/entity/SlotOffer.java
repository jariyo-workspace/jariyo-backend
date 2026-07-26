package com.example.jariyo_backend.domain.waitlist.entity;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "slot_offer")
public class SlotOffer {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "waitlist_entry_id", nullable = false)
	private UUID waitlistEntryId;

	@Column(name = "store_id", nullable = false)
	private UUID storeId;

	@Column(name = "service_id", nullable = false)
	private UUID serviceId;

	@Column(name = "staff_id")
	private UUID staffId;

	@Column(name = "start_at", nullable = false)
	private Instant startAt;

	@Column(name = "service_end_at", nullable = false)
	private Instant serviceEndAt;

	@Column(name = "occupied_until", nullable = false)
	private Instant occupiedUntil;

	@Column(name = "source_reservation_id")
	private UUID sourceReservationId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private SlotOfferStatus status;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "accepted_at")
	private Instant acceptedAt;

	@Column(name = "declined_at")
	private Instant declinedAt;

	@Column(name = "resulting_reservation_id")
	private UUID resultingReservationId;

	@Column(nullable = false)
	private long version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected SlotOffer() {
	}

	public SlotOffer(UUID waitlistEntryId, UUID storeId, UUID serviceId, UUID staffId, Instant startAt,
		Instant serviceEndAt, Instant occupiedUntil, UUID sourceReservationId, Instant expiresAt) {
		this.waitlistEntryId = waitlistEntryId;
		this.storeId = storeId;
		this.serviceId = serviceId;
		this.staffId = staffId;
		this.startAt = startAt;
		this.serviceEndAt = serviceEndAt;
		this.occupiedUntil = occupiedUntil;
		this.sourceReservationId = sourceReservationId;
		this.status = SlotOfferStatus.PENDING;
		this.expiresAt = expiresAt;
	}

	public void accept(UUID reservationId, Instant acceptedAt) {
		this.status = SlotOfferStatus.ACCEPTED;
		this.resultingReservationId = reservationId;
		this.acceptedAt = acceptedAt;
	}

	public boolean isPending() {
		return status == SlotOfferStatus.PENDING;
	}

	public boolean isAlreadyAccepted() {
		return status == SlotOfferStatus.ACCEPTED;
	}

	public boolean isAlreadyDeclinedOrRevoked() {
		return status == SlotOfferStatus.DECLINED || status == SlotOfferStatus.REVOKED;
	}

	public void decline(Instant declinedAt) {
		this.status = SlotOfferStatus.DECLINED;
		this.declinedAt = declinedAt;
	}

	public void expire() {
		this.status = SlotOfferStatus.EXPIRED;
	}

	public void revoke() {
		this.status = SlotOfferStatus.REVOKED;
	}

	public boolean isExpiredAt(Instant now) {
		return !expiresAt.isAfter(now);
	}

	public UUID getId() {
		return id;
	}

	public UUID getWaitlistEntryId() {
		return waitlistEntryId;
	}

	public UUID getStoreId() {
		return storeId;
	}

	public UUID getServiceId() {
		return serviceId;
	}

	public UUID getStaffId() {
		return staffId;
	}

	public Instant getStartAt() {
		return startAt;
	}

	public Instant getServiceEndAt() {
		return serviceEndAt;
	}

	public Instant getOccupiedUntil() {
		return occupiedUntil;
	}

	public UUID getSourceReservationId() {
		return sourceReservationId;
	}

	public SlotOfferStatus getStatus() {
		return status;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getAcceptedAt() {
		return acceptedAt;
	}

	public UUID getResultingReservationId() {
		return resultingReservationId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
