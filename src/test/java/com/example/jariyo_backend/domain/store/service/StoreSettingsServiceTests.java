package com.example.jariyo_backend.domain.store.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.domain.admin.entity.AuditLog;
import com.example.jariyo_backend.domain.admin.repository.AuditLogRepository;
import com.example.jariyo_backend.domain.reservation.entity.Reservation;
import com.example.jariyo_backend.domain.reservation.repository.ReservationRepository;
import com.example.jariyo_backend.domain.store.entity.ServiceOffering;
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
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.entity.StoreMemberRole;
import com.example.jariyo_backend.domain.user.entity.StoreMemberStatus;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import com.example.jariyo_backend.domain.user.repository.UserAccountRepository;
import com.example.jariyo_backend.domain.user.service.StoreAuthorizationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StoreSettingsServiceTests {
	private final StoreRepository storeRepository = mock();
	private final StorePolicyRepository policyRepository = mock();
	private final ServiceRepository serviceRepository = mock();
	private final StoreMemberRepository memberRepository = mock();
	private final UserAccountRepository userRepository = mock();
	private final StaffServiceRepository staffServiceRepository = mock();
	private final BusinessHourRepository businessHourRepository = mock();
	private final ScheduleExceptionRepository scheduleExceptionRepository = mock();
	private final StaffScheduleRepository staffScheduleRepository = mock();
	private final StaffScheduleExceptionRepository staffExceptionRepository = mock();
	private final ReservationRepository reservationRepository = mock();
	private final StoreAuthorizationService authorizationService = mock();
	private final AuditLogRepository auditLogRepository = mock();
	private final EntityManager entityManager = mock();
	private StoreSettingsService service;

	@BeforeEach
	void setUp() {
		Query query = mock();
		when(entityManager.createNativeQuery(any(String.class))).thenReturn(query);
		when(query.setParameter(any(String.class), any())).thenReturn(query);
		when(query.getSingleResult()).thenReturn(1);
		service = new StoreSettingsService(storeRepository, policyRepository, serviceRepository, memberRepository,
			userRepository, staffServiceRepository, businessHourRepository, scheduleExceptionRepository,
			staffScheduleRepository, staffExceptionRepository, reservationRepository, authorizationService,
			auditLogRepository, entityManager, Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), ZoneOffset.UTC));
	}

	@Test
	void anotherOwnerCannotBeChanged() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UUID staffId = UUID.randomUUID();
		StoreMember actor = member(UUID.randomUUID(), storeId, StoreMemberRole.OWNER);
		StoreMember target = member(staffId, storeId, StoreMemberRole.OWNER);
		when(authorizationService.requireManager(userId, storeId)).thenReturn(actor);
		when(authorizationService.requireOwner(userId, storeId)).thenReturn(actor);
		when(memberRepository.findByIdAndStoreId(staffId, storeId)).thenReturn(Optional.of(target));

		BusinessException exception = assertThrows(BusinessException.class, () -> service.updateStaff(userId, storeId,
			staffId, new StoreSettingsService.StaffUpdateCommand("대상", StoreMemberRole.MANAGER, true,
				StoreMemberStatus.ACTIVE)));

		assertEquals(ErrorCode.OWNER_CHANGE_NOT_ALLOWED, exception.getErrorCode());
	}

	@Test
	void lastActiveOwnerCannotDemoteSelf() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UUID staffId = UUID.randomUUID();
		StoreMember owner = member(staffId, storeId, StoreMemberRole.OWNER);
		when(authorizationService.requireManager(userId, storeId)).thenReturn(owner);
		when(authorizationService.requireOwner(userId, storeId)).thenReturn(owner);
		when(memberRepository.findByIdAndStoreId(staffId, storeId)).thenReturn(Optional.of(owner));
		when(memberRepository.countByStoreIdAndRoleAndStatus(storeId, StoreMemberRole.OWNER,
			StoreMemberStatus.ACTIVE)).thenReturn(1L);

		BusinessException exception = assertThrows(BusinessException.class, () -> service.updateStaff(userId, storeId,
			staffId, new StoreSettingsService.StaffUpdateCommand("본인", StoreMemberRole.STAFF, true,
				StoreMemberStatus.ACTIVE)));

		assertEquals(ErrorCode.LAST_ACTIVE_OWNER_REQUIRED, exception.getErrorCode());
	}

	@Test
	void serviceDeactivationReturnsConflictWithoutMutationOrAudit() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UUID serviceId = UUID.randomUUID();
		UUID reservationId = UUID.randomUUID();
		StoreMember manager = member(UUID.randomUUID(), storeId, StoreMemberRole.MANAGER);
		ServiceOffering offering = mock();
		Reservation reservation = mock();
		Store store = new Store(storeId, "자리요", null, "0212345678", "서울", "Asia/Seoul", StoreStatus.ACTIVE);
		when(authorizationService.requireManager(userId, storeId)).thenReturn(manager);
		when(serviceRepository.findByIdAndStoreId(serviceId, storeId)).thenReturn(Optional.of(offering));
		when(memberRepository.findAllByStoreIdOrderByCreatedAtAsc(storeId)).thenReturn(List.of());
		when(reservationRepository.findFutureActiveReservations(any(), any(), any(), any()))
			.thenReturn(List.of(reservation));
		when(reservation.getServiceId()).thenReturn(serviceId);
		when(reservation.getId()).thenReturn(reservationId);
		when(reservation.getStartAt()).thenReturn(Instant.parse("2026-08-04T01:00:00Z"));
		when(storeRepository.findById(storeId)).thenReturn(Optional.of(store));

		StoreSettingsService.UpdateResult result = service.deactivateService(userId, storeId, serviceId);

		assertFalse(result.updated());
		assertEquals(reservationId, result.conflicts().get(0).reservationId());
		verify(offering, never()).deactivate();
		verify(auditLogRepository, never()).save(any(AuditLog.class));
	}

	@Test
	void rejectsOverlappingBusinessPeriodsBeforeWriting() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		StoreMember manager = member(UUID.randomUUID(), storeId, StoreMemberRole.MANAGER);
		when(authorizationService.requireManager(userId, storeId)).thenReturn(manager);

		assertThrows(BusinessException.class, () -> service.replaceBusinessHours(userId, storeId, List.of(
			new StoreSettingsService.BusinessDayCommand(DayOfWeek.MONDAY, false, List.of(
				new StoreSettingsService.TimePeriod(java.time.LocalTime.of(9, 0), java.time.LocalTime.of(12, 0)),
				new StoreSettingsService.TimePeriod(java.time.LocalTime.of(11, 0), java.time.LocalTime.of(18, 0)))))));

		verify(businessHourRepository, never()).saveAll(any());
	}

	private StoreMember member(UUID id, UUID storeId, StoreMemberRole role) {
		StoreMember member = mock();
		when(member.getId()).thenReturn(id);
		when(member.getStoreId()).thenReturn(storeId);
		when(member.getRole()).thenReturn(role);
		when(member.getStatus()).thenReturn(StoreMemberStatus.ACTIVE);
		return member;
	}
}
