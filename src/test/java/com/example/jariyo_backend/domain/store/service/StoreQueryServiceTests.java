package com.example.jariyo_backend.domain.store.service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.domain.store.entity.ServiceOffering;
import com.example.jariyo_backend.domain.store.entity.BusinessHour;
import com.example.jariyo_backend.domain.store.entity.ServiceStatus;
import com.example.jariyo_backend.domain.store.entity.StaffService;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.entity.StoreStatus;
import com.example.jariyo_backend.domain.store.repository.BusinessHourRepository;
import com.example.jariyo_backend.domain.store.repository.ScheduleExceptionRepository;
import com.example.jariyo_backend.domain.store.repository.ServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StaffScheduleExceptionRepository;
import com.example.jariyo_backend.domain.store.repository.StaffScheduleRepository;
import com.example.jariyo_backend.domain.store.repository.StaffServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StorePolicyRepository;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreQueryServiceTests {
	@Mock StoreRepository storeRepository;
	@Mock StorePolicyRepository storePolicyRepository;
	@Mock ServiceRepository serviceRepository;
	@Mock StaffServiceRepository staffServiceRepository;
	@Mock StoreMemberRepository storeMemberRepository;
	@Mock BusinessHourRepository businessHourRepository;
	@Mock ScheduleExceptionRepository scheduleExceptionRepository;
	@Mock StaffScheduleRepository staffScheduleRepository;
	@Mock StaffScheduleExceptionRepository staffScheduleExceptionRepository;

	@Test
	void aggregatesAvailableStaffCountWithoutPerServiceLookup() {
		UUID storeId = UUID.randomUUID();
		UUID serviceAId = UUID.randomUUID();
		UUID serviceBId = UUID.randomUUID();
		StoreQueryService service = new StoreQueryService(
			storeRepository, storePolicyRepository, serviceRepository, staffServiceRepository, storeMemberRepository,
			businessHourRepository, scheduleExceptionRepository, staffScheduleRepository, staffScheduleExceptionRepository);
		ServiceOffering serviceA = serviceOffering(serviceAId, storeId, "커트", 30, 10, 1);
		ServiceOffering serviceB = serviceOffering(serviceBId, storeId, "펌", 60, 15, 1);

		when(storeRepository.findById(storeId)).thenReturn(Optional.of(activeStore(storeId)));
		when(serviceRepository.findAllByStoreIdOrderByCreatedAtAsc(storeId)).thenReturn(List.of(serviceA, serviceB));
		when(staffServiceRepository.findAllByServiceIdInAndActiveTrue(List.of(serviceAId, serviceBId))).thenReturn(List.of(
			new StaffService(UUID.randomUUID(), UUID.randomUUID(), serviceAId, null, true),
			new StaffService(UUID.randomUUID(), UUID.randomUUID(), serviceAId, 40, true),
			new StaffService(UUID.randomUUID(), UUID.randomUUID(), serviceBId, null, true)));

		List<StoreQueryService.ServiceSummary> result = service.listServices(storeId, false);

		assertEquals(2L, result.get(0).availableStaffCount());
		assertEquals(1L, result.get(1).availableStaffCount());
	}

	@Test
	void rejectsStaffLookupWhenLinkedStaffDoesNotBelongToStore() {
		UUID storeId = UUID.randomUUID();
		UUID serviceId = UUID.randomUUID();
		UUID staffId = UUID.randomUUID();
		StoreQueryService service = new StoreQueryService(
			storeRepository, storePolicyRepository, serviceRepository, staffServiceRepository, storeMemberRepository,
			businessHourRepository, scheduleExceptionRepository, staffScheduleRepository, staffScheduleExceptionRepository);

		when(serviceRepository.findByIdAndStoreId(serviceId, storeId)).thenReturn(Optional.of(mock(ServiceOffering.class)));
		when(staffServiceRepository.findAllByServiceIdAndActiveTrueOrderByStoreMemberIdAsc(serviceId)).thenReturn(List.of(
			new StaffService(UUID.randomUUID(), staffId, serviceId, null, true)));
		when(storeMemberRepository.findAllByStoreIdAndIdInOrderByCreatedAtAsc(storeId, List.of(staffId)))
			.thenReturn(List.of());

		BusinessException exception = assertThrows(BusinessException.class,
			() -> service.listServiceStaff(storeId, serviceId));

		assertEquals(ErrorCode.STAFF_NOT_FOUND, exception.getErrorCode());
	}

	@Test
	void groupsMultipleBusinessPeriodsByDay() {
		UUID storeId = UUID.randomUUID();
		StoreQueryService service = new StoreQueryService(
			storeRepository, storePolicyRepository, serviceRepository, staffServiceRepository, storeMemberRepository,
			businessHourRepository, scheduleExceptionRepository, staffScheduleRepository, staffScheduleExceptionRepository);
		when(storeRepository.findById(storeId)).thenReturn(Optional.of(activeStore(storeId)));
		when(businessHourRepository.findAllByStoreIdOrderByDayOfWeekAsc(storeId)).thenReturn(List.of(
			new BusinessHour(null, storeId, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), false),
			new BusinessHour(null, storeId, DayOfWeek.MONDAY, LocalTime.of(13, 0), LocalTime.of(18, 0), false)));

		List<StoreQueryService.BusinessHourSummary> result = service.listBusinessHours(storeId);

		assertEquals(1, result.size());
		assertEquals(2, result.get(0).periods().size());
	}

	private Store activeStore(UUID storeId) {
		return new Store(storeId, "자리요", null, "0212345678", "서울", "Asia/Seoul", StoreStatus.ACTIVE);
	}

	private ServiceOffering serviceOffering(UUID serviceId, UUID storeId, String name, int durationMinutes,
		int cleanupMinutes, int capacity) {
		ServiceOffering serviceOffering = mock(ServiceOffering.class);
		when(serviceOffering.getId()).thenReturn(serviceId);
		when(serviceOffering.getName()).thenReturn(name);
		when(serviceOffering.getDurationMinutes()).thenReturn(durationMinutes);
		when(serviceOffering.getCleanupMinutes()).thenReturn(cleanupMinutes);
		when(serviceOffering.getCapacity()).thenReturn(capacity);
		when(serviceOffering.getStatus()).thenReturn(ServiceStatus.ACTIVE);
		return serviceOffering;
	}
}
