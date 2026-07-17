package com.example.jariyo_backend.domain.store.service;

import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.domain.store.dto.BusinessHourSummary;
import com.example.jariyo_backend.domain.store.dto.ScheduleExceptionSummary;
import com.example.jariyo_backend.domain.store.dto.ServiceStaffSummary;
import com.example.jariyo_backend.domain.store.dto.ServiceSummary;
import com.example.jariyo_backend.domain.store.dto.StaffScheduleExceptionSummary;
import com.example.jariyo_backend.domain.store.dto.StaffScheduleSummary;
import com.example.jariyo_backend.domain.store.dto.StoreDetail;
import com.example.jariyo_backend.domain.store.dto.StoreMemberDetail;
import com.example.jariyo_backend.domain.store.dto.StoreMemberSummary;
import com.example.jariyo_backend.domain.store.dto.StorePolicySummary;
import com.example.jariyo_backend.domain.store.dto.StoreSummary;
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
		Store store = storeRepository.findById(storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		StorePolicy policy = storePolicyRepository.findByStore_Id(storeId).orElse(null);
		List<BusinessHour> businessHours = businessHourRepository.findAllByStoreIdOrderByDayOfWeekAsc(storeId);
		return StoreDetail.from(store, policy, businessHours);
	}

	public List<ServiceSummary> listServices(UUID storeId, boolean activeOnly) {
		List<ServiceOffering> services = activeOnly
			? serviceRepository.findAllByStoreIdAndStatusOrderByCreatedAtAsc(storeId, ServiceStatus.ACTIVE)
			: serviceRepository.findAllByStoreIdOrderByCreatedAtAsc(storeId);
		return services.stream()
			.map(service -> ServiceSummary.from(service, countAvailableStaff(service.getId())))
			.toList();
	}

	public List<ServiceStaffSummary> listServiceStaff(UUID storeId, UUID serviceId) {
		List<StaffService> links = staffServiceRepository.findAllByServiceIdAndActiveTrueOrderByStoreMemberIdAsc(serviceId);
		return links.stream()
			.map(link -> {
				StoreMember member = storeMemberRepository.findById(link.getStoreMemberId())
					.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
				if (!storeId.equals(member.getStoreId())) {
					throw new BusinessException(ErrorCode.NOT_FOUND);
				}
				return ServiceStaffSummary.from(member, link);
			})
			.toList();
	}

	public List<StoreMemberSummary> listAdminStaff(UUID storeId) {
		return storeMemberRepository.findAll().stream()
			.filter(member -> storeId.equals(member.getStoreId()))
			.map(StoreMemberSummary::from)
			.toList();
	}

	public StoreMemberDetail getAdminStaff(UUID storeId, UUID staffId) {
		StoreMember member = storeMemberRepository.findById(staffId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		if (!storeId.equals(member.getStoreId())) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		return StoreMemberDetail.from(member);
	}

	public List<StaffScheduleSummary> listStaffSchedules(UUID storeId, UUID staffId) {
		ensureStaffInStore(storeId, staffId);
		return staffScheduleRepository.findAllByStoreMemberIdOrderByDayOfWeekAscStartTimeAsc(staffId).stream()
			.map(StaffScheduleSummary::from)
			.toList();
	}

	public List<StaffScheduleExceptionSummary> listStaffScheduleExceptions(UUID storeId, UUID staffId) {
		ensureStaffInStore(storeId, staffId);
		return staffScheduleExceptionRepository.findAllByStoreMemberIdOrderByTargetDateAscCreatedAtAsc(staffId).stream()
			.map(StaffScheduleExceptionSummary::from)
			.toList();
	}

	public List<BusinessHourSummary> listBusinessHours(UUID storeId) {
		return businessHourRepository.findAllByStoreIdOrderByDayOfWeekAsc(storeId).stream()
			.map(BusinessHourSummary::from)
			.toList();
	}

	public List<ScheduleExceptionSummary> listScheduleExceptions(UUID storeId) {
		return scheduleExceptionRepository.findAllByStoreIdOrderByTargetDateAscCreatedAtAsc(storeId).stream()
			.map(ScheduleExceptionSummary::from)
			.toList();
	}

	public StorePolicySummary getPolicy(UUID storeId) {
		StorePolicy policy = storePolicyRepository.findByStore_Id(storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		return StorePolicySummary.from(policy);
	}

	private void ensureStaffInStore(UUID storeId, UUID staffId) {
		StoreMember member = storeMemberRepository.findById(staffId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		if (!storeId.equals(member.getStoreId())) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
	}

	private long countAvailableStaff(UUID serviceId) {
		return staffServiceRepository.findAllByServiceIdAndActiveTrueOrderByStoreMemberIdAsc(serviceId).stream().count();
	}
}
