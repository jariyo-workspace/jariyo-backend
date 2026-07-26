package com.example.jariyo_backend.domain.waitlist.service;

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
import com.example.jariyo_backend.domain.store.entity.StorePolicy;
import com.example.jariyo_backend.domain.store.entity.StoreStatus;
import com.example.jariyo_backend.domain.store.repository.ServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StaffServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StorePolicyRepository;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.user.entity.CustomerProfile;
import com.example.jariyo_backend.domain.user.entity.UserAccount;
import com.example.jariyo_backend.domain.user.repository.CustomerProfileRepository;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import com.example.jariyo_backend.domain.waitlist.entity.StaffPreferenceType;
import com.example.jariyo_backend.domain.waitlist.entity.WaitlistEntry;
import com.example.jariyo_backend.domain.waitlist.repository.SlotOfferRepository;
import com.example.jariyo_backend.domain.waitlist.repository.SlotOfferStatusHistoryRepository;
import com.example.jariyo_backend.domain.waitlist.repository.WaitlistEntryRepository;
import jakarta.persistence.EntityManager;
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
class WaitlistServiceTests {
	@Mock WaitlistEntryRepository waitlistEntryRepository;
	@Mock SlotOfferRepository slotOfferRepository;
	@Mock SlotOfferStatusHistoryRepository slotOfferStatusHistoryRepository;
	@Mock CustomerProfileRepository customerProfileRepository;
	@Mock StoreRepository storeRepository;
	@Mock StorePolicyRepository storePolicyRepository;
	@Mock ServiceRepository serviceRepository;
	@Mock StaffServiceRepository staffServiceRepository;
	@Mock StoreMemberRepository storeMemberRepository;
	@Mock com.example.jariyo_backend.domain.reservation.repository.ReservationRepository reservationRepository;
	@Mock PersistentIdempotencyService idempotencyService;
	@Mock AsyncEventRecorder asyncEventRecorder;
	@Mock EntityManager entityManager;

	@Test
	void cancelMineRejectsEntryOwnedByAnotherCustomer() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID customerId = UUID.randomUUID();
		UUID waitlistId = UUID.randomUUID();
		CustomerProfile customer = customerProfile(customerId);
		WaitlistEntry entry = new WaitlistEntry(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
			StaffPreferenceType.ANY_STAFF, LocalDate.of(2026, 7, 27), LocalTime.of(13, 0), LocalTime.of(17, 0),
			1, 1, Instant.parse("2026-07-27T08:00:00Z"));
		when(customerProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(customer));
		when(waitlistEntryRepository.findByIdForUpdate(waitlistId)).thenReturn(Optional.of(entry));
		mockIdempotentCancelExecution();
		WaitlistService service = waitlistService();

		BusinessException exception = assertThrows(BusinessException.class,
			() -> service.cancelMine(userId, waitlistId, "key", new WaitlistService.CancelWaitlistCommand("취소")));

		assertEquals(ErrorCode.WAITLIST_NOT_OWNED_BY_USER, exception.getErrorCode());
	}

	@Test
	void getMineUsesStoreScopedServiceLookupWithoutRequiringActiveStatus() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID customerId = UUID.randomUUID();
		UUID waitlistId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UUID serviceId = UUID.randomUUID();
		CustomerProfile customer = customerProfile(customerId);
		WaitlistEntry entry = new WaitlistEntry(storeId, customerId, serviceId, null, StaffPreferenceType.ANY_STAFF,
			LocalDate.of(2026, 7, 27), LocalTime.of(13, 0), LocalTime.of(17, 0), 1, 3,
			Instant.parse("2026-07-27T08:00:00Z"));
		ServiceOffering serviceOffering = serviceOffering(serviceId, storeId, "커트");
		setId(entry, waitlistId);
		when(customerProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(customer));
		when(waitlistEntryRepository.findById(waitlistId)).thenReturn(Optional.of(entry));
		when(slotOfferRepository.findFirstByWaitlistEntryIdAndStatusOrderByCreatedAtDesc(any(), any()))
			.thenReturn(Optional.empty());
		when(storeRepository.findById(storeId)).thenReturn(Optional.of(
			new Store(storeId, "자리요", null, "0212345678", "서울", "Asia/Seoul", StoreStatus.ACTIVE)));
		when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceOffering));
		WaitlistService service = waitlistService();

		WaitlistService.WaitlistDetail detail = service.getMine(userId, waitlistId);

		assertEquals(waitlistId, detail.id());
		assertEquals("커트", detail.service().name());
	}

	private WaitlistService waitlistService() {
		return new WaitlistService(waitlistEntryRepository, slotOfferRepository, slotOfferStatusHistoryRepository,
			customerProfileRepository, storeRepository, storePolicyRepository, serviceRepository, staffServiceRepository,
			storeMemberRepository, reservationRepository, idempotencyService, asyncEventRecorder, entityManager,
			Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC));
	}

	private void mockIdempotentCancelExecution() {
		doAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			java.util.function.Supplier<WaitlistService.WaitlistCancelResult> action = invocation.getArgument(5);
			return action.get();
		}).when(idempotencyService).execute(any(), any(), any(), any(), eq(WaitlistService.WaitlistCancelResult.class), any());
	}

	private CustomerProfile customerProfile(UUID customerId) throws Exception {
		CustomerProfile customer = new CustomerProfile(new UserAccount("user@example.com", "+821012345678", "hash"),
			"류승엽", false, true);
		var field = CustomerProfile.class.getDeclaredField("id");
		field.setAccessible(true);
		field.set(customer, customerId);
		return customer;
	}

	private void setId(WaitlistEntry entry, UUID id) throws Exception {
		var field = WaitlistEntry.class.getDeclaredField("id");
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
