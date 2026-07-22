package com.example.jariyo_backend.domain.availability.dto;

import java.util.List;
import java.util.UUID;

public record AvailabilityResponse(
	UUID storeId,
	UUID serviceId,
	UUID staffId,
	List<AvailabilityDateResponse> dates
) {
}
