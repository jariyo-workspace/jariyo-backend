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
@Table(name = "failed_async_job")
public class FailedAsyncJob {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "store_id")
	private UUID storeId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 64)
	private AsyncEventType type;

	@Column(name = "reference_type", nullable = false, length = 64)
	private String referenceType;

	@Column(name = "reference_id", nullable = false)
	private UUID referenceId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private FailedJobStatus status;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "last_error_code", length = 128)
	private String lastErrorCode;

	@Column(name = "last_error_message", length = 1000)
	private String lastErrorMessage;

	@Column(name = "failed_at", nullable = false)
	private Instant failedAt;

	@Column(name = "ignored_reason", length = 1000)
	private String ignoredReason;

	@Version
	@Column(nullable = false)
	private long version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected FailedAsyncJob() {
	}

	public FailedAsyncJob(UUID storeId, AsyncEventType type, String referenceType, UUID referenceId, int attemptCount,
		String lastErrorCode, String lastErrorMessage, Instant failedAt) {
		this.storeId = storeId;
		this.type = type;
		this.referenceType = referenceType;
		this.referenceId = referenceId;
		this.status = FailedJobStatus.FAILED;
		this.attemptCount = attemptCount;
		this.lastErrorCode = lastErrorCode;
		this.lastErrorMessage = lastErrorMessage;
		this.failedAt = failedAt;
	}

	public void refreshFailure(int attemptCount, String errorCode, String errorMessage, Instant failedAt) {
		this.status = FailedJobStatus.FAILED;
		this.attemptCount = attemptCount;
		this.lastErrorCode = errorCode;
		this.lastErrorMessage = errorMessage;
		this.failedAt = failedAt;
	}

	public void markResolved() {
		this.status = FailedJobStatus.RESOLVED;
		this.ignoredReason = null;
	}

	public void markPending() {
		this.status = FailedJobStatus.PENDING;
		this.ignoredReason = null;
	}

	public void markIgnored(String reason) {
		this.status = FailedJobStatus.IGNORED;
		this.ignoredReason = reason;
	}

	public FailedJobStatus getStatus() {
		return status;
	}

	public UUID getId() {
		return id;
	}

	public UUID getStoreId() {
		return storeId;
	}

	public AsyncEventType getType() {
		return type;
	}

	public String getReferenceType() {
		return referenceType;
	}

	public UUID getReferenceId() {
		return referenceId;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public String getLastErrorCode() {
		return lastErrorCode;
	}

	public String getLastErrorMessage() {
		return lastErrorMessage;
	}

	public Instant getFailedAt() {
		return failedAt;
	}

	public String getIgnoredReason() {
		return ignoredReason;
	}
}
