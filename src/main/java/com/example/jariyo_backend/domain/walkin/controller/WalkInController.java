package com.example.jariyo_backend.domain.walkin.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.common.idempotency.IdempotencyKey;
import com.example.jariyo_backend.domain.user.support.AuthenticatedUser;
import com.example.jariyo_backend.domain.walkin.entity.WalkInStatus;
import com.example.jariyo_backend.domain.walkin.service.WalkInService;
import com.example.jariyo_backend.domain.walkin.service.WalkInService.AdminWalkInSummary;
import com.example.jariyo_backend.domain.walkin.service.WalkInService.CallCommand;
import com.example.jariyo_backend.domain.walkin.service.WalkInService.CallResponse;
import com.example.jariyo_backend.domain.walkin.service.WalkInService.CallResponseCommand;
import com.example.jariyo_backend.domain.walkin.service.WalkInService.CompleteServiceCommand;
import com.example.jariyo_backend.domain.walkin.service.WalkInService.ReasonCommand;
import com.example.jariyo_backend.domain.walkin.service.WalkInService.RegisterCustomerCommand;
import com.example.jariyo_backend.domain.walkin.service.WalkInService.RegisterGuestCommand;
import com.example.jariyo_backend.domain.walkin.service.WalkInService.CompleteServiceResult;
import com.example.jariyo_backend.domain.walkin.service.WalkInService.StartServiceResult;
import com.example.jariyo_backend.domain.walkin.service.WalkInService.StartServiceCommand;
import com.example.jariyo_backend.domain.walkin.service.WalkInService.WalkInAvailability;
import com.example.jariyo_backend.domain.walkin.service.WalkInService.WalkInDetail;
import com.example.jariyo_backend.domain.walkin.service.WalkInService.WalkInSummary;
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
public class WalkInController {
	private final WalkInService walkInService;

	public WalkInController(WalkInService walkInService) {
		this.walkInService = walkInService;
	}

	@GetMapping("/stores/{storeId}/walk-in-status")
	public ResponseEntity<ApiResponse<WalkInAvailability>> getAvailability(@PathVariable UUID storeId,
		@RequestParam(required = false) UUID serviceId, @RequestParam(required = false) UUID staffId) {
		return ok(walkInService.getAvailability(storeId, serviceId, staffId));
	}

	@PostMapping("/walk-ins")
	public ResponseEntity<ApiResponse<WalkInSummary>> register(@AuthenticationPrincipal Jwt jwt,
		@RequestHeader(IdempotencyKey.HEADER_NAME) String key, @Valid @RequestBody RegisterRequest request) {
		return ok(walkInService.registerCustomer(userId(jwt), key, new RegisterCustomerCommand(request.storeId(),
			request.serviceId(), request.preferredStaffId(), request.partySize())));
	}

	@GetMapping("/me/walk-ins")
	public ResponseEntity<ApiResponse<List<WalkInSummary>>> listMine(@AuthenticationPrincipal Jwt jwt,
		@RequestParam(required = false) WalkInStatus status,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return ok(walkInService.listMine(userId(jwt), status, date));
	}

	@GetMapping("/walk-ins/{walkInId}")
	public ResponseEntity<ApiResponse<WalkInDetail>> getMine(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID walkInId) {
		return ok(walkInService.getMine(userId(jwt), walkInId));
	}

	@PostMapping("/walk-ins/{walkInId}/cancel")
	public ResponseEntity<ApiResponse<WalkInSummary>> cancelMine(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID walkInId, @RequestHeader(IdempotencyKey.HEADER_NAME) String key,
		@Valid @RequestBody ReasonRequest request) {
		return ok(walkInService.cancelMine(userId(jwt), walkInId, key, new ReasonCommand(request.reason())));
	}

	@PostMapping("/walk-ins/{walkInId}/respond-call")
	public ResponseEntity<ApiResponse<WalkInSummary>> respondCall(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID walkInId, @RequestHeader(IdempotencyKey.HEADER_NAME) String key,
		@Valid @RequestBody RespondCallRequest request) {
		return ok(walkInService.respondCall(userId(jwt), walkInId, key, new CallResponseCommand(request.response())));
	}

	@GetMapping("/admin/stores/{storeId}/walk-ins")
	public ResponseEntity<ApiResponse<List<AdminWalkInSummary>>> listAdmin(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID storeId, @RequestParam(required = false) WalkInStatus status,
		@RequestParam(required = false) UUID serviceId, @RequestParam(required = false) UUID staffId,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return ok(walkInService.listAdmin(userId(jwt), storeId, status, serviceId, staffId, date));
	}

	@PostMapping("/admin/stores/{storeId}/walk-ins")
	public ResponseEntity<ApiResponse<WalkInSummary>> registerGuest(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID storeId, @RequestHeader(IdempotencyKey.HEADER_NAME) String key,
		@Valid @RequestBody AdminRegisterRequest request) {
		return ok(walkInService.registerGuest(userId(jwt), key, storeId, new RegisterGuestCommand(
			request.customer().guestName(), request.customer().guestPhoneNumber(), request.serviceId(),
			request.preferredStaffId(), request.partySize())));
	}

	@PostMapping("/admin/stores/{storeId}/walk-ins/{walkInId}/call")
	public ResponseEntity<ApiResponse<WalkInSummary>> call(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID storeId, @PathVariable UUID walkInId,
		@RequestHeader(IdempotencyKey.HEADER_NAME) String key, @Valid @RequestBody CallRequest request) {
		return ok(walkInService.call(userId(jwt), storeId, walkInId, key,
			new CallCommand(request.responseTimeoutMinutes()), false));
	}

	@PostMapping("/admin/stores/{storeId}/walk-ins/{walkInId}/recall")
	public ResponseEntity<ApiResponse<WalkInSummary>> recall(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID storeId, @PathVariable UUID walkInId,
		@RequestHeader(IdempotencyKey.HEADER_NAME) String key, @Valid @RequestBody CallRequest request) {
		return ok(walkInService.call(userId(jwt), storeId, walkInId, key,
			new CallCommand(request.responseTimeoutMinutes()), true));
	}

	@PostMapping("/admin/stores/{storeId}/walk-ins/{walkInId}/check-in")
	public ResponseEntity<ApiResponse<WalkInSummary>> checkIn(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID storeId, @PathVariable UUID walkInId,
		@RequestHeader(IdempotencyKey.HEADER_NAME) String key) {
		return ok(walkInService.checkInAdmin(userId(jwt), storeId, walkInId, key));
	}

	@PostMapping("/admin/stores/{storeId}/walk-ins/{walkInId}/skip")
	public ResponseEntity<ApiResponse<WalkInSummary>> skip(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID storeId, @PathVariable UUID walkInId,
		@RequestHeader(IdempotencyKey.HEADER_NAME) String key, @Valid @RequestBody ReasonRequest request) {
		return ok(walkInService.skip(userId(jwt), storeId, walkInId, key, new ReasonCommand(request.reason())));
	}

	@PostMapping("/admin/stores/{storeId}/walk-ins/{walkInId}/cancel")
	public ResponseEntity<ApiResponse<WalkInSummary>> cancelAdmin(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID storeId, @PathVariable UUID walkInId,
		@RequestHeader(IdempotencyKey.HEADER_NAME) String key, @Valid @RequestBody ReasonRequest request) {
		return ok(walkInService.cancelAdmin(userId(jwt), storeId, walkInId, key, new ReasonCommand(request.reason())));
	}

	@PostMapping("/admin/stores/{storeId}/walk-ins/{walkInId}/restore")
	public ResponseEntity<ApiResponse<WalkInSummary>> restore(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID storeId, @PathVariable UUID walkInId,
		@RequestHeader(IdempotencyKey.HEADER_NAME) String key) {
		return ok(walkInService.restore(userId(jwt), storeId, walkInId, key));
	}

	@PostMapping("/admin/stores/{storeId}/walk-ins/{walkInId}/mark-no-show")
	public ResponseEntity<ApiResponse<WalkInSummary>> markNoShow(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID storeId, @PathVariable UUID walkInId,
		@RequestHeader(IdempotencyKey.HEADER_NAME) String key, @Valid @RequestBody ReasonRequest request) {
		return ok(walkInService.markNoShow(userId(jwt), storeId, walkInId, key, new ReasonCommand(request.reason())));
	}

	@PostMapping("/admin/stores/{storeId}/walk-ins/{walkInId}/start-service")
	public ResponseEntity<ApiResponse<StartServiceResult>> startService(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID storeId, @PathVariable UUID walkInId,
		@RequestHeader(IdempotencyKey.HEADER_NAME) String key, @Valid @RequestBody StartServiceRequest request) {
		return ok(walkInService.startService(userId(jwt), storeId, walkInId, key,
			new StartServiceCommand(request.staffId())));
	}

	@PostMapping("/admin/stores/{storeId}/service-sessions/{sessionId}/complete")
	public ResponseEntity<ApiResponse<CompleteServiceResult>> completeService(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID storeId, @PathVariable UUID sessionId,
		@RequestHeader(IdempotencyKey.HEADER_NAME) String key, @Valid @RequestBody CompleteServiceRequest request) {
		return ok(walkInService.completeService(userId(jwt), storeId, sessionId, key,
			new CompleteServiceCommand(request.completionNote())));
	}

	private UUID userId(Jwt jwt) {
		return AuthenticatedUser.from(jwt).id();
	}

	private <T> ResponseEntity<ApiResponse<T>> ok(T data) {
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	public record RegisterRequest(@NotNull UUID storeId, @NotNull UUID serviceId, UUID preferredStaffId,
		@Min(1) @Max(20) int partySize) { }
	public record GuestCustomer(@NotBlank @Size(max = 100) String guestName,
		@NotBlank @Size(max = 32) String guestPhoneNumber) { }
	public record AdminRegisterRequest(@NotNull @Valid GuestCustomer customer, @NotNull UUID serviceId,
		UUID preferredStaffId, @Min(1) @Max(20) int partySize) { }
	public record ReasonRequest(@NotBlank @Size(max = 500) String reason) { }
	public record RespondCallRequest(@NotNull CallResponse response) { }
	public record CallRequest(@Min(1) @Max(60) Integer responseTimeoutMinutes) { }
	public record StartServiceRequest(@NotNull UUID staffId) { }
	public record CompleteServiceRequest(@NotBlank @Size(max = 1000) String completionNote) { }
}
