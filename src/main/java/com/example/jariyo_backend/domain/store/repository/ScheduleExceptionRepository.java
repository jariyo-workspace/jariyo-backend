package com.example.jariyo_backend.domain.store.repository;

import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.domain.store.entity.ScheduleException;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleExceptionRepository extends JpaRepository<ScheduleException, UUID> {
	List<ScheduleException> findAllByStoreIdOrderByTargetDateAscCreatedAtAsc(UUID storeId);
}

