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
@Table(name = "schedule_exception")
public class ScheduleException {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "store_id", nullable = false)
	private UUID storeId;

	@Column(name = "target_date", nullable = false)
	private LocalDate targetDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ScheduleExceptionType type;

	@Column(name = "start_time")
	private LocalTime startTime;

	@Column(name = "end_time")
	private LocalTime endTime;

	@Column(length = 255)
	private String reason;

	@Column(name = "created_by_member_id")
	private UUID createdByMemberId;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ScheduleException() {
	}

	public ScheduleException(UUID id, UUID storeId, LocalDate targetDate, ScheduleExceptionType type,
		LocalTime startTime, LocalTime endTime, String reason, UUID createdByMemberId) {
		this.id = id;
		this.storeId = storeId;
		this.targetDate = targetDate;
		this.type = type;
		this.startTime = startTime;
		this.endTime = endTime;
		this.reason = reason;
		this.createdByMemberId = createdByMemberId;
	}

	public LocalDate getTargetDate() {
		return targetDate;
	}

	public ScheduleExceptionType getType() {
		return type;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}
}
