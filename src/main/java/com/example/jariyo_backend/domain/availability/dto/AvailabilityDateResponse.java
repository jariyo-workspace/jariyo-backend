package com.example.jariyo_backend.domain.availability.dto;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityDateResponse(
	LocalDate date,
	List<AvailabilitySlotResponse> slots
) {
}
