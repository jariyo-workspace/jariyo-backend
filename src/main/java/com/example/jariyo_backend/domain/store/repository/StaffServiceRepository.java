package com.example.jariyo_backend.domain.store.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.store.entity.StaffService;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffServiceRepository extends JpaRepository<StaffService, UUID> {
	List<StaffService> findAllByServiceIdAndActiveTrueAndStoreMemberIdIn(UUID serviceId, Iterable<UUID> storeMemberIds);

	List<StaffService> findAllByServiceIdAndActiveTrueOrderByStoreMemberIdAsc(UUID serviceId);

	List<StaffService> findAllByServiceIdInAndActiveTrue(Collection<UUID> serviceIds);

	Optional<StaffService> findByStoreMemberIdAndServiceId(UUID storeMemberId, UUID serviceId);

	boolean existsByStoreMemberIdAndServiceIdAndActiveTrue(UUID storeMemberId, UUID serviceId);

	void deleteAllByStoreMemberId(UUID storeMemberId);
}
