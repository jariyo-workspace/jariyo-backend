package com.example.jariyo_backend.common.async;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface FailedAsyncJobRepository extends JpaRepository<FailedAsyncJob, UUID> {
	Optional<FailedAsyncJob> findByTypeAndReferenceTypeAndReferenceId(AsyncEventType type, String referenceType,
		UUID referenceId);

	Optional<FailedAsyncJob> findByIdAndStoreId(UUID id, UUID storeId);

	@Query("""
		select j from FailedAsyncJob j
		where j.storeId = :storeId
			and (:type is null or j.type = :type)
			and (:status is null or j.status = :status)
			and (:from is null or j.failedAt >= :from)
			and (:to is null or j.failedAt < :to)
		order by j.failedAt desc, j.id desc
		""")
	List<FailedAsyncJob> findAllByStoreIdAndFilters(
		@Param("storeId") UUID storeId,
		@Param("type") AsyncEventType type,
		@Param("status") FailedJobStatus status,
		@Param("from") Instant from,
		@Param("to") Instant to);
}
