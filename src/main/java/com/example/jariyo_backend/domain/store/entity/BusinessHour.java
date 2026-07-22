package com.example.jariyo_backend.domain.store.entity;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "business_hour")
public class BusinessHour {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "store_id", nullable = false)
	private UUID storeId;

	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false, length = 16)
	private DayOfWeekValue dayOfWeek;

	@Column(name = "open_time")
	private LocalTime openTime;

	@Column(name = "close_time")
	private LocalTime closeTime;

	@Column(name = "is_closed", nullable = false)
	private boolean closed;

	protected BusinessHour() {
	}

	public BusinessHour(UUID id, UUID storeId, DayOfWeek dayOfWeek, LocalTime openTime, LocalTime closeTime,
		boolean closed) {
		this.id = id;
		this.storeId = storeId;
		this.dayOfWeek = DayOfWeekValue.valueOf(dayOfWeek.name());
		this.openTime = openTime;
		this.closeTime = closeTime;
		this.closed = closed;
	}

	public UUID getStoreId() {
		return storeId;
	}

	public DayOfWeekValue getDayOfWeek() {
		return dayOfWeek;
	}

	public LocalTime getOpenTime() {
		return openTime;
	}

	public LocalTime getCloseTime() {
		return closeTime;
	}

	public boolean isClosed() {
		return closed;
	}

	public UUID getId() {
		return id;
	}
}
