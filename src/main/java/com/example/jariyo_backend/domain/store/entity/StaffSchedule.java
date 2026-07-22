package com.example.jariyo_backend.domain.store.entity;

import java.time.DayOfWeek;
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
@Table(name = "staff_schedule")
public class StaffSchedule {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "store_member_id", nullable = false)
	private UUID storeMemberId;

	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false, length = 16)
	private DayOfWeek dayOfWeek;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	@Column(name = "valid_from", nullable = false)
	private LocalDate validFrom;

	@Column(name = "valid_until")
	private LocalDate validUntil;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected StaffSchedule() {
	}

	public StaffSchedule(UUID id, UUID storeMemberId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
		LocalDate validFrom, LocalDate validUntil) {
		this.id = id;
		this.storeMemberId = storeMemberId;
		this.dayOfWeek = dayOfWeek;
		this.startTime = startTime;
		this.endTime = endTime;
		this.validFrom = validFrom;
		this.validUntil = validUntil;
	}

	public UUID getStoreMemberId() {
		return storeMemberId;
	}

	public DayOfWeek getDayOfWeek() {
		return dayOfWeek;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public LocalDate getValidFrom() {
		return validFrom;
	}

	public LocalDate getValidUntil() {
		return validUntil;
	}
}
