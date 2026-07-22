package com.example.jariyo_backend.domain.store.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.domain.store.entity.StaffSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffScheduleRepository extends JpaRepository<StaffSchedule, UUID> {
	List<StaffSchedule> findAllByStoreMemberIdIn(Collection<UUID> storeMemberIds);
}
