package com.example.jariyo_backend.domain.walkin.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
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
import jakarta.persistence.Version;

@Entity
@Table(name = "walk_in_entry")
public class WalkInEntry {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "store_id", nullable = false)
	private UUID storeId;

	@Column(name = "customer_id")
	private UUID customerId;

	@Column(name = "guest_name", length = 100)
	private String guestName;

	@Column(name = "guest_phone_number", length = 32)
	private String guestPhoneNumber;

	@Column(name = "service_id", nullable = false)
	private UUID serviceId;

	@Column(name = "preferred_staff_id")
	private UUID preferredStaffId;

	@Column(name = "party_size", nullable = false)
	private int partySize;

	@Column(name = "operation_date", nullable = false)
	private LocalDate operationDate;

	@Column(name = "queue_number", nullable = false)
	private int queueNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private WalkInStatus status;

	@Column(name = "estimated_wait_minutes", nullable = false)
	private int estimatedWaitMinutes;

	@Column(name = "checked_in_at")
	private Instant checkedInAt;

	@Column(name = "called_at")
	private Instant calledAt;

	@Column(name = "call_expires_at")
	private Instant callExpiresAt;

	@Column(name = "service_started_at")
	private Instant serviceStartedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Version
	@Column(nullable = false)
	private long version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected WalkInEntry() {
	}

	private WalkInEntry(UUID storeId, UUID customerId, String guestName, String guestPhoneNumber, UUID serviceId,
		UUID preferredStaffId, int partySize, LocalDate operationDate, int queueNumber, int estimatedWaitMinutes) {
		this.storeId = storeId;
		this.customerId = customerId;
		this.guestName = guestName;
		this.guestPhoneNumber = guestPhoneNumber;
		this.serviceId = serviceId;
		this.preferredStaffId = preferredStaffId;
		this.partySize = partySize;
		this.operationDate = operationDate;
		this.queueNumber = queueNumber;
		this.estimatedWaitMinutes = estimatedWaitMinutes;
		this.status = WalkInStatus.WAITING;
	}

	public static WalkInEntry forCustomer(UUID storeId, UUID customerId, UUID serviceId, UUID preferredStaffId,
		int partySize, LocalDate operationDate, int queueNumber, int estimatedWaitMinutes) {
		return new WalkInEntry(storeId, customerId, null, null, serviceId, preferredStaffId, partySize, operationDate,
			queueNumber, estimatedWaitMinutes);
	}

	public static WalkInEntry forGuest(UUID storeId, String guestName, String guestPhoneNumber, UUID serviceId,
		UUID preferredStaffId, int partySize, LocalDate operationDate, int queueNumber, int estimatedWaitMinutes) {
		return new WalkInEntry(storeId, null, guestName, guestPhoneNumber, serviceId, preferredStaffId, partySize,
			operationDate, queueNumber, estimatedWaitMinutes);
	}

	public void call(Instant calledAt, Instant expiresAt) {
		requireTransition(WalkInStatus.CALLED);
		status = WalkInStatus.CALLED;
		this.calledAt = calledAt;
		callExpiresAt = expiresAt;
	}

	public boolean belongsToCustomer(UUID customerId) {
		return this.customerId != null && this.customerId.equals(customerId);
	}

	public boolean isCalled() {
		return status == WalkInStatus.CALLED;
	}

	public void recall(Instant calledAt, Instant expiresAt) {
		if (status != WalkInStatus.CALLED) throw new BusinessException(ErrorCode.WALK_IN_INVALID_STATE);
		this.calledAt = calledAt;
		callExpiresAt = expiresAt;
	}

	public void transitionTo(WalkInStatus next, Instant now) {
		requireTransition(next);
		status = next;
		if (next == WalkInStatus.CHECKED_IN) checkedInAt = now;
		if (next == WalkInStatus.IN_SERVICE) serviceStartedAt = now;
		if (next == WalkInStatus.COMPLETED) completedAt = now;
		if (next == WalkInStatus.CANCELLED) cancelledAt = now;
	}

	private void requireTransition(WalkInStatus next) {
		EnumSet<WalkInStatus> allowed = switch (status) {
			case WAITING -> EnumSet.of(WalkInStatus.CALLED, WalkInStatus.CANCELLED);
			case CALLED -> EnumSet.of(WalkInStatus.CHECKED_IN, WalkInStatus.SKIPPED,
				WalkInStatus.NO_SHOW, WalkInStatus.CANCELLED);
			case SKIPPED -> EnumSet.of(WalkInStatus.WAITING, WalkInStatus.CALLED, WalkInStatus.CANCELLED);
			case CHECKED_IN -> EnumSet.of(WalkInStatus.IN_SERVICE, WalkInStatus.CANCELLED);
			case IN_SERVICE -> EnumSet.of(WalkInStatus.COMPLETED);
			default -> EnumSet.noneOf(WalkInStatus.class);
		};
		if (!allowed.contains(next)) throw new BusinessException(ErrorCode.WALK_IN_INVALID_STATE);
	}

	public UUID getId() { return id; }
	public UUID getStoreId() { return storeId; }
	public UUID getCustomerId() { return customerId; }
	public String getGuestName() { return guestName; }
	public String getGuestPhoneNumber() { return guestPhoneNumber; }
	public UUID getServiceId() { return serviceId; }
	public UUID getPreferredStaffId() { return preferredStaffId; }
	public int getPartySize() { return partySize; }
	public LocalDate getOperationDate() { return operationDate; }
	public int getQueueNumber() { return queueNumber; }
	public WalkInStatus getStatus() { return status; }
	public int getEstimatedWaitMinutes() { return estimatedWaitMinutes; }
	public Instant getCheckedInAt() { return checkedInAt; }
	public Instant getCalledAt() { return calledAt; }
	public Instant getCallExpiresAt() { return callExpiresAt; }
	public Instant getServiceStartedAt() { return serviceStartedAt; }
	public Instant getCompletedAt() { return completedAt; }
	public Instant getCancelledAt() { return cancelledAt; }
	public Instant getCreatedAt() { return createdAt; }
}
