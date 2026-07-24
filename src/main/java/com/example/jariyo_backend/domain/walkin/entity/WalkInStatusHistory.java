package com.example.jariyo_backend.domain.walkin.entity;

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
@Table(name = "walk_in_status_history")
public class WalkInStatusHistory {
	@Id @UuidGenerator(style = UuidGenerator.Style.VERSION_7) private UUID id;
	@Column(name = "walk_in_entry_id", nullable = false) private UUID walkInEntryId;
	@Enumerated(EnumType.STRING) @Column(name = "previous_status", length = 32) private WalkInStatus previousStatus;
	@Enumerated(EnumType.STRING) @Column(name = "new_status", nullable = false, length = 32) private WalkInStatus newStatus;
	@Enumerated(EnumType.STRING) @Column(name = "actor_type", nullable = false, length = 32) private WalkInActorType actorType;
	@Column(name = "actor_id") private UUID actorId;
	@Column(length = 500) private String reason;
	@Column(name = "occurred_at", nullable = false) private Instant occurredAt;

	protected WalkInStatusHistory() { }

	public WalkInStatusHistory(UUID walkInEntryId, WalkInStatus previousStatus, WalkInStatus newStatus,
		WalkInActorType actorType, UUID actorId, String reason, Instant occurredAt) {
		this.walkInEntryId = walkInEntryId;
		this.previousStatus = previousStatus;
		this.newStatus = newStatus;
		this.actorType = actorType;
		this.actorId = actorId;
		this.reason = reason;
		this.occurredAt = occurredAt;
	}
}
