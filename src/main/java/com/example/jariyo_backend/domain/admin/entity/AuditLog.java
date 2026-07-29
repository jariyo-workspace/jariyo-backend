package com.example.jariyo_backend.domain.admin.entity;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_log")
public class AuditLog {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "store_id", nullable = false)
	private UUID storeId;

	@Enumerated(EnumType.STRING)
	@Column(name = "actor_type", nullable = false, length = 32)
	private AuditActorType actorType;

	@Column(name = "actor_id")
	private UUID actorId;

	@Column(nullable = false, length = 100)
	private String action;

	@Column(name = "target_type", nullable = false, length = 100)
	private String targetType;

	@Column(name = "target_id", nullable = false)
	private UUID targetId;

	@Column(length = 500)
	private String reason;

	@Column(name = "previous_data", length = 4000)
	private String previousData;

	@Column(name = "changed_data", length = 4000)
	private String changedData;

	@Column(name = "request_id", length = 200)
	private String requestId;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AuditLog() {
	}

	public AuditLog(UUID storeId, AuditActorType actorType, UUID actorId, String action, String targetType, UUID targetId,
		String reason, String previousData, String changedData, String requestId, Instant occurredAt) {
		this.storeId = storeId;
		this.actorType = actorType;
		this.actorId = actorId;
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.reason = reason;
		this.previousData = previousData;
		this.changedData = changedData;
		this.requestId = requestId;
		this.occurredAt = occurredAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getStoreId() {
		return storeId;
	}

	public AuditActorType getActorType() {
		return actorType;
	}

	public UUID getActorId() {
		return actorId;
	}

	public String getAction() {
		return action;
	}

	public String getTargetType() {
		return targetType;
	}

	public UUID getTargetId() {
		return targetId;
	}

	public String getReason() {
		return reason;
	}

	public String getPreviousData() {
		return previousData;
	}

	public String getChangedData() {
		return changedData;
	}

	public String getRequestId() {
		return requestId;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}
}
