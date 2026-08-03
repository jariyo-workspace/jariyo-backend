package com.example.jariyo_backend.domain.user.entity;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "store_member", uniqueConstraints = {
	@UniqueConstraint(name = "uk_store_member_user_store", columnNames = {"user_id", "store_id"})
})
public class StoreMember {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "store_id", nullable = false)
	private UUID storeId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private StoreMemberRole role;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private StoreMemberStatus status;

	@Column(name = "booking_enabled", nullable = false)
	private boolean bookingEnabled;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected StoreMember() {
	}

	public StoreMember(UUID storeId, UserAccount user, StoreMemberRole role, String displayName, boolean bookingEnabled) {
		this(null, storeId, user, role, displayName, bookingEnabled);
	}

	public StoreMember(UUID id, UUID storeId, UserAccount user, StoreMemberRole role, String displayName,
		boolean bookingEnabled) {
		this.id = id;
		this.storeId = storeId;
		this.user = user;
		this.role = role;
		this.displayName = displayName;
		this.status = StoreMemberStatus.ACTIVE;
		this.bookingEnabled = bookingEnabled;
	}

	public UUID getStoreId() {
		return storeId;
	}

	public UUID getId() {
		return id;
	}

	public StoreMemberRole getRole() {
		return role;
	}

	public String getDisplayName() {
		return displayName;
	}

	public StoreMemberStatus getStatus() {
		return status;
	}

	public boolean isBookingEnabled() {
		return bookingEnabled;
	}

	public void update(String displayName, StoreMemberRole role, boolean bookingEnabled, StoreMemberStatus status) {
		this.displayName = displayName;
		this.role = role;
		this.bookingEnabled = bookingEnabled;
		this.status = status;
	}

	public void deactivate() {
		status = StoreMemberStatus.INACTIVE;
		bookingEnabled = false;
	}
}
