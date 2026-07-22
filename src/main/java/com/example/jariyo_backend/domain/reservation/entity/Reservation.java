package com.example.jariyo_backend.domain.reservation.entity;

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
@Table(name = "reservation")
public class Reservation {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "store_id", nullable = false)
	private UUID storeId;

	@Column(name = "customer_id", nullable = false)
	private UUID customerId;

	@Column(name = "service_id", nullable = false)
	private UUID serviceId;

	@Column(name = "assigned_staff_id")
	private UUID assignedStaffId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ReservationSource source;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ReservationStatus status;

	@Column(name = "start_at", nullable = false)
	private Instant startAt;

	@Column(name = "service_end_at", nullable = false)
	private Instant serviceEndAt;

	@Column(name = "occupied_until", nullable = false)
	private Instant occupiedUntil;

	@Column(name = "party_size", nullable = false)
	private int partySize;

	@Column(name = "customer_note", length = 1000)
	private String customerNote;

	@Column(name = "store_note", length = 1000)
	private String storeNote;

	@Column(name = "hold_expires_at")
	private Instant holdExpiresAt;

	@Column(name = "cancellation_reason", length = 255)
	private String cancellationReason;

	@Column(name = "cancelled_by_type", length = 32)
	private String cancelledByType;

	@Column(name = "cancelled_by_id")
	private UUID cancelledById;

	@Column(name = "confirmed_at")
	private Instant confirmedAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(nullable = false)
	private long version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Reservation() {
	}

	public Reservation(UUID id, UUID storeId, UUID customerId, UUID serviceId, UUID assignedStaffId,
		ReservationSource source, ReservationStatus status, Instant startAt, Instant serviceEndAt,
		Instant occupiedUntil, int partySize) {
		this.id = id;
		this.storeId = storeId;
		this.customerId = customerId;
		this.serviceId = serviceId;
		this.assignedStaffId = assignedStaffId;
		this.source = source;
		this.status = status;
		this.startAt = startAt;
		this.serviceEndAt = serviceEndAt;
		this.occupiedUntil = occupiedUntil;
		this.partySize = partySize;
	}

	public UUID getAssignedStaffId() {
		return assignedStaffId;
	}

	public Instant getStartAt() {
		return startAt;
	}

	public Instant getOccupiedUntil() {
		return occupiedUntil;
	}
}
