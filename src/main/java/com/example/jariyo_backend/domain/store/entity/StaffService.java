package com.example.jariyo_backend.domain.store.entity;

import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "staff_service", uniqueConstraints = {
	@UniqueConstraint(name = "uk_staff_service_member_service", columnNames = {"store_member_id", "service_id"})
})
public class StaffService {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "store_member_id", nullable = false)
	private UUID storeMemberId;

	@Column(name = "service_id", nullable = false)
	private UUID serviceId;

	@Column(name = "custom_duration_minutes")
	private Integer customDurationMinutes;

	@Column(nullable = false)
	private boolean active;

	protected StaffService() {
	}

	public StaffService(UUID id, UUID storeMemberId, UUID serviceId, Integer customDurationMinutes, boolean active) {
		this.id = id;
		this.storeMemberId = storeMemberId;
		this.serviceId = serviceId;
		this.customDurationMinutes = customDurationMinutes;
		this.active = active;
	}

	public UUID getStoreMemberId() {
		return storeMemberId;
	}

	public Integer getCustomDurationMinutes() {
		return customDurationMinutes;
	}
}
