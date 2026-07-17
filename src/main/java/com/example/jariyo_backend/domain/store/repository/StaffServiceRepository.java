package com.example.jariyo_backend.domain.store.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.store.entity.StaffService;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffServiceRepository extends JpaRepository<StaffService, UUID> {
	List<StaffService> findAllByServiceIdAndActiveTrueOrderByStoreMemberIdAsc(UUID serviceId);

	Optional<StaffService> findByStoreMemberIdAndServiceId(UUID storeMemberId, UUID serviceId);
}

