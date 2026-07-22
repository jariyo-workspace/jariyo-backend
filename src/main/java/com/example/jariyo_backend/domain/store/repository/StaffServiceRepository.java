package com.example.jariyo_backend.domain.store.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.domain.store.entity.StaffService;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffServiceRepository extends JpaRepository<StaffService, UUID> {
	List<StaffService> findAllByServiceIdAndActiveTrueAndStoreMemberIdIn(UUID serviceId, Collection<UUID> storeMemberIds);
}
