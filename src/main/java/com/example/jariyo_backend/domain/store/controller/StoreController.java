package com.example.jariyo_backend.domain.store.controller;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.domain.store.service.StoreQueryService;
import com.example.jariyo_backend.domain.store.service.StoreSettingsService;
import com.example.jariyo_backend.domain.store.service.StoreSettingsService.BusinessDayCommand;
import com.example.jariyo_backend.domain.store.service.StoreSettingsService.PolicyCommand;
import com.example.jariyo_backend.domain.store.service.StoreSettingsService.ScheduleCommand;
import com.example.jariyo_backend.domain.store.service.StoreSettingsService.ServiceCommand;
import com.example.jariyo_backend.domain.store.service.StoreSettingsService.StaffAddCommand;
import com.example.jariyo_backend.domain.store.service.StoreSettingsService.StaffExceptionCommand;
import com.example.jariyo_backend.domain.store.service.StoreSettingsService.StaffServiceCommand;
import com.example.jariyo_backend.domain.store.service.StoreSettingsService.StaffUpdateCommand;
import com.example.jariyo_backend.domain.store.service.StoreSettingsService.StoreCommand;
import com.example.jariyo_backend.domain.store.service.StoreSettingsService.StoreExceptionCommand;
import com.example.jariyo_backend.domain.store.service.StoreSettingsService.TimePeriod;
import com.example.jariyo_backend.domain.store.entity.ScheduleExceptionType;
import com.example.jariyo_backend.domain.store.entity.StaffScheduleExceptionType;
import com.example.jariyo_backend.domain.user.entity.StoreMemberRole;
import com.example.jariyo_backend.domain.user.entity.StoreMemberStatus;
import com.example.jariyo_backend.domain.user.support.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StoreController {
	private final StoreQueryService storeQueryService;
	private final StoreSettingsService storeSettingsService;

	public StoreController(StoreQueryService storeQueryService, StoreSettingsService storeSettingsService) {
		this.storeQueryService = storeQueryService;
		this.storeSettingsService = storeSettingsService;
	}

	@GetMapping("/stores")
	public ResponseEntity<ApiResponse<List<StoreQueryService.StoreSummary>>> listStores() {
		return ResponseEntity.ok(ApiResponse.success(storeQueryService.listStores()));
	}

	@GetMapping("/stores/{storeId}")
	public ResponseEntity<ApiResponse<StoreQueryService.StoreDetail>> getStore(@PathVariable UUID storeId) {
		return ResponseEntity.ok(ApiResponse.success(storeQueryService.getStore(storeId)));
	}

	@GetMapping("/stores/{storeId}/services")
	public ResponseEntity<ApiResponse<List<StoreQueryService.ServiceSummary>>> listServices(@PathVariable UUID storeId,
		@RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly) {
		return ResponseEntity.ok(ApiResponse.success(storeQueryService.listServices(storeId, activeOnly)));
	}

	@GetMapping("/stores/{storeId}/services/{serviceId}/staff")
	public ResponseEntity<ApiResponse<List<StoreQueryService.ServiceStaffSummary>>> listServiceStaff(
		@PathVariable UUID storeId, @PathVariable UUID serviceId) {
		return ResponseEntity.ok(ApiResponse.success(storeQueryService.listServiceStaff(storeId, serviceId)));
	}

	@GetMapping("/admin/stores/{storeId}/staff")
	public ResponseEntity<ApiResponse<List<StoreQueryService.StoreMemberSummary>>> listAdminStaff(
		@PathVariable UUID storeId) {
		return ResponseEntity.ok(ApiResponse.success(storeQueryService.listAdminStaff(storeId)));
	}

	@GetMapping("/admin/stores/{storeId}/staff/{staffId}")
	public ResponseEntity<ApiResponse<StoreQueryService.StoreMemberDetail>> getAdminStaff(@PathVariable UUID storeId,
		@PathVariable UUID staffId) {
		return ResponseEntity.ok(ApiResponse.success(storeQueryService.getAdminStaff(storeId, staffId)));
	}

	@GetMapping("/admin/stores/{storeId}/staff/{staffId}/schedules")
	public ResponseEntity<ApiResponse<List<StoreQueryService.StaffScheduleSummary>>> listStaffSchedules(
		@PathVariable UUID storeId, @PathVariable UUID staffId) {
		return ResponseEntity.ok(ApiResponse.success(storeQueryService.listStaffSchedules(storeId, staffId)));
	}

	@GetMapping("/admin/stores/{storeId}/staff/{staffId}/schedule-exceptions")
	public ResponseEntity<ApiResponse<List<StoreQueryService.StaffScheduleExceptionSummary>>> listStaffScheduleExceptions(
		@PathVariable UUID storeId, @PathVariable UUID staffId) {
		return ResponseEntity.ok(ApiResponse.success(storeQueryService.listStaffScheduleExceptions(storeId, staffId)));
	}

	@GetMapping("/admin/stores/{storeId}/business-hours")
	public ResponseEntity<ApiResponse<List<StoreQueryService.BusinessHourSummary>>> listBusinessHours(
		@PathVariable UUID storeId) {
		return ResponseEntity.ok(ApiResponse.success(storeQueryService.listBusinessHours(storeId)));
	}

	@GetMapping("/admin/stores/{storeId}/schedule-exceptions")
	public ResponseEntity<ApiResponse<List<StoreQueryService.ScheduleExceptionSummary>>> listScheduleExceptions(
		@PathVariable UUID storeId) {
		return ResponseEntity.ok(ApiResponse.success(storeQueryService.listScheduleExceptions(storeId)));
	}

	@GetMapping("/admin/stores/{storeId}/policy")
	public ResponseEntity<ApiResponse<StoreQueryService.StorePolicySummary>> getPolicy(@PathVariable UUID storeId) {
		return ResponseEntity.ok(ApiResponse.success(storeQueryService.getPolicy(storeId)));
	}

	@PutMapping("/admin/stores/{storeId}")
	public ResponseEntity<ApiResponse<StoreQueryService.StoreSummary>> updateStore(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID storeId, @Valid @RequestBody StoreUpdateRequest request) {
		return ok(storeSettingsService.updateStore(userId(jwt), storeId,
			new StoreCommand(request.name(), request.description(), request.phoneNumber(), request.address())));
	}

	@PutMapping("/admin/stores/{storeId}/policy")
	public ResponseEntity<ApiResponse<StoreQueryService.StorePolicySummary>> updatePolicy(
		@AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId, @Valid @RequestBody PolicyUpdateRequest request) {
		return ok(storeSettingsService.updatePolicy(userId(jwt), storeId, new PolicyCommand(request.bookingOpenDays(),
			request.minimumBookingNoticeMinutes(), request.cancellationDeadlineMinutes(),
			request.checkInOpenBeforeMinutes(), request.lateToleranceMinutes(), request.noShowAfterMinutes(),
			request.reservationHoldMinutes(), request.slotOfferExpirationMinutes(), request.walkInCallTimeoutMinutes(),
			request.waitlistEnabled(), request.walkInEnabled(), request.autoNoShowEnabled())));
	}

	@PostMapping("/admin/stores/{storeId}/services")
	public ResponseEntity<ApiResponse<StoreQueryService.ServiceSummary>> createService(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID storeId, @Valid @RequestBody ServiceRequest request) {
		return created(storeSettingsService.createService(userId(jwt), storeId, request.toCommand()));
	}

	@PutMapping("/admin/stores/{storeId}/services/{serviceId}")
	public ResponseEntity<ApiResponse<StoreQueryService.ServiceSummary>> updateService(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID storeId, @PathVariable UUID serviceId, @Valid @RequestBody ServiceRequest request) {
		return ok(storeSettingsService.updateService(userId(jwt), storeId, serviceId, request.toCommand()));
	}

	@PostMapping("/admin/stores/{storeId}/services/{serviceId}/activate")
	public ResponseEntity<ApiResponse<StoreQueryService.ServiceSummary>> activateService(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID storeId, @PathVariable UUID serviceId) {
		return ok(storeSettingsService.activateService(userId(jwt), storeId, serviceId));
	}

	@PostMapping("/admin/stores/{storeId}/services/{serviceId}/deactivate")
	public ResponseEntity<ApiResponse<StoreSettingsService.UpdateResult>> deactivateService(
		@AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId, @PathVariable UUID serviceId) {
		return ok(storeSettingsService.deactivateService(userId(jwt), storeId, serviceId));
	}

	@PostMapping("/admin/stores/{storeId}/staff")
	public ResponseEntity<ApiResponse<StoreQueryService.StoreMemberDetail>> addStaff(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID storeId, @Valid @RequestBody StaffAddRequest request) {
		return created(storeSettingsService.addStaff(userId(jwt), storeId,
			new StaffAddCommand(request.email(), request.role(), request.displayName())));
	}

	@PutMapping("/admin/stores/{storeId}/staff/{staffId}")
	public ResponseEntity<ApiResponse<StoreSettingsService.UpdateResult>> updateStaff(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID storeId, @PathVariable UUID staffId, @Valid @RequestBody StaffUpdateRequest request) {
		return ok(storeSettingsService.updateStaff(userId(jwt), storeId, staffId,
			new StaffUpdateCommand(request.displayName(), request.role(), request.bookingEnabled(), request.status())));
	}

	@PostMapping("/admin/stores/{storeId}/staff/{staffId}/deactivate")
	public ResponseEntity<ApiResponse<StoreSettingsService.UpdateResult>> deactivateStaff(
		@AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId, @PathVariable UUID staffId) {
		return ok(storeSettingsService.deactivateStaff(userId(jwt), storeId, staffId));
	}

	@PutMapping("/admin/stores/{storeId}/staff/{staffId}/services")
	public ResponseEntity<ApiResponse<StoreSettingsService.UpdateResult>> replaceStaffServices(
		@AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId, @PathVariable UUID staffId,
		@Valid @RequestBody StaffServicesRequest request) {
		return ok(storeSettingsService.replaceStaffServices(userId(jwt), storeId, staffId,
			request.services().stream().map(StaffServiceRequest::toCommand).toList()));
	}

	@PutMapping("/admin/stores/{storeId}/staff/{staffId}/schedules")
	public ResponseEntity<ApiResponse<StoreSettingsService.UpdateResult>> replaceStaffSchedules(
		@AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId, @PathVariable UUID staffId,
		@Valid @RequestBody StaffSchedulesRequest request) {
		return ok(storeSettingsService.replaceStaffSchedules(userId(jwt), storeId, staffId,
			request.schedules().stream().map(StaffScheduleRequest::toCommand).toList()));
	}

	@PostMapping("/admin/stores/{storeId}/staff/{staffId}/schedule-exceptions")
	public ResponseEntity<ApiResponse<StoreSettingsService.UpdateResult>> createStaffScheduleException(
		@AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId, @PathVariable UUID staffId,
		@Valid @RequestBody StaffExceptionRequest request) {
		return ok(storeSettingsService.createStaffException(userId(jwt), storeId, staffId, request.toCommand()));
	}

	@DeleteMapping("/admin/stores/{storeId}/staff/{staffId}/schedule-exceptions/{exceptionId}")
	public ResponseEntity<ApiResponse<StoreSettingsService.UpdateResult>> deleteStaffScheduleException(
		@AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId, @PathVariable UUID staffId,
		@PathVariable UUID exceptionId) {
		return ok(storeSettingsService.deleteStaffException(userId(jwt), storeId, staffId, exceptionId));
	}

	@PutMapping("/admin/stores/{storeId}/business-hours")
	public ResponseEntity<ApiResponse<StoreSettingsService.UpdateResult>> replaceBusinessHours(
		@AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId,
		@Valid @RequestBody BusinessHoursRequest request) {
		return ok(storeSettingsService.replaceBusinessHours(userId(jwt), storeId,
			request.businessHours().stream().map(BusinessDayRequest::toCommand).toList()));
	}

	@PostMapping("/admin/stores/{storeId}/schedule-exceptions")
	public ResponseEntity<ApiResponse<StoreSettingsService.UpdateResult>> createStoreScheduleException(
		@AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId,
		@Valid @RequestBody StoreExceptionRequest request) {
		return ok(storeSettingsService.createStoreException(userId(jwt), storeId, request.toCommand()));
	}

	@DeleteMapping("/admin/stores/{storeId}/schedule-exceptions/{exceptionId}")
	public ResponseEntity<ApiResponse<StoreSettingsService.UpdateResult>> deleteStoreScheduleException(
		@AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId, @PathVariable UUID exceptionId) {
		return ok(storeSettingsService.deleteStoreException(userId(jwt), storeId, exceptionId));
	}

	private UUID userId(Jwt jwt) {
		return AuthenticatedUser.from(jwt).id();
	}

	private <T> ResponseEntity<ApiResponse<T>> ok(T data) {
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	private <T> ResponseEntity<ApiResponse<T>> created(T data) {
		return ResponseEntity.status(201).body(ApiResponse.success(data));
	}

	public record StoreUpdateRequest(@NotBlank @Size(max = 200) String name,
		@Size(max = 1000) String description, @NotBlank @Size(max = 32) String phoneNumber,
		@NotBlank @Size(max = 500) String address) { }

	public record ServiceRequest(@NotBlank @Size(max = 120) String name, @Size(max = 1000) String description,
		@Positive int durationMinutes, @Min(0) int cleanupMinutes, @Positive int capacity) {
		ServiceCommand toCommand() {
			return new ServiceCommand(name, description, durationMinutes, cleanupMinutes, capacity);
		}
	}

	public record PolicyUpdateRequest(@Min(0) int bookingOpenDays, @Min(0) int minimumBookingNoticeMinutes,
		@Min(0) int cancellationDeadlineMinutes, @Min(0) int checkInOpenBeforeMinutes,
		@Min(0) int lateToleranceMinutes, @Min(0) int noShowAfterMinutes,
		@Positive int reservationHoldMinutes, @Positive int slotOfferExpirationMinutes,
		@Positive int walkInCallTimeoutMinutes, boolean waitlistEnabled, boolean walkInEnabled,
		boolean autoNoShowEnabled) { }

	public record StaffAddRequest(@NotBlank @Email @Size(max = 320) String email,
		@NotNull StoreMemberRole role, @NotBlank @Size(max = 100) String displayName) { }

	public record StaffUpdateRequest(@NotBlank @Size(max = 100) String displayName,
		@NotNull StoreMemberRole role, boolean bookingEnabled, @NotNull StoreMemberStatus status) { }

	public record StaffServicesRequest(@NotNull @Valid List<StaffServiceRequest> services) { }
	public record StaffServiceRequest(@NotNull UUID serviceId, boolean active,
		@Positive Integer customDurationMinutes) {
		StaffServiceCommand toCommand() {
			return new StaffServiceCommand(serviceId, active, customDurationMinutes);
		}
	}

	public record StaffSchedulesRequest(@NotNull @Valid List<StaffScheduleRequest> schedules) { }
	public record StaffScheduleRequest(@NotNull DayOfWeek dayOfWeek, @NotEmpty @Valid List<TimePeriodRequest> periods,
		@NotNull LocalDate validFrom, LocalDate validUntil) {
		ScheduleCommand toCommand() {
			return new ScheduleCommand(dayOfWeek, periods.stream().map(TimePeriodRequest::toCommand).toList(),
				validFrom, validUntil);
		}
	}

	public record BusinessHoursRequest(@NotNull @Valid List<BusinessDayRequest> businessHours) { }
	public record BusinessDayRequest(@NotNull DayOfWeek dayOfWeek, boolean isClosed,
		@NotNull @Valid List<BusinessPeriodRequest> periods) {
		BusinessDayCommand toCommand() {
			return new BusinessDayCommand(dayOfWeek, isClosed,
				periods.stream().map(BusinessPeriodRequest::toCommand).toList());
		}
	}

	public record TimePeriodRequest(@NotNull LocalTime startTime, @NotNull LocalTime endTime) {
		TimePeriod toCommand() {
			return new TimePeriod(startTime, endTime);
		}
	}
	public record BusinessPeriodRequest(@NotNull LocalTime openTime, @NotNull LocalTime closeTime) {
		TimePeriod toCommand() {
			return new TimePeriod(openTime, closeTime);
		}
	}

	public record StoreExceptionRequest(@NotNull LocalDate targetDate, @NotNull ScheduleExceptionType type,
		LocalTime startTime, LocalTime endTime, @Size(max = 500) String reason) {
		StoreExceptionCommand toCommand() {
			return new StoreExceptionCommand(targetDate, type, startTime, endTime, reason);
		}
	}

	public record StaffExceptionRequest(@NotNull LocalDate targetDate, @NotNull StaffScheduleExceptionType type,
		LocalTime startTime, LocalTime endTime, @Size(max = 500) String reason) {
		StaffExceptionCommand toCommand() {
			return new StaffExceptionCommand(targetDate, type, startTime, endTime, reason);
		}
	}
}

