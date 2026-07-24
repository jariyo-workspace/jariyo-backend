package com.example.jariyo_backend.domain.reservation.controller;

import java.util.UUID;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.common.idempotency.IdempotencyKey;
import com.example.jariyo_backend.domain.reservation.service.ReservationService;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.CancelReservationCommand;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.CancelReservationResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {
	private final ReservationService reservationService;

	public ReservationController(ReservationService reservationService) {
		this.reservationService = reservationService;
	}

	@PostMapping("/{reservationId}/cancel")
	public ResponseEntity<ApiResponse<CancelReservationResult>> cancelMine(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID reservationId, @RequestHeader(IdempotencyKey.HEADER_NAME) String key,
		@Valid @RequestBody CancelReservationRequest request) {
		return ResponseEntity.ok(ApiResponse.success(reservationService.cancelMine(userId(jwt), reservationId, key,
			new CancelReservationCommand(request.reasonCode(), request.reason()))));
	}

	private UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

	public record CancelReservationRequest(@NotBlank @Size(max = 100) String reasonCode,
		@NotBlank @Size(max = 500) String reason) { }
}
