package com.example.jariyo_backend.domain.walkin.repository;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.walkin.entity.WalkInEntry;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalkInEntryRepository extends JpaRepository<WalkInEntry, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select w from WalkInEntry w where w.id = :id")
	Optional<WalkInEntry> findByIdForUpdate(@Param("id") UUID id);

	List<WalkInEntry> findAllByStoreIdAndOperationDateOrderByQueueNumberAsc(UUID storeId, LocalDate operationDate);

	List<WalkInEntry> findAllByCustomerIdOrderByCreatedAtDesc(UUID customerId);

	List<WalkInEntry> findAllByStoreIdAndCreatedAtBetween(UUID storeId, Instant from, Instant to);

}
