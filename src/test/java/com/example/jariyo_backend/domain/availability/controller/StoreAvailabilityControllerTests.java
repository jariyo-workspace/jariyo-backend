package com.example.jariyo_backend.domain.availability.controller;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.domain.availability.dto.AvailabilityDateResponse;
import com.example.jariyo_backend.domain.availability.dto.AvailabilityResponse;
import com.example.jariyo_backend.domain.availability.dto.AvailabilitySlotResponse;
import com.example.jariyo_backend.domain.availability.dto.AvailabilitySlotStatus;
import com.example.jariyo_backend.domain.availability.service.AvailabilityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreAvailabilityControllerTests {
	@Mock AvailabilityService availabilityService;

	@Test
	void returnsAvailabilityResponseWrappedInApiEnvelope() {
		UUID storeId = UUID.randomUUID();
		UUID serviceId = UUID.randomUUID();
		UUID staffId = UUID.randomUUID();
		LocalDate from = LocalDate.of(2026, 7, 23);
		AvailabilityResponse payload = new AvailabilityResponse(storeId, serviceId, staffId, List.of(
			new AvailabilityDateResponse(from, List.of(
				new AvailabilitySlotResponse(
					OffsetDateTime.of(2026, 7, 23, 14, 0, 0, 0, ZoneOffset.ofHours(9)),
					OffsetDateTime.of(2026, 7, 23, 14, 30, 0, 0, ZoneOffset.ofHours(9)),
					OffsetDateTime.of(2026, 7, 23, 14, 40, 0, 0, ZoneOffset.ofHours(9)),
					staffId,
					AvailabilitySlotStatus.AVAILABLE)))));
		when(availabilityService.getAvailability(storeId, serviceId, staffId, from, from, 1)).thenReturn(payload);
		StoreAvailabilityController controller = new StoreAvailabilityController(availabilityService);

		ResponseEntity<ApiResponse<AvailabilityResponse>> response = controller
			.getAvailability(storeId, serviceId, staffId, from, from, 1);

		assertEquals(200, response.getStatusCode().value());
		assertEquals(payload, response.getBody().data());
		verify(availabilityService).getAvailability(storeId, serviceId, staffId, from, from, 1);
	}
}
