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
@Table(name = "store")
public class Store {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(length = 1000)
	private String description;

	@Column(name = "phone_number", nullable = false, length = 32)
	private String phoneNumber;

	@Column(nullable = false, length = 500)
	private String address;

	@Column(nullable = false, length = 64)
	private String timezone;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private StoreStatus status;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Store() {
	}

	public Store(UUID id) {
		this.id = id;
	}

	public Store(UUID id, String name, String description, String phoneNumber, String address, String timezone,
		StoreStatus status) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.phoneNumber = phoneNumber;
		this.address = address;
		this.timezone = timezone;
		this.status = status;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public String getAddress() {
		return address;
	}

	public String getTimezone() {
		return timezone;
	}

	public StoreStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
