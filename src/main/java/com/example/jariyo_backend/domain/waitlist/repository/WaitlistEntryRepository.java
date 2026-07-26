package com.example.jariyo_backend.domain.waitlist.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.waitlist.entity.WaitlistEntry;
import com.example.jariyo_backend.domain.waitlist.entity.WaitlistStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, UUID> {
	List<WaitlistEntry> findAllByCustomerIdOrderByCreatedAtDesc(UUID customerId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select w from WaitlistEntry w where w.id = :id")
	Optional<WaitlistEntry> findByIdForUpdate(@Param("id") UUID id);

	@Query("""
		select w from WaitlistEntry w
		where w.customerId = :customerId
			and w.storeId = :storeId
			and w.serviceId = :serviceId
			and w.desiredDate = :desiredDate
			and w.status in :statuses
		""")
	List<WaitlistEntry> findDuplicates(
		@Param("customerId") UUID customerId,
		@Param("storeId") UUID storeId,
		@Param("serviceId") UUID serviceId,
		@Param("desiredDate") LocalDate desiredDate,
		@Param("statuses") Collection<WaitlistStatus> statuses);

	@Query("""
		select w from WaitlistEntry w
		where w.storeId = :storeId
			and w.serviceId = :serviceId
			and w.desiredDate = :desiredDate
			and w.status = 'WAITING'
		order by w.sequenceNumber asc, w.createdAt asc
		""")
	List<WaitlistEntry> findOfferCandidates(
		@Param("storeId") UUID storeId,
		@Param("serviceId") UUID serviceId,
		@Param("desiredDate") LocalDate desiredDate);

	@Query("""
		select w from WaitlistEntry w
		where w.status in :statuses
			and w.expiresAt <= :threshold
		""")
	List<WaitlistEntry> findExpiredEntries(
		@Param("statuses") Collection<WaitlistStatus> statuses,
		@Param("threshold") java.time.Instant threshold);

	@Query("select coalesce(max(w.sequenceNumber), 0) from WaitlistEntry w where w.storeId = :storeId")
	int findMaxSequenceNumberByStoreId(@Param("storeId") UUID storeId);

	List<WaitlistEntry> findAllByStoreIdAndDesiredDateOrderBySequenceNumberAscCreatedAtAsc(UUID storeId, LocalDate desiredDate);
}
