package com.example.jariyo_backend.domain.waitlist.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
@Table(name = "waitlist_entry")
public class WaitlistEntry {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "store_id", nullable = false)
	private UUID storeId;

	@Column(name = "customer_id", nullable = false)
	private UUID customerId;

	@Column(name = "service_id", nullable = false)
	private UUID serviceId;

	@Column(name = "preferred_staff_id")
	private UUID preferredStaffId;

	@Enumerated(EnumType.STRING)
	@Column(name = "staff_preference_type", nullable = false, length = 32)
	private StaffPreferenceType staffPreferenceType;

	@Column(name = "desired_date", nullable = false)
	private LocalDate desiredDate;

	@Column(name = "acceptable_start_time", nullable = false)
	private LocalTime acceptableStartTime;

	@Column(name = "acceptable_end_time", nullable = false)
	private LocalTime acceptableEndTime;

	@Column(name = "party_size", nullable = false)
	private int partySize;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private WaitlistStatus status;

	@Column(name = "sequence_number", nullable = false)
	private int sequenceNumber;

	@Column(nullable = false)
	private int priority;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Column(name = "reserved_at")
	private Instant reservedAt;

	@Column(nullable = false)
	private long version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected WaitlistEntry() {
	}

	public WaitlistEntry(UUID storeId, UUID customerId, UUID serviceId, UUID preferredStaffId,
		StaffPreferenceType staffPreferenceType, LocalDate desiredDate, LocalTime acceptableStartTime,
		LocalTime acceptableEndTime, int partySize, int sequenceNumber, Instant expiresAt) {
		this.storeId = storeId;
		this.customerId = customerId;
		this.serviceId = serviceId;
		this.preferredStaffId = preferredStaffId;
		this.staffPreferenceType = staffPreferenceType;
		this.desiredDate = desiredDate;
		this.acceptableStartTime = acceptableStartTime;
		this.acceptableEndTime = acceptableEndTime;
		this.partySize = partySize;
		this.status = WaitlistStatus.WAITING;
		this.sequenceNumber = sequenceNumber;
		this.priority = 0;
		this.expiresAt = expiresAt;
	}

	public void markOffered() {
		this.status = WaitlistStatus.OFFERED;
	}

	public void markReserved(Instant reservedAt) {
		this.status = WaitlistStatus.RESERVED;
		this.reservedAt = reservedAt;
	}

	public void markCancelled(Instant cancelledAt) {
		this.status = WaitlistStatus.CANCELLED;
		this.cancelledAt = cancelledAt;
	}

	public void markExpired() {
		this.status = WaitlistStatus.EXPIRED;
	}

	public void restoreWaiting() {
		this.status = WaitlistStatus.WAITING;
	}

	public boolean isExpiredAt(Instant now) {
		return !expiresAt.isAfter(now);
	}

	public boolean matchesStaff(UUID staffId) {
		return switch (staffPreferenceType) {
			case ANY_STAFF -> true;
			case SPECIFIC_ONLY, SPECIFIC_PREFERRED -> preferredStaffId != null && preferredStaffId.equals(staffId);
		};
	}

	public UUID getId() {
		return id;
	}

	public UUID getStoreId() {
		return storeId;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public UUID getServiceId() {
		return serviceId;
	}

	public UUID getPreferredStaffId() {
		return preferredStaffId;
	}

	public StaffPreferenceType getStaffPreferenceType() {
		return staffPreferenceType;
	}

	public LocalDate getDesiredDate() {
		return desiredDate;
	}

	public LocalTime getAcceptableStartTime() {
		return acceptableStartTime;
	}

	public LocalTime getAcceptableEndTime() {
		return acceptableEndTime;
	}

	public int getPartySize() {
		return partySize;
	}

	public WaitlistStatus getStatus() {
		return status;
	}

	public int getSequenceNumber() {
		return sequenceNumber;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getCancelledAt() {
		return cancelledAt;
	}

	public Instant getReservedAt() {
		return reservedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
