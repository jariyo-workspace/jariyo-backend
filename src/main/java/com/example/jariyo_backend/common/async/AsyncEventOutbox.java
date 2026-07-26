package com.example.jariyo_backend.common.async;

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
import jakarta.persistence.Version;

@Entity
@Table(name = "async_event_outbox")
public class AsyncEventOutbox {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 64)
	private AsyncEventType type;

	@Column(name = "store_id")
	private UUID storeId;

	@Column(name = "reference_type", nullable = false, length = 64)
	private String referenceType;

	@Column(name = "reference_id", nullable = false)
	private UUID referenceId;

	@Column(name = "payload_json", nullable = false, length = 4000)
	private String payloadJson;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private AsyncEventStatus status;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "processed_at")
	private Instant processedAt;

	@Column(name = "last_error_code", length = 128)
	private String lastErrorCode;

	@Column(name = "last_error_message", length = 1000)
	private String lastErrorMessage;

	@Version
	@Column(nullable = false)
	private long version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected AsyncEventOutbox() {
	}

	public AsyncEventOutbox(AsyncEventType type, UUID storeId, String referenceType, UUID referenceId, String payloadJson) {
		this.type = type;
		this.storeId = storeId;
		this.referenceType = referenceType;
		this.referenceId = referenceId;
		this.payloadJson = payloadJson;
		this.status = AsyncEventStatus.PENDING;
	}

	public void markProcessed(Instant processedAt) {
		this.status = AsyncEventStatus.PROCESSED;
		this.processedAt = processedAt;
		this.lastErrorCode = null;
		this.lastErrorMessage = null;
	}

	public void markFailed(String errorCode, String errorMessage) {
		this.status = AsyncEventStatus.FAILED;
		this.attemptCount += 1;
		this.lastErrorCode = errorCode;
		this.lastErrorMessage = errorMessage;
	}

	public void requeue() {
		this.status = AsyncEventStatus.PENDING;
		this.lastErrorCode = null;
		this.lastErrorMessage = null;
	}

	public UUID getId() {
		return id;
	}

	public AsyncEventType getType() {
		return type;
	}

	public UUID getStoreId() {
		return storeId;
	}

	public String getReferenceType() {
		return referenceType;
	}

	public UUID getReferenceId() {
		return referenceId;
	}

	public String getPayloadJson() {
		return payloadJson;
	}

	public AsyncEventStatus getStatus() {
		return status;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}

	public String getLastErrorCode() {
		return lastErrorCode;
	}

	public String getLastErrorMessage() {
		return lastErrorMessage;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
