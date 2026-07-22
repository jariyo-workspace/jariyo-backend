package com.example.jariyo_backend.domain.walkin.entity;

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
@Table(name = "check_in")
public class CheckIn {
	@Id @UuidGenerator(style = UuidGenerator.Style.VERSION_7) private UUID id;
	@Column(name = "store_id", nullable = false) private UUID storeId;
	@Column(name = "customer_id") private UUID customerId;
	@Column(name = "walk_in_entry_id", nullable = false) private UUID walkInEntryId;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private CheckInMethod method;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private CheckInStatus status;
	@Column(name = "checked_in_at", nullable = false) private Instant checkedInAt;
	@Column(name = "cancelled_at") private Instant cancelledAt;
	@Column(name = "processed_by_member_id") private UUID processedByMemberId;
	@CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

	protected CheckIn() { }

	public CheckIn(UUID storeId, UUID customerId, UUID walkInEntryId, CheckInMethod method, Instant checkedInAt,
		UUID processedByMemberId) {
		this.storeId = storeId;
		this.customerId = customerId;
		this.walkInEntryId = walkInEntryId;
		this.method = method;
		this.status = CheckInStatus.VALID;
		this.checkedInAt = checkedInAt;
		this.processedByMemberId = processedByMemberId;
	}

	public UUID getId() { return id; }
	public CheckInStatus getStatus() { return status; }
	public Instant getCheckedInAt() { return checkedInAt; }
}
