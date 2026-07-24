package com.example.jariyo_backend.domain.reservation.entity;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reservation_status_history")
public class ReservationStatusHistory {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "reservation_id", nullable = false)
	private UUID reservationId;

	@Enumerated(EnumType.STRING)
	@Column(name = "previous_status", length = 32)
	private ReservationStatus previousStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "next_status", nullable = false, length = 32)
	private ReservationStatus nextStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "changed_by_type", nullable = false, length = 32)
	private ReservationActorType changedByType;

	@Column(name = "changed_by_id")
	private UUID changedById;

	@Column(name = "reason_code", nullable = false, length = 64)
	private String reasonCode;

	@Column(length = 500)
	private String note;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	protected ReservationStatusHistory() {
	}

	public ReservationStatusHistory(UUID reservationId, ReservationStatus previousStatus, ReservationStatus nextStatus,
		ReservationActorType changedByType, UUID changedById, String reasonCode, String note, Instant occurredAt) {
		this.reservationId = reservationId;
		this.previousStatus = previousStatus;
		this.nextStatus = nextStatus;
		this.changedByType = changedByType;
		this.changedById = changedById;
		this.reasonCode = reasonCode;
		this.note = note;
		this.occurredAt = occurredAt;
	}

	public UUID getId() {
		return id;
	}

	public ReservationStatus getPreviousStatus() {
		return previousStatus;
	}

	public ReservationStatus getNextStatus() {
		return nextStatus;
	}

	public ReservationActorType getChangedByType() {
		return changedByType;
	}

	public UUID getChangedById() {
		return changedById;
	}

	public String getReasonCode() {
		return reasonCode;
	}

	public String getNote() {
		return note;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}
}
