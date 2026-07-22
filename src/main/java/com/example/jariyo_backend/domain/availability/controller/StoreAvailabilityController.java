package com.example.jariyo_backend.domain.availability.controller;

import java.time.LocalDate;
import java.util.UUID;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.common.api.ResponseSupport;
import com.example.jariyo_backend.domain.availability.dto.AvailabilityResponse;
import com.example.jariyo_backend.domain.availability.service.AvailabilityService;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/stores")
public class StoreAvailabilityController {
	private final AvailabilityService availabilityService;

	public StoreAvailabilityController(AvailabilityService availabilityService) {
		this.availabilityService = availabilityService;
	}

	@GetMapping("/{storeId}/availability")
	public ResponseEntity<ApiResponse<AvailabilityResponse>> getAvailability(
		@PathVariable UUID storeId,
		@RequestParam UUID serviceId,
		@RequestParam(required = false) UUID staffId,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
		@RequestParam @Min(1) int partySize
	) {
		return ResponseSupport.ok(availabilityService.getAvailability(storeId, serviceId, staffId, from, to, partySize));
	}
}
