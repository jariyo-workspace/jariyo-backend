package com.example.jariyo_backend.domain.waitlist.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.waitlist.entity.SlotOffer;
import com.example.jariyo_backend.domain.waitlist.entity.SlotOfferStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlotOfferRepository extends JpaRepository<SlotOffer, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from SlotOffer o where o.id = :id")
	Optional<SlotOffer> findByIdForUpdate(@Param("id") UUID id);

	Optional<SlotOffer> findFirstByWaitlistEntryIdAndStatusOrderByCreatedAtDesc(UUID waitlistEntryId, SlotOfferStatus status);

	List<SlotOffer> findAllByWaitlistEntryIdAndStatus(UUID waitlistEntryId, SlotOfferStatus status);

	@Query("""
		select o from SlotOffer o
		where o.status = :status
			and o.expiresAt <= :threshold
		""")
	List<SlotOffer> findAllExpiredPendingOffers(@Param("status") SlotOfferStatus status, @Param("threshold") Instant threshold);

	@Query("""
		select o from SlotOffer o
		join WaitlistEntry w on w.id = o.waitlistEntryId
		where w.customerId = :customerId
			and (:status is null or o.status = :status)
		order by o.createdAt desc
		""")
	List<SlotOffer> findMine(@Param("customerId") UUID customerId, @Param("status") SlotOfferStatus status);

	boolean existsByStoreIdAndServiceIdAndStaffIdAndStartAtAndStatus(
		UUID storeId, UUID serviceId, UUID staffId, Instant startAt, SlotOfferStatus status);

	@Query("""
		select o from SlotOffer o
		where o.storeId = :storeId
			and o.status = :status
			and o.expiresAt > :now
		order by o.expiresAt asc
		""")
	List<SlotOffer> findActiveByStoreIdAndStatus(
		@Param("storeId") UUID storeId,
		@Param("status") SlotOfferStatus status,
		@Param("now") Instant now);
}
