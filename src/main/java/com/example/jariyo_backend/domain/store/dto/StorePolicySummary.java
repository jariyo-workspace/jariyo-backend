package com.example.jariyo_backend.domain.store.dto;

import com.example.jariyo_backend.domain.store.entity.StorePolicy;

public record StorePolicySummary(int bookingOpenDays, int minimumBookingNoticeMinutes,
	int cancellationDeadlineMinutes, int checkInOpenBeforeMinutes, int lateToleranceMinutes, int noShowAfterMinutes,
	int reservationHoldMinutes, int slotOfferExpirationMinutes, int walkInCallTimeoutMinutes, boolean waitlistEnabled,
	boolean walkInEnabled, boolean autoNoShowEnabled) {
	public static StorePolicySummary from(StorePolicy policy) {
		return new StorePolicySummary(policy.getBookingOpenDays(), policy.getMinimumBookingNoticeMinutes(),
			policy.getCancellationDeadlineMinutes(), policy.getCheckInOpenBeforeMinutes(),
			policy.getLateToleranceMinutes(), policy.getNoShowAfterMinutes(), policy.getReservationHoldMinutes(),
			policy.getSlotOfferExpirationMinutes(), policy.getWalkInCallTimeoutMinutes(),
			policy.isWaitlistEnabled(), policy.isWalkInEnabled(), policy.isAutoNoShowEnabled());
	}
}
