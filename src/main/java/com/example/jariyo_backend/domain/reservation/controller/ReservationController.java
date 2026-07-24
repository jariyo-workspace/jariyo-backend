package com.example.jariyo_backend.domain.reservation.controller;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.common.api.ResponseSupport;
import com.example.jariyo_backend.common.idempotency.IdempotencyKey;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.service.ReservationService;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.CancelReservationCommand;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.CancelReservationResult;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.CreateReservationCommand;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.ReservationCreateResult;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.ReservationDetail;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.ReservationHistoryResult;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.ReservationSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReservationController {
	private final ReservationService reservationService;

	public ReservationController(ReservationService reservationService) {
		this.reservationService = reservationService;
	}

	@PostMapping("/reservations")
	public ResponseEntity<ApiResponse<ReservationCreateResult>> create(@AuthenticationPrincipal Jwt jwt,
		@RequestHeader(IdempotencyKey.HEADER_NAME) String key, @Valid @RequestBody CreateReservationRequest request) {
		return ResponseSupport.created(reservationService.create(userId(jwt), key,
			new CreateReservationCommand(request.storeId(), request.serviceId(), request.staffId(), request.startAt(),
				request.partySize(), request.customerNote())));
	}

	@GetMapping("/me/reservations")
	public ResponseEntity<ApiResponse<List<ReservationSummary>>> listMine(@AuthenticationPrincipal Jwt jwt,
		@RequestParam(required = false) ReservationStatus status,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return ResponseSupport.ok(reservationService.listMine(userId(jwt), status, from, to));
	}

	@GetMapping("/reservations/{reservationId}")
	public ResponseEntity<ApiResponse<ReservationDetail>> getMine(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID reservationId) {
		return ResponseSupport.ok(reservationService.getMine(userId(jwt), reservationId));
	}

	@GetMapping("/reservations/{reservationId}/history")
	public ResponseEntity<ApiResponse<List<ReservationHistoryResult>>> historyMine(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID reservationId) {
		return ResponseSupport.ok(reservationService.historyMine(userId(jwt), reservationId));
	}

	@PostMapping("/reservations/{reservationId}/cancel")
	public ResponseEntity<ApiResponse<CancelReservationResult>> cancelMine(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID reservationId, @RequestHeader(IdempotencyKey.HEADER_NAME) String key,
		@Valid @RequestBody CancelReservationRequest request) {
		return ResponseSupport.ok(reservationService.cancelMine(userId(jwt), reservationId, key,
			new CancelReservationCommand(request.reason())));
	}

	private UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

	public record CreateReservationRequest(@NotNull UUID storeId, @NotNull UUID serviceId, @NotNull UUID staffId,
		@NotNull OffsetDateTime startAt, @Min(1) @Max(20) int partySize,
		@Size(max = 1000) String customerNote) { }

	public record CancelReservationRequest(@NotBlank @Size(max = 255) String reason) { }
}
