package com.example.jariyo_backend.domain.store.controller;

import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.domain.store.service.StoreQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StoreController {
	private final StoreQueryService storeQueryService;

	public StoreController(StoreQueryService storeQueryService) {
		this.storeQueryService = storeQueryService;
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
}

