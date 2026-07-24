package com.example.jariyo_backend.domain.store.entity;

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

@Entity
@Table(name = "service")
public class StoreServiceDefinition {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "store_id", nullable = false)
	private UUID storeId;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(length = 1000)
	private String description;

	@Column(name = "duration_minutes", nullable = false)
	private int durationMinutes;

	@Column(name = "cleanup_minutes", nullable = false)
	private int cleanupMinutes;

	@Column(nullable = false)
	private int capacity;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ServiceStatus status;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected StoreServiceDefinition() {
	}

	public StoreServiceDefinition(UUID id, UUID storeId, String name, String description, int durationMinutes,
		int cleanupMinutes, int capacity, ServiceStatus status) {
		this.id = id;
		this.storeId = storeId;
		this.name = name;
		this.description = description;
		this.durationMinutes = durationMinutes;
		this.cleanupMinutes = cleanupMinutes;
		this.capacity = capacity;
		this.status = status;
	}

	public UUID getId() {
		return id;
	}

	public UUID getStoreId() {
		return storeId;
	}

	public String getName() {
		return name;
	}

	public int getDurationMinutes() {
		return durationMinutes;
	}

	public int getCleanupMinutes() {
		return cleanupMinutes;
	}

	public int getCapacity() {
		return capacity;
	}

	public ServiceStatus getStatus() {
		return status;
	}
}
