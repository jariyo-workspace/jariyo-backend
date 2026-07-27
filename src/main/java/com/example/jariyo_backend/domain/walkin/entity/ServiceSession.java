package com.example.jariyo_backend.domain.walkin.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
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
@Table(name = "service_session")
public class ServiceSession {
	@Id @UuidGenerator(style = UuidGenerator.Style.VERSION_7) private UUID id;
	@Column(name = "store_id", nullable = false) private UUID storeId;
	@Column(name = "customer_id") private UUID customerId;
	@Column(name = "walk_in_entry_id", nullable = false) private UUID walkInEntryId;
	@Column(name = "service_id", nullable = false) private UUID serviceId;
	@Column(name = "staff_id", nullable = false) private UUID staffId;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ServiceSessionStatus status;
	@Column(name = "actual_start_at", nullable = false) private Instant actualStartAt;
	@Column(name = "actual_end_at") private Instant actualEndAt;
	@Column(name = "completion_note", length = 1000) private String completionNote;
	@CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
	@UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

	protected ServiceSession() { }

	public ServiceSession(UUID storeId, UUID customerId, UUID walkInEntryId, UUID serviceId, UUID staffId,
		Instant actualStartAt) {
		this.storeId = storeId;
		this.customerId = customerId;
		this.walkInEntryId = walkInEntryId;
		this.serviceId = serviceId;
		this.staffId = staffId;
		this.status = ServiceSessionStatus.IN_PROGRESS;
		this.actualStartAt = actualStartAt;
	}

	public void complete(Instant at, String note) {
		if (status != ServiceSessionStatus.IN_PROGRESS) throw new BusinessException(ErrorCode.SERVICE_SESSION_INVALID_STATE);
		status = ServiceSessionStatus.COMPLETED;
		actualEndAt = at;
		completionNote = note;
	}

	public UUID getId() { return id; }
	public UUID getStoreId() { return storeId; }
	public UUID getCustomerId() { return customerId; }
	public UUID getWalkInEntryId() { return walkInEntryId; }
	public UUID getServiceId() { return serviceId; }
	public UUID getStaffId() { return staffId; }
	public ServiceSessionStatus getStatus() { return status; }
	public Instant getActualStartAt() { return actualStartAt; }
	public Instant getActualEndAt() { return actualEndAt; }
	public long getActualDurationMinutes() { return actualEndAt == null ? 0 : Duration.between(actualStartAt, actualEndAt).toMinutes(); }
}
