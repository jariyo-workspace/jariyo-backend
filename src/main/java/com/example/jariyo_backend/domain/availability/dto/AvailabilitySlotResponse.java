package com.example.jariyo_backend.domain.availability.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AvailabilitySlotResponse(
	OffsetDateTime startAt,
	OffsetDateTime serviceEndAt,
	OffsetDateTime occupiedUntil,
	UUID staffId,
	AvailabilitySlotStatus status
) {
}
