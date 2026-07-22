package com.example.jariyo_backend.domain.store.entity;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "store_policy")
public class StorePolicy {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "store_id", nullable = false, unique = true)
	private Store store;

	@Column(name = "booking_open_days", nullable = false)
	private int bookingOpenDays;

	@Column(name = "minimum_booking_notice_minutes", nullable = false)
	private int minimumBookingNoticeMinutes;

	@Column(name = "cancellation_deadline_minutes", nullable = false)
	private int cancellationDeadlineMinutes;

	@Column(name = "check_in_open_before_minutes", nullable = false)
	private int checkInOpenBeforeMinutes;

	@Column(name = "late_tolerance_minutes", nullable = false)
	private int lateToleranceMinutes;

	@Column(name = "no_show_after_minutes", nullable = false)
	private int noShowAfterMinutes;

	@Column(name = "reservation_hold_minutes", nullable = false)
	private int reservationHoldMinutes;

	@Column(name = "slot_offer_expiration_minutes", nullable = false)
	private int slotOfferExpirationMinutes;

	@Column(name = "walk_in_call_timeout_minutes", nullable = false)
	private int walkInCallTimeoutMinutes;

	@Column(name = "waitlist_enabled", nullable = false)
	private boolean waitlistEnabled;

	@Column(name = "walk_in_enabled", nullable = false)
	private boolean walkInEnabled;

	@Column(name = "auto_no_show_enabled", nullable = false)
	private boolean autoNoShowEnabled;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected StorePolicy() {
	}

	public StorePolicy(UUID id, UUID storeId, int bookingOpenDays, int minimumBookingNoticeMinutes,
		int cancellationDeadlineMinutes, int checkInOpenBeforeMinutes, int lateToleranceMinutes,
		int noShowAfterMinutes, int reservationHoldMinutes, int slotOfferExpirationMinutes,
		int walkInCallTimeoutMinutes, boolean waitlistEnabled, boolean walkInEnabled, boolean autoNoShowEnabled) {
		this.id = id;
		this.store = new Store(storeId);
		this.bookingOpenDays = bookingOpenDays;
		this.minimumBookingNoticeMinutes = minimumBookingNoticeMinutes;
		this.cancellationDeadlineMinutes = cancellationDeadlineMinutes;
		this.checkInOpenBeforeMinutes = checkInOpenBeforeMinutes;
		this.lateToleranceMinutes = lateToleranceMinutes;
		this.noShowAfterMinutes = noShowAfterMinutes;
		this.reservationHoldMinutes = reservationHoldMinutes;
		this.slotOfferExpirationMinutes = slotOfferExpirationMinutes;
		this.walkInCallTimeoutMinutes = walkInCallTimeoutMinutes;
		this.waitlistEnabled = waitlistEnabled;
		this.walkInEnabled = walkInEnabled;
		this.autoNoShowEnabled = autoNoShowEnabled;
	}

	public int getBookingOpenDays() {
		return bookingOpenDays;
	}

	public int getMinimumBookingNoticeMinutes() {
		return minimumBookingNoticeMinutes;
	}

	public int getCancellationDeadlineMinutes() {
		return cancellationDeadlineMinutes;
	}

	public int getCheckInOpenBeforeMinutes() {
		return checkInOpenBeforeMinutes;
	}

	public int getLateToleranceMinutes() {
		return lateToleranceMinutes;
	}

	public int getNoShowAfterMinutes() {
		return noShowAfterMinutes;
	}

	public int getReservationHoldMinutes() {
		return reservationHoldMinutes;
	}

	public int getSlotOfferExpirationMinutes() {
		return slotOfferExpirationMinutes;
	}

	public int getWalkInCallTimeoutMinutes() {
		return walkInCallTimeoutMinutes;
	}

	public boolean isWaitlistEnabled() {
		return waitlistEnabled;
	}

	public boolean isWalkInEnabled() {
		return walkInEnabled;
	}

	public boolean isAutoNoShowEnabled() {
		return autoNoShowEnabled;
	}
}
