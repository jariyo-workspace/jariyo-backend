package com.example.jariyo_backend.domain.admin.controller;

import java.util.UUID;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.common.api.ResponseSupport;
import com.example.jariyo_backend.common.idempotency.IdempotencyKey;
import com.example.jariyo_backend.domain.admin.service.ServiceSessionCommandService;
import com.example.jariyo_backend.domain.reservation.service.ReservationAdminService;
import com.example.jariyo_backend.domain.user.support.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/api/v1/admin/stores/{storeId}")
public class AdminReservationCommandController {
	private final ReservationAdminService reservationAdminService;
	private final ServiceSessionCommandService serviceSessionCommandService;

	public AdminReservationCommandController(ReservationAdminService reservationAdminService,
		ServiceSessionCommandService serviceSessionCommandService) {
		this.reservationAdminService = reservationAdminService;
		this.serviceSessionCommandService = serviceSessionCommandService;
	}

	@PostMapping("/reservations/{reservationId}/check-in")
	public ResponseEntity<ApiResponse<ReservationAdminService.ReservationCheckInResult>> checkIn(
		@AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId, @PathVariable UUID reservationId,
		@RequestHeader(IdempotencyKey.HEADER_NAME) String key) {
		return ResponseSupport.ok(reservationAdminService.checkIn(userId(jwt), storeId, reservationId, key));
	}

	@PostMapping("/reservations/{reservationId}/mark-no-show")
	public ResponseEntity<ApiResponse<ReservationAdminService.ReservationNoShowResult>> markNoShow(
		@AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId, @PathVariable UUID reservationId,
		@RequestHeader(IdempotencyKey.HEADER_NAME) String key, @Valid @RequestBody ReasonRequest request) {
		return ResponseSupport.ok(reservationAdminService.markNoShow(userId(jwt), storeId, reservationId, key,
			new ReservationAdminService.ReservationNoShowCommand(request.reason())));
	}

	@PostMapping("/reservations/{reservationId}/start-service")
	public ResponseEntity<ApiResponse<ReservationAdminService.StartReservationServiceResult>> startService(
		@AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId, @PathVariable UUID reservationId,
		@RequestHeader(IdempotencyKey.HEADER_NAME) String key, @Valid @RequestBody StartServiceRequest request) {
		return ResponseSupport.ok(reservationAdminService.startService(userId(jwt), storeId, reservationId, key,
			new ReservationAdminService.StartReservationServiceCommand(request.staffId())));
	}

	@PostMapping("/service-sessions/{sessionId}/complete")
	public ResponseEntity<ApiResponse<ServiceSessionCommandService.CompleteServiceResult>> completeService(
		@AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId, @PathVariable UUID sessionId,
		@RequestHeader(IdempotencyKey.HEADER_NAME) String key, @Valid @RequestBody CompleteServiceRequest request) {
		return ResponseSupport.ok(serviceSessionCommandService.completeService(userId(jwt), storeId, sessionId, key,
			new ServiceSessionCommandService.CompleteServiceCommand(request.completionNote())));
	}

	private UUID userId(Jwt jwt) {
		return AuthenticatedUser.from(jwt).id();
	}

	public record ReasonRequest(@NotBlank @Size(max = 255) String reason) { }

	public record StartServiceRequest(@NotNull UUID staffId) { }

	public record CompleteServiceRequest(@Size(max = 1000) String completionNote) { }
}
