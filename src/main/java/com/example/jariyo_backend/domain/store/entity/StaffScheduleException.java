package com.example.jariyo_backend.domain.store.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
@Table(name = "staff_schedule_exception")
public class StaffScheduleException {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "store_member_id", nullable = false)
	private UUID storeMemberId;

	@Column(name = "target_date", nullable = false)
	private LocalDate targetDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private StaffScheduleExceptionType type;

	@Column(name = "start_time")
	private LocalTime startTime;

	@Column(name = "end_time")
	private LocalTime endTime;

	@Column(length = 500)
	private String reason;

	@Column(name = "created_by_member_id")
	private UUID createdByMemberId;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected StaffScheduleException() {
	}

	public StaffScheduleException(UUID storeMemberId, LocalDate targetDate, StaffScheduleExceptionType type,
		LocalTime startTime, LocalTime endTime, String reason, UUID createdByMemberId) {
		this.storeMemberId = storeMemberId;
		this.targetDate = targetDate;
		this.type = type;
		this.startTime = startTime;
		this.endTime = endTime;
		this.reason = reason;
		this.createdByMemberId = createdByMemberId;
	}

	public UUID getId() {
		return id;
	}

	public UUID getStoreMemberId() {
		return storeMemberId;
	}

	public LocalDate getTargetDate() {
		return targetDate;
	}

	public StaffScheduleExceptionType getType() {
		return type;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public String getReason() {
		return reason;
	}

	public UUID getCreatedByMemberId() {
		return createdByMemberId;
	}
}

