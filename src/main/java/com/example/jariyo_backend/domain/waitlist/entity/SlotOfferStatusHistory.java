package com.example.jariyo_backend.domain.waitlist.entity;

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
@Table(name = "slot_offer_status_history")
public class SlotOfferStatusHistory {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "slot_offer_id", nullable = false)
	private UUID slotOfferId;

	@Enumerated(EnumType.STRING)
	@Column(name = "previous_status", length = 32)
	private SlotOfferStatus previousStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "next_status", nullable = false, length = 32)
	private SlotOfferStatus nextStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "changed_by_type", nullable = false, length = 32)
	private SlotOfferActorType changedByType;

	@Column(name = "changed_by_id")
	private UUID changedById;

	@Column(name = "reason_code", nullable = false, length = 64)
	private String reasonCode;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	protected SlotOfferStatusHistory() {
	}

	public SlotOfferStatusHistory(UUID slotOfferId, SlotOfferStatus previousStatus, SlotOfferStatus nextStatus,
		SlotOfferActorType changedByType, UUID changedById, String reasonCode, Instant occurredAt) {
		this.slotOfferId = slotOfferId;
		this.previousStatus = previousStatus;
		this.nextStatus = nextStatus;
		this.changedByType = changedByType;
		this.changedById = changedById;
		this.reasonCode = reasonCode;
		this.occurredAt = occurredAt;
	}
}
