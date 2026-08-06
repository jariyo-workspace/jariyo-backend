package com.example.jariyo_backend.domain.store.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.domain.store.entity.BusinessHour;
import com.example.jariyo_backend.domain.store.entity.ScheduleException;
import com.example.jariyo_backend.domain.store.entity.ServiceOffering;
import com.example.jariyo_backend.domain.store.entity.ServiceStatus;
import com.example.jariyo_backend.domain.store.entity.StaffSchedule;
import com.example.jariyo_backend.domain.store.entity.StaffScheduleException;
import com.example.jariyo_backend.domain.store.entity.StaffService;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.entity.StorePolicy;
import com.example.jariyo_backend.domain.store.repository.BusinessHourRepository;
import com.example.jariyo_backend.domain.store.repository.ScheduleExceptionRepository;
import com.example.jariyo_backend.domain.store.repository.ServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StaffScheduleExceptionRepository;
import com.example.jariyo_backend.domain.store.repository.StaffScheduleRepository;
import com.example.jariyo_backend.domain.store.repository.StaffServiceRepository;
import com.example.jariyo_backend.domain.store.repository.StorePolicyRepository;
import com.example.jariyo_backend.domain.store.repository.StoreRepository;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StoreQueryService {
	private final StoreRepository storeRepository;
	private final StorePolicyRepository storePolicyRepository;
	private final ServiceRepository serviceRepository;
	private final StaffServiceRepository staffServiceRepository;
	private final StoreMemberRepository storeMemberRepository;
	private final BusinessHourRepository businessHourRepository;
	private final ScheduleExceptionRepository scheduleExceptionRepository;
	private final StaffScheduleRepository staffScheduleRepository;
	private final StaffScheduleExceptionRepository staffScheduleExceptionRepository;

	public StoreQueryService(StoreRepository storeRepository,
		StorePolicyRepository storePolicyRepository,
		ServiceRepository serviceRepository,
		StaffServiceRepository staffServiceRepository,
		StoreMemberRepository storeMemberRepository,
		BusinessHourRepository businessHourRepository,
		ScheduleExceptionRepository scheduleExceptionRepository,
		StaffScheduleRepository staffScheduleRepository,
		StaffScheduleExceptionRepository staffScheduleExceptionRepository) {
		this.storeRepository = storeRepository;
		this.storePolicyRepository = storePolicyRepository;
		this.serviceRepository = serviceRepository;
		this.staffServiceRepository = staffServiceRepository;
		this.storeMemberRepository = storeMemberRepository;
		this.businessHourRepository = businessHourRepository;
		this.scheduleExceptionRepository = scheduleExceptionRepository;
		this.staffScheduleRepository = staffScheduleRepository;
		this.staffScheduleExceptionRepository = staffScheduleExceptionRepository;
	}

	public List<StoreSummary> listStores() {
		return storeRepository.findAllByOrderByNameAsc().stream().map(StoreSummary::from).toList();
	}

	public StoreDetail getStore(UUID storeId) {
		Store store = getStoreOrThrow(storeId);
		StorePolicy policy = storePolicyRepository.findByStore_Id(storeId).orElse(null);
		List<BusinessHour> businessHours = businessHourRepository.findAllByStoreIdOrderByDayOfWeekAsc(storeId);
		return StoreDetail.from(store, policy, businessHours);
	}

	public List<ServiceSummary> listServices(UUID storeId, boolean activeOnly) {
		getStoreOrThrow(storeId);
		List<ServiceOffering> services = activeOnly
			? serviceRepository.findAllByStoreIdAndStatusOrderByCreatedAtAsc(storeId, ServiceStatus.ACTIVE)
			: serviceRepository.findAllByStoreIdOrderByCreatedAtAsc(storeId);
		Map<UUID, Long> availableStaffCounts = countAvailableStaffByService(services);
		return services.stream()
			.map(service -> ServiceSummary.from(service, availableStaffCounts.getOrDefault(service.getId(), 0L)))
			.toList();
	}

	public List<ServiceStaffSummary> listServiceStaff(UUID storeId, UUID serviceId) {
		getServiceOrThrow(storeId, serviceId);
		List<StaffService> links = staffServiceRepository.findAllByServiceIdAndActiveTrueOrderByStoreMemberIdAsc(serviceId);
		Map<UUID, StoreMember> membersById = getStoreMembersById(storeId,
			links.stream().map(StaffService::getStoreMemberId).toList());
		return links.stream()
			.map(link -> ServiceStaffSummary.from(getStoreMemberOrThrow(membersById, link.getStoreMemberId()), link))
			.toList();
	}

	public List<StoreMemberSummary> listAdminStaff(UUID storeId) {
		getStoreOrThrow(storeId);
		return storeMemberRepository.findAllByStoreIdOrderByCreatedAtAsc(storeId).stream()
			.map(StoreMemberSummary::from)
			.toList();
	}

	public StoreMemberDetail getAdminStaff(UUID storeId, UUID staffId) {
		return StoreMemberDetail.from(getStoreMemberOrThrow(storeId, staffId));
	}

	public List<StaffScheduleSummary> listStaffSchedules(UUID storeId, UUID staffId) {
		getStoreMemberOrThrow(storeId, staffId);
		return staffScheduleRepository.findAllByStoreMemberIdOrderByDayOfWeekAscStartTimeAsc(staffId).stream()
			.map(StaffScheduleSummary::from)
			.toList();
	}

	public List<StaffScheduleExceptionSummary> listStaffScheduleExceptions(UUID storeId, UUID staffId) {
		getStoreMemberOrThrow(storeId, staffId);
		return staffScheduleExceptionRepository.findAllByStoreMemberIdOrderByTargetDateAscCreatedAtAsc(staffId).stream()
			.map(StaffScheduleExceptionSummary::from)
			.toList();
	}

	public List<BusinessHourSummary> listBusinessHours(UUID storeId) {
		getStoreOrThrow(storeId);
		return summarizeBusinessHours(businessHourRepository.findAllByStoreIdOrderByDayOfWeekAsc(storeId));
	}

	public List<ScheduleExceptionSummary> listScheduleExceptions(UUID storeId) {
		getStoreOrThrow(storeId);
		return scheduleExceptionRepository.findAllByStoreIdOrderByTargetDateAscCreatedAtAsc(storeId).stream()
			.map(ScheduleExceptionSummary::from)
			.toList();
	}

	public StorePolicySummary getPolicy(UUID storeId) {
		getStoreOrThrow(storeId);
		StorePolicy policy = storePolicyRepository.findByStore_Id(storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_POLICY_NOT_FOUND));
		return StorePolicySummary.from(policy);
	}

	private Store getStoreOrThrow(UUID storeId) {
		return storeRepository.findById(storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
	}

	private ServiceOffering getServiceOrThrow(UUID storeId, UUID serviceId) {
		return serviceRepository.findByIdAndStoreId(serviceId, storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_NOT_FOUND));
	}

	private StoreMember getStoreMemberOrThrow(UUID storeId, UUID staffId) {
		return storeMemberRepository.findByIdAndStoreId(staffId, storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));
	}

	private Map<UUID, StoreMember> getStoreMembersById(UUID storeId, Collection<UUID> staffIds) {
		if (staffIds.isEmpty()) {
			return Map.of();
		}
		return storeMemberRepository.findAllByStoreIdAndIdInOrderByCreatedAtAsc(storeId, staffIds).stream()
			.collect(Collectors.toMap(StoreMember::getId, Function.identity()));
	}

	private StoreMember getStoreMemberOrThrow(Map<UUID, StoreMember> membersById, UUID staffId) {
		StoreMember member = membersById.get(staffId);
		if (member == null) {
			throw new BusinessException(ErrorCode.STAFF_NOT_FOUND);
		}
		return member;
	}

	private Map<UUID, Long> countAvailableStaffByService(List<ServiceOffering> services) {
		if (services.isEmpty()) {
			return Map.of();
		}
		return staffServiceRepository.findAllByServiceIdInAndActiveTrue(
			services.stream().map(ServiceOffering::getId).toList()).stream()
			.collect(Collectors.groupingBy(StaffService::getServiceId, Collectors.counting()));
	}

	private static List<BusinessHourSummary> summarizeBusinessHours(List<BusinessHour> hours) {
		return hours.stream().collect(Collectors.groupingBy(BusinessHour::getDayOfWeek)).entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> new BusinessHourSummary(entry.getKey().name(),
				entry.getValue().stream().allMatch(BusinessHour::isClosed),
				entry.getValue().stream().filter(hour -> !hour.isClosed())
					.map(hour -> new BusinessHourSummary.Period(hour.getOpenTime().toString(),
						hour.getCloseTime().toString())).toList()))
			.toList();
	}

	public record StoreSummary(UUID id, String name, String description, String phoneNumber, String address, String timezone,
		String status) {
		static StoreSummary from(Store store) {
			return new StoreSummary(store.getId(), store.getName(), store.getDescription(), store.getPhoneNumber(),
				store.getAddress(), store.getTimezone(), store.getStatus().name());
		}
	}

	public record StoreDetail(UUID id, String name, String description, String phoneNumber, String address, String timezone,
		String status, List<BusinessHourSummary> businessHours, StorePolicySummary policySummary) {
		static StoreDetail from(Store store, StorePolicy policy, List<BusinessHour> businessHours) {
			return new StoreDetail(store.getId(), store.getName(), store.getDescription(), store.getPhoneNumber(),
				store.getAddress(), store.getTimezone(), store.getStatus().name(),
				summarizeBusinessHours(businessHours),
				policy == null ? null : StorePolicySummary.from(policy));
		}
	}

	public record ServiceSummary(UUID id, String name, String description, int durationMinutes, int cleanupMinutes,
		int capacity, String status, long availableStaffCount) {
		static ServiceSummary from(ServiceOffering service, long availableStaffCount) {
			return new ServiceSummary(service.getId(), service.getName(), service.getDescription(),
				service.getDurationMinutes(), service.getCleanupMinutes(), service.getCapacity(),
				service.getStatus().name(), availableStaffCount);
		}
	}

	public record ServiceStaffSummary(UUID id, String displayName, boolean bookingEnabled, Integer customDurationMinutes) {
		static ServiceStaffSummary from(StoreMember member, StaffService service) {
			return new ServiceStaffSummary(member.getId(), member.getDisplayName(), member.isBookingEnabled(),
				service.getCustomDurationMinutes());
		}
	}

	public record StoreMemberSummary(UUID id, String displayName, String role, String status, boolean bookingEnabled) {
		static StoreMemberSummary from(StoreMember member) {
			return new StoreMemberSummary(member.getId(), member.getDisplayName(), member.getRole().name(),
				member.getStatus().name(), member.isBookingEnabled());
		}
	}

	public record StoreMemberDetail(UUID id, String displayName, String role, String status, boolean bookingEnabled,
		UUID storeId) {
		static StoreMemberDetail from(StoreMember member) {
			return new StoreMemberDetail(member.getId(), member.getDisplayName(), member.getRole().name(),
				member.getStatus().name(), member.isBookingEnabled(), member.getStoreId());
		}
	}

	public record StaffScheduleSummary(UUID id, String dayOfWeek, String startTime, String endTime,
		String validFrom, String validUntil) {
		static StaffScheduleSummary from(StaffSchedule schedule) {
			return new StaffScheduleSummary(schedule.getId(), schedule.getDayOfWeek().name(),
				schedule.getStartTime().toString(), schedule.getEndTime().toString(),
				schedule.getValidFrom() == null ? null : schedule.getValidFrom().toString(),
				schedule.getValidUntil() == null ? null : schedule.getValidUntil().toString());
		}
	}

	public record StaffScheduleExceptionSummary(UUID id, String targetDate, String type, String startTime,
		String endTime, String reason) {
		static StaffScheduleExceptionSummary from(StaffScheduleException exception) {
			return new StaffScheduleExceptionSummary(exception.getId(), exception.getTargetDate().toString(),
				exception.getType().name(), exception.getStartTime() == null ? null : exception.getStartTime().toString(),
				exception.getEndTime() == null ? null : exception.getEndTime().toString(), exception.getReason());
		}
	}

	public record BusinessHourSummary(String dayOfWeek, boolean isClosed, List<Period> periods) {
		public record Period(String openTime, String closeTime) {
		}
	}

	public record ScheduleExceptionSummary(UUID id, String targetDate, String type, String startTime, String endTime,
		String reason) {
		static ScheduleExceptionSummary from(ScheduleException exception) {
			return new ScheduleExceptionSummary(exception.getId(), exception.getTargetDate().toString(),
				exception.getType().name(), exception.getStartTime() == null ? null : exception.getStartTime().toString(),
				exception.getEndTime() == null ? null : exception.getEndTime().toString(), exception.getReason());
		}
	}

	public record StorePolicySummary(int bookingOpenDays, int minimumBookingNoticeMinutes,
		int cancellationDeadlineMinutes, int checkInOpenBeforeMinutes, int lateToleranceMinutes, int noShowAfterMinutes,
		int reservationHoldMinutes, int slotOfferExpirationMinutes, int walkInCallTimeoutMinutes, boolean waitlistEnabled,
		boolean walkInEnabled, boolean autoNoShowEnabled) {
		static StorePolicySummary from(StorePolicy policy) {
			return new StorePolicySummary(policy.getBookingOpenDays(), policy.getMinimumBookingNoticeMinutes(),
				policy.getCancellationDeadlineMinutes(), policy.getCheckInOpenBeforeMinutes(),
				policy.getLateToleranceMinutes(), policy.getNoShowAfterMinutes(), policy.getReservationHoldMinutes(),
				policy.getSlotOfferExpirationMinutes(), policy.getWalkInCallTimeoutMinutes(),
				policy.isWaitlistEnabled(), policy.isWalkInEnabled(), policy.isAutoNoShowEnabled());
		}
	}
}
