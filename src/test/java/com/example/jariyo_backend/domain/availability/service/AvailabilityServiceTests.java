package com.example.jariyo_backend.domain.availability.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.availability.dto.AvailabilityResponse;
import com.example.jariyo_backend.domain.reservation.entity.Reservation;
import com.example.jariyo_backend.domain.reservation.entity.ReservationSource;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.repository.ReservationRepository;
import com.example.jariyo_backend.domain.store.entity.BusinessHour;
import com.example.jariyo_backend.domain.store.entity.ScheduleException;
import com.example.jariyo_backend.domain.store.entity.ScheduleExceptionType;
import com.example.jariyo_backend.domain.store.entity.ServiceStatus;
import com.example.jariyo_backend.domain.store.entity.StaffSchedule;
import com.example.jariyo_backend.domain.store.entity.StaffService;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.entity.StorePolicy;
import com.example.jariyo_backend.domain.store.entity.StoreServiceDefinition;
import com.example.jariyo_backend.domain.store.entity.StoreStatus;
import com.example.jariyo_backend.domain.store.repository.BusinessHourRepository;
import com.example.jariyo_backend.domain.store.repository.ScheduleExceptionRepository;
import com.example.jariyo_backend.domain.store.repository.StaffScheduleExceptionRepository;
import com.example.jariyo_backend.domain.store.repository.StaffScheduleRepository;
import com.example.jariyo_backend.domain.store.repository.StaffServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StorePolicyRepository;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.store.repository.StoreServiceDefinitionRepository;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.entity.StoreMemberRole;
import com.example.jariyo_backend.domain.user.entity.UserAccount;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTests {
	@Mock StoreRepository storeRepository;
	@Mock StorePolicyRepository storePolicyRepository;
	@Mock StoreServiceDefinitionRepository storeServiceDefinitionRepository;
	@Mock BusinessHourRepository businessHourRepository;
	@Mock ScheduleExceptionRepository scheduleExceptionRepository;
	@Mock StoreMemberRepository storeMemberRepository;
	@Mock StaffServiceRepository staffServiceRepository;
	@Mock StaffScheduleRepository staffScheduleRepository;
	@Mock StaffScheduleExceptionRepository staffScheduleExceptionRepository;
	@Mock ReservationRepository reservationRepository;

	@Test
	void calculatesAvailableSlotsExcludingExistingReservations() {
		UUID storeId = UUID.randomUUID();
		UUID serviceId = UUID.randomUUID();
		UUID staffId = UUID.randomUUID();
		LocalDate date = LocalDate.of(2026, 7, 23);
		AvailabilityService service = new AvailabilityService(
			storeRepository, storePolicyRepository, storeServiceDefinitionRepository, businessHourRepository,
			scheduleExceptionRepository, storeMemberRepository, staffServiceRepository, staffScheduleRepository,
			staffScheduleExceptionRepository, reservationRepository,
			Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC));
		StoreMember staff = new StoreMember(staffId, storeId, new UserAccount("staff@example.com", "01012345678", "hash"),
			StoreMemberRole.STAFF, "디자이너", true);

		mockStoreGraph(storeId, serviceId, staffId, staff, date);
		when(reservationRepository.findActiveReservationsForAvailability(eq(storeId), any(), any(), any(), any()))
			.thenReturn(List.of(new Reservation(
				UUID.randomUUID(),
				storeId,
				UUID.randomUUID(),
				serviceId,
				staffId,
				ReservationSource.CUSTOMER_BOOKING,
				ReservationStatus.CONFIRMED,
				Instant.parse("2026-07-23T05:00:00Z"),
				Instant.parse("2026-07-23T05:30:00Z"),
				Instant.parse("2026-07-23T05:40:00Z"),
				1)));

		AvailabilityResponse response = service.getAvailability(storeId, serviceId, staffId, date, date, 1);
		List<String> starts = response.dates().get(0).slots().stream()
			.map(slot -> slot.startAt().toLocalTime().toString())
			.toList();

		assertTrue(starts.contains("13:00"));
		assertTrue(starts.contains("15:00"));
		assertTrue(starts.contains("15:30"));
		assertFalse(starts.contains("14:00"));
		assertFalse(starts.contains("13:30"));
		assertFalse(starts.contains("14:30"));
	}

	@Test
	void returnsPerStaffSlotsInDeterministicOrderWhenStaffIsNotSpecified() {
		UUID storeId = UUID.randomUUID();
		UUID serviceId = UUID.randomUUID();
		UUID staffAId = UUID.randomUUID();
		UUID staffBId = UUID.randomUUID();
		LocalDate date = LocalDate.of(2026, 7, 23);
		AvailabilityService service = new AvailabilityService(
			storeRepository, storePolicyRepository, storeServiceDefinitionRepository, businessHourRepository,
			scheduleExceptionRepository, storeMemberRepository, staffServiceRepository, staffScheduleRepository,
			staffScheduleExceptionRepository, reservationRepository,
			Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC));
		StoreMember staffA = new StoreMember(staffAId, storeId, new UserAccount("a@example.com", "01011112222", "hash"),
			StoreMemberRole.STAFF, "가디자이너", true);
		StoreMember staffB = new StoreMember(staffBId, storeId, new UserAccount("b@example.com", "01033334444", "hash"),
			StoreMemberRole.STAFF, "나다자이너", true);

		when(storeRepository.findByIdAndStatus(storeId, StoreStatus.ACTIVE)).thenReturn(Optional.of(
			new Store(storeId, "자리요", null, "0212345678", "서울", "Asia/Seoul", StoreStatus.ACTIVE)));
		when(storePolicyRepository.findByStoreId(storeId)).thenReturn(Optional.of(defaultPolicy(storeId)));
		when(storeServiceDefinitionRepository.findByIdAndStoreIdAndStatus(serviceId, storeId, ServiceStatus.ACTIVE))
			.thenReturn(Optional.of(new StoreServiceDefinition(serviceId, storeId, "커트", null, 30, 10, 1, ServiceStatus.ACTIVE)));
		when(storeMemberRepository.findAllByStoreIdAndStatusAndBookingEnabledTrue(storeId,
			com.example.jariyo_backend.domain.user.entity.StoreMemberStatus.ACTIVE)).thenReturn(List.of(staffB, staffA));
		when(staffServiceRepository.findAllByServiceIdAndActiveTrueAndStoreMemberIdIn(eq(serviceId), any()))
			.thenReturn(List.of(
				new StaffService(UUID.randomUUID(), staffAId, serviceId, null, true),
				new StaffService(UUID.randomUUID(), staffBId, serviceId, null, true)));
		when(staffScheduleRepository.findAllByStoreMemberIdIn(any())).thenReturn(List.of(
			new StaffSchedule(UUID.randomUUID(), staffAId, DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(11, 0),
				LocalDate.of(2026, 1, 1), null),
			new StaffSchedule(UUID.randomUUID(), staffBId, DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(11, 0),
				LocalDate.of(2026, 1, 1), null)));
		when(staffScheduleExceptionRepository.findAllByStoreMemberIdInAndTargetDateBetween(any(), eq(date), eq(date)))
			.thenReturn(List.of());
		when(scheduleExceptionRepository.findAllByStoreIdAndTargetDateBetween(storeId, date, date)).thenReturn(List.of());
		when(businessHourRepository.findAllByStoreId(storeId)).thenReturn(List.of(
			new BusinessHour(UUID.randomUUID(), storeId, DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), false)));
		when(reservationRepository.findActiveReservationsForAvailability(eq(storeId), any(), any(), any(), any()))
			.thenReturn(List.of());

		AvailabilityResponse response = service.getAvailability(storeId, serviceId, null, date, date, 1);

		assertEquals(staffAId, response.dates().get(0).slots().get(0).staffId());
		assertEquals(staffBId, response.dates().get(0).slots().get(1).staffId());
	}

	@Test
	void returnsEmptySlotsWhenStoreIsClosedByException() {
		UUID storeId = UUID.randomUUID();
		UUID serviceId = UUID.randomUUID();
		UUID staffId = UUID.randomUUID();
		LocalDate date = LocalDate.of(2026, 7, 23);
		AvailabilityService service = new AvailabilityService(
			storeRepository, storePolicyRepository, storeServiceDefinitionRepository, businessHourRepository,
			scheduleExceptionRepository, storeMemberRepository, staffServiceRepository, staffScheduleRepository,
			staffScheduleExceptionRepository, reservationRepository,
			Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC));
		StoreMember staff = new StoreMember(staffId, storeId, new UserAccount("staff@example.com", "01012345678", "hash"),
			StoreMemberRole.STAFF, "디자이너", true);

		mockStoreGraph(storeId, serviceId, staffId, staff, date);
		when(scheduleExceptionRepository.findAllByStoreIdAndTargetDateBetween(storeId, date, date)).thenReturn(List.of(
			new ScheduleException(UUID.randomUUID(), storeId, date, ScheduleExceptionType.CLOSED_ALL_DAY,
				null, null, "휴무", null)));
		when(reservationRepository.findActiveReservationsForAvailability(eq(storeId), any(), any(), any(), any()))
			.thenReturn(List.of());

		AvailabilityResponse response = service.getAvailability(storeId, serviceId, staffId, date, date, 1);

		assertTrue(response.dates().get(0).slots().isEmpty());
	}

	private void mockStoreGraph(UUID storeId, UUID serviceId, UUID staffId, StoreMember staff, LocalDate date) {
		when(storeRepository.findByIdAndStatus(storeId, StoreStatus.ACTIVE)).thenReturn(Optional.of(
			new Store(storeId, "자리요", null, "0212345678", "서울", "Asia/Seoul", StoreStatus.ACTIVE)));
		when(storePolicyRepository.findByStoreId(storeId)).thenReturn(Optional.of(defaultPolicy(storeId)));
		when(storeServiceDefinitionRepository.findByIdAndStoreIdAndStatus(serviceId, storeId, ServiceStatus.ACTIVE))
			.thenReturn(Optional.of(new StoreServiceDefinition(serviceId, storeId, "커트", null, 30, 10, 1, ServiceStatus.ACTIVE)));
		when(storeMemberRepository.findByIdAndStoreId(staffId, storeId)).thenReturn(Optional.of(staff));
		when(staffServiceRepository.findAllByServiceIdAndActiveTrueAndStoreMemberIdIn(eq(serviceId), any()))
			.thenReturn(List.of(new StaffService(UUID.randomUUID(), staffId, serviceId, null, true)));
		when(staffScheduleRepository.findAllByStoreMemberIdIn(any())).thenReturn(List.of(
			new StaffSchedule(UUID.randomUUID(), staffId, DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(12, 0),
				LocalDate.of(2026, 1, 1), null),
			new StaffSchedule(UUID.randomUUID(), staffId, DayOfWeek.THURSDAY, LocalTime.of(13, 0), LocalTime.of(18, 0),
				LocalDate.of(2026, 1, 1), null)));
		when(staffScheduleExceptionRepository.findAllByStoreMemberIdInAndTargetDateBetween(any(), eq(date), eq(date)))
			.thenReturn(List.of());
		when(scheduleExceptionRepository.findAllByStoreIdAndTargetDateBetween(storeId, date, date)).thenReturn(List.of());
		when(businessHourRepository.findAllByStoreId(storeId)).thenReturn(List.of(
			new BusinessHour(UUID.randomUUID(), storeId, DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), false),
			new BusinessHour(UUID.randomUUID(), storeId, DayOfWeek.THURSDAY, LocalTime.of(13, 0), LocalTime.of(18, 0), false)));
	}

	private StorePolicy defaultPolicy(UUID storeId) {
		return new StorePolicy(UUID.randomUUID(), storeId, 14, 60, 60, 10, 10, 10, 5, 3, 3, true, true, true);
	}
}
