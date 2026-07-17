package com.example.jariyo_backend.domain.store.repository;

import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.domain.store.entity.StaffScheduleException;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffScheduleExceptionRepository extends JpaRepository<StaffScheduleException, UUID> {
	List<StaffScheduleException> findAllByStoreMemberIdOrderByTargetDateAscCreatedAtAsc(UUID storeMemberId);
}

