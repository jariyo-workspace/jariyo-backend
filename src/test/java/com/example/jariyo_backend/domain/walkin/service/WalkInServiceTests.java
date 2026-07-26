package com.example.jariyo_backend.domain.walkin.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.common.async.AsyncEventRecorder;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.common.idempotency.PersistentIdempotencyService;
import com.example.jariyo_backend.domain.store.entity.ServiceOffering;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.entity.StoreStatus;
import com.example.jariyo_backend.domain.store.repository.BusinessHourRepository;
import com.example.jariyo_backend.domain.store.repository.ScheduleExceptionRepository;
import com.example.jariyo_backend.domain.store.repository.ServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StaffServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StorePolicyRepository;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.user.entity.CustomerProfile;
import com.example.jariyo_backend.domain.user.entity.UserAccount;
import com.example.jariyo_backend.domain.user.repository.CustomerProfileRepository;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import com.example.jariyo_backend.domain.user.service.StoreAuthorizationService;
import com.example.jariyo_backend.domain.walkin.entity.WalkInEntry;
import com.example.jariyo_backend.domain.walkin.repository.CallHistoryRepository;
import com.example.jariyo_backend.domain.walkin.repository.CheckInRepository;
import com.example.jariyo_backend.domain.walkin.repository.QueueNumberIssuer;
import com.example.jariyo_backend.domain.walkin.repository.ServiceSessionRepository;
import com.example.jariyo_backend.domain.walkin.repository.WalkInEntryRepository;
import com.example.jariyo_backend.domain.walkin.repository.WalkInStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalkInServiceTests {
	@Mock StoreRepository storeRepository;
	@Mock StorePolicyRepository storePolicyRepository;
	@Mock ServiceRepository serviceRepository;
	@Mock StaffServiceRepository staffServiceRepository;
	@Mock StoreMemberRepository storeMemberRepository;
	@Mock CustomerProfileRepository customerProfileRepository;
	@Mock BusinessHourRepository businessHourRepository;
	@Mock ScheduleExceptionRepository scheduleExceptionRepository;
	@Mock WalkInEntryRepository walkInEntryRepository;
	@Mock CallHistoryRepository callHistoryRepository;
	@Mock CheckInRepository checkInRepository;
	@Mock ServiceSessionRepository serviceSessionRepository;
	@Mock WalkInStatusHistoryRepository historyRepository;
	@Mock QueueNumberIssuer queueNumberIssuer;
	@Mock StoreAuthorizationService storeAuthorizationService;
	@Mock PersistentIdempotencyService idempotencyService;
	@Mock AsyncEventRecorder asyncEventRecorder;

	@Test
	void getMineRejectsEntryOwnedByAnotherCustomer() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID customerId = UUID.randomUUID();
		UUID walkInId = UUID.randomUUID();
		CustomerProfile customer = customerProfile(customerId);
		WalkInEntry entry = WalkInEntry.forCustomer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
			1, LocalDate.of(2026, 7, 26), 1, 10);
		when(customerProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(customer));
		when(walkInEntryRepository.findById(walkInId)).thenReturn(Optional.of(entry));
		WalkInService service = walkInService();

		BusinessException exception = assertThrows(BusinessException.class, () -> service.getMine(userId, walkInId));

		assertEquals(ErrorCode.RESOURCE_NOT_OWNED_BY_USER, exception.getErrorCode());
	}

	@Test
	void getMineUsesStoreScopedServiceLookupWithoutRequiringActiveStatus() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID customerId = UUID.randomUUID();
		UUID walkInId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UUID serviceId = UUID.randomUUID();
		CustomerProfile customer = customerProfile(customerId);
		WalkInEntry entry = WalkInEntry.forCustomer(storeId, customerId, serviceId, null, 1,
			LocalDate.of(2026, 7, 26), 12, 30);
		ServiceOffering serviceOffering = serviceOffering(serviceId, storeId, "커트");
		setId(entry, walkInId);
		when(customerProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(customer));
		when(walkInEntryRepository.findById(walkInId)).thenReturn(Optional.of(entry));
		when(walkInEntryRepository.findAllByStoreIdAndOperationDateOrderByQueueNumberAsc(storeId, entry.getOperationDate()))
			.thenReturn(List.of(entry));
		when(storeRepository.findById(storeId)).thenReturn(Optional.of(
			new Store(storeId, "자리요", null, "0212345678", "서울", "Asia/Seoul", StoreStatus.ACTIVE)));
		when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceOffering));
		WalkInService service = walkInService();

		WalkInService.WalkInDetail detail = service.getMine(userId, walkInId);

		assertEquals(walkInId, detail.id());
		assertEquals("커트", detail.service().name());
	}

	private WalkInService walkInService() {
		return new WalkInService(storeRepository, storePolicyRepository, serviceRepository, staffServiceRepository,
			storeMemberRepository, customerProfileRepository, businessHourRepository, scheduleExceptionRepository,
			walkInEntryRepository, callHistoryRepository, checkInRepository, serviceSessionRepository, historyRepository,
			queueNumberIssuer, storeAuthorizationService, idempotencyService, asyncEventRecorder);
	}

	private CustomerProfile customerProfile(UUID customerId) throws Exception {
		CustomerProfile customer = new CustomerProfile(new UserAccount("user@example.com", "+821012345678", "hash"),
			"류승엽", false, true);
		var field = CustomerProfile.class.getDeclaredField("id");
		field.setAccessible(true);
		field.set(customer, customerId);
		return customer;
	}

	private void setId(WalkInEntry entry, UUID id) throws Exception {
		var field = WalkInEntry.class.getDeclaredField("id");
		field.setAccessible(true);
		field.set(entry, id);
	}

	private ServiceOffering serviceOffering(UUID serviceId, UUID storeId, String name) {
		ServiceOffering service = mock(ServiceOffering.class);
		when(service.getId()).thenReturn(serviceId);
		when(service.getStoreId()).thenReturn(storeId);
		when(service.getName()).thenReturn(name);
		return service;
	}
}
