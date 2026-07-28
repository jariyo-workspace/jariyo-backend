package com.example.jariyo_backend.domain.reservation.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.domain.availability.dto.AvailabilityDateResponse;
import com.example.jariyo_backend.domain.availability.dto.AvailabilityResponse;
import com.example.jariyo_backend.domain.availability.dto.AvailabilitySlotResponse;
import com.example.jariyo_backend.domain.availability.dto.AvailabilitySlotStatus;
import com.example.jariyo_backend.domain.availability.service.AvailabilityService;
import com.example.jariyo_backend.domain.reservation.entity.Reservation;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.repository.ReservationRepository;
import com.example.jariyo_backend.domain.reservation.repository.ReservationStatusHistoryRepository;
import com.example.jariyo_backend.domain.store.entity.ServiceStatus;
import com.example.jariyo_backend.domain.store.entity.StaffService;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.entity.StorePolicy;
import com.example.jariyo_backend.domain.store.entity.StoreServiceDefinition;
import com.example.jariyo_backend.domain.store.entity.StoreStatus;
import com.example.jariyo_backend.domain.store.repository.StaffServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StorePolicyRepository;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.store.repository.StoreServiceDefinitionRepository;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.entity.StoreMemberRole;
import com.example.jariyo_backend.domain.user.entity.UserAccount;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationBookingServiceTests {
	private static final Instant NOW = Instant.parse("2026-07-26T00:00:00Z");
	private static final UUID STORE_ID = UUID.randomUUID();
	private static final UUID SERVICE_ID = UUID.randomUUID();
	private static final UUID STAFF_ID = UUID.randomUUID();
	private static final UUID CUSTOMER_ID = UUID.randomUUID();
	private static final OffsetDateTime START_AT = OffsetDateTime.parse("2026-07-27T14:00:00+09:00");

	@Mock ReservationRepository reservationRepository;
	@Mock ReservationStatusHistoryRepository historyRepository;
	@Mock StoreRepository storeRepository;
	@Mock StorePolicyRepository storePolicyRepository;
	@Mock StoreServiceDefinitionRepository serviceRepository;
	@Mock StoreMemberRepository storeMemberRepository;
	@Mock StaffServiceRepository staffServiceRepository;
	@Mock AvailabilityService availabilityService;
	@Mock EntityManager entityManager;
	@Mock Query query;

	@BeforeEach
	void setUp() {
		Store store = new Store(STORE_ID, "자리요", null, "0212345678", "서울", "Asia/Seoul", StoreStatus.ACTIVE);
		StorePolicy policy = new StorePolicy(UUID.randomUUID(), STORE_ID, 14, 60, 1440, 10, 10, 10,
			5, 3, 3, true, true, true);
		StoreServiceDefinition service = new StoreServiceDefinition(SERVICE_ID, STORE_ID, "커트", null,
			30, 10, 1, ServiceStatus.ACTIVE);
		StoreMember staff = new StoreMember(STAFF_ID, STORE_ID,
			new UserAccount("staff@example.com", "+821012345678", "hash"), StoreMemberRole.STAFF, "직원", true);
		StaffService staffService = new StaffService(UUID.randomUUID(), STAFF_ID, SERVICE_ID, null, true);
		AvailabilitySlotResponse slot = new AvailabilitySlotResponse(START_AT, START_AT.plusMinutes(30),
			START_AT.plusMinutes(40), STAFF_ID, AvailabilitySlotStatus.AVAILABLE);
		when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
		when(storePolicyRepository.findByStoreId(STORE_ID)).thenReturn(Optional.of(policy));
		when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
		when(storeMemberRepository.findByIdAndStoreId(STAFF_ID, STORE_ID)).thenReturn(Optional.of(staff));
		when(staffServiceRepository.findByStoreMemberIdAndServiceId(STAFF_ID, SERVICE_ID))
			.thenReturn(Optional.of(staffService));
		when(availabilityService.getAvailability(eq(STORE_ID), eq(SERVICE_ID), eq(STAFF_ID), any(), any(), eq(1)))
			.thenReturn(new AvailabilityResponse(STORE_ID, SERVICE_ID, STAFF_ID,
				List.of(new AvailabilityDateResponse(LocalDate.of(2026, 7, 27), List.of(slot)))));
		when(entityManager.createNativeQuery(anyString())).thenReturn(query);
		when(query.setParameter(anyString(), any())).thenReturn(query);
		when(query.getSingleResult()).thenReturn(1);
	}

	@Test
	void createsHeldReservationUntilStorePolicyExpiration() {
		when(reservationRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
		ReservationBookingService service = service();

		Reservation held = service.holdCustomer(CUSTOMER_ID, command());

		assertEquals(ReservationStatus.HELD, held.getStatus());
		assertEquals(NOW.plusSeconds(5 * 60L), held.getHoldExpiresAt());
		verify(reservationRepository).existsOverlappingReservation(eq(STORE_ID), eq(STAFF_ID), any(),
			eq(ReservationStatus.HELD), eq(NOW), eq(START_AT.toInstant()), eq(START_AT.plusMinutes(40).toInstant()));
		verify(historyRepository).save(any());
	}

	@Test
	void rejectsHoldWhenStaffSlotIsAlreadyActive() {
		when(reservationRepository.existsOverlappingReservation(eq(STORE_ID), eq(STAFF_ID), any(),
			eq(ReservationStatus.HELD), eq(NOW), any(), any())).thenReturn(true);
		ReservationBookingService service = service();

		BusinessException exception = assertThrows(BusinessException.class,
			() -> service.holdCustomer(CUSTOMER_ID, command()));

		assertEquals(ErrorCode.RESERVATION_SLOT_ALREADY_TAKEN, exception.getErrorCode());
	}

	private ReservationBookingService service() {
		return new ReservationBookingService(reservationRepository, historyRepository, storeRepository,
			storePolicyRepository, serviceRepository, storeMemberRepository, staffServiceRepository,
			availabilityService, entityManager, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private ReservationBookingService.CustomerBookingCommand command() {
		return new ReservationBookingService.CustomerBookingCommand(
			STORE_ID, SERVICE_ID, STAFF_ID, START_AT, 1, null);
	}
}
