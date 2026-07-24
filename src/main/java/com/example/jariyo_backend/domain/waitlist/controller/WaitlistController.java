package com.example.jariyo_backend.domain.waitlist.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.common.idempotency.IdempotencyKey;
import com.example.jariyo_backend.domain.waitlist.entity.SlotOfferStatus;
import com.example.jariyo_backend.domain.waitlist.entity.StaffPreferenceType;
import com.example.jariyo_backend.domain.waitlist.entity.WaitlistStatus;
import com.example.jariyo_backend.domain.waitlist.service.WaitlistService;
import com.example.jariyo_backend.domain.waitlist.service.WaitlistService.AcceptSlotOfferResult;
import com.example.jariyo_backend.domain.waitlist.service.WaitlistService.CancelWaitlistCommand;
import com.example.jariyo_backend.domain.waitlist.service.WaitlistService.CreateWaitlistCommand;
import com.example.jariyo_backend.domain.waitlist.service.WaitlistService.SlotOfferDetail;
import com.example.jariyo_backend.domain.waitlist.service.WaitlistService.SlotOfferSummary;
import com.example.jariyo_backend.domain.waitlist.service.WaitlistService.WaitlistCancelResult;
import com.example.jariyo_backend.domain.waitlist.service.WaitlistService.WaitlistDetail;
import com.example.jariyo_backend.domain.waitlist.service.WaitlistService.WaitlistSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class WaitlistController {
	private final WaitlistService waitlistService;

	public WaitlistController(WaitlistService waitlistService) {
		this.waitlistService = waitlistService;
	}

	@PostMapping("/waitlists")
	public ResponseEntity<ApiResponse<WaitlistSummary>> create(@AuthenticationPrincipal Jwt jwt,
		@RequestHeader(IdempotencyKey.HEADER_NAME) String key, @Valid @RequestBody CreateWaitlistRequest request) {
		return ok(waitlistService.create(userId(jwt), key, new CreateWaitlistCommand(request.storeId(), request.serviceId(),
			request.preferredStaffId(), request.staffPreferenceType(), request.desiredDate(),
			request.acceptableStartTime(), request.acceptableEndTime(), request.partySize())));
	}

	@GetMapping("/me/waitlists")
	public ResponseEntity<ApiResponse<List<WaitlistSummary>>> listMine(@AuthenticationPrincipal Jwt jwt,
		@RequestParam(required = false) WaitlistStatus status,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return ok(waitlistService.listMine(userId(jwt), status, from, to));
	}

	@GetMapping("/waitlists/{waitlistId}")
	public ResponseEntity<ApiResponse<WaitlistDetail>> getMine(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID waitlistId) {
		return ok(waitlistService.getMine(userId(jwt), waitlistId));
	}

	@PostMapping("/waitlists/{waitlistId}/cancel")
	public ResponseEntity<ApiResponse<WaitlistCancelResult>> cancelMine(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID waitlistId, @RequestHeader(IdempotencyKey.HEADER_NAME) String key,
		@Valid @RequestBody CancelWaitlistRequest request) {
		return ok(waitlistService.cancelMine(userId(jwt), waitlistId, key, new CancelWaitlistCommand(request.reason())));
	}

	@GetMapping("/me/slot-offers")
	public ResponseEntity<ApiResponse<List<SlotOfferSummary>>> listOffers(@AuthenticationPrincipal Jwt jwt,
		@RequestParam(required = false) SlotOfferStatus status) {
		return ok(waitlistService.listOffers(userId(jwt), status));
	}

	@GetMapping("/slot-offers/{offerId}")
	public ResponseEntity<ApiResponse<SlotOfferDetail>> getOffer(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID offerId) {
		return ok(waitlistService.getOffer(userId(jwt), offerId));
	}

	@PostMapping("/slot-offers/{offerId}/accept")
	public ResponseEntity<ApiResponse<AcceptSlotOfferResult>> accept(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID offerId, @RequestHeader(IdempotencyKey.HEADER_NAME) String key) {
		return ok(waitlistService.accept(userId(jwt), offerId, key));
	}

	private UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

	private <T> ResponseEntity<ApiResponse<T>> ok(T data) {
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	public record CreateWaitlistRequest(@NotNull UUID storeId, @NotNull UUID serviceId, UUID preferredStaffId,
		@NotNull StaffPreferenceType staffPreferenceType,
		@NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desiredDate,
		@NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime acceptableStartTime,
		@NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime acceptableEndTime,
		@Min(1) @Max(20) int partySize) { }

	public record CancelWaitlistRequest(@NotBlank String reason) { }
}
