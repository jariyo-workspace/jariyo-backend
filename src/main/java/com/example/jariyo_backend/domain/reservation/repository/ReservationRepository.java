package com.example.jariyo_backend.domain.reservation.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.reservation.entity.Reservation;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
	@Query(value = """
		select r.*
		from reservation r
		join store s on s.id = r.store_id
		where r.customer_id = :customerId
			and (cast(:status as text) is null or r.status = cast(:status as text))
			and (cast(:fromDate as date) is null
				or (r.start_at at time zone s.timezone)::date >= cast(:fromDate as date))
			and (cast(:toDate as date) is null
				or (r.start_at at time zone s.timezone)::date <= cast(:toDate as date))
			and (cast(:cursorStartAt as timestamptz) is null
				or (r.start_at, r.id) < (cast(:cursorStartAt as timestamptz), cast(:cursorId as uuid)))
		order by r.start_at desc, r.id desc
		""", nativeQuery = true)
	List<Reservation> findPageByCustomerId(
		@Param("customerId") UUID customerId,
		@Param("status") String status,
		@Param("fromDate") java.time.LocalDate fromDate,
		@Param("toDate") java.time.LocalDate toDate,
		@Param("cursorStartAt") Instant cursorStartAt,
		@Param("cursorId") UUID cursorId,
		Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Reservation r where r.id = :id")
	Optional<Reservation> findByIdForUpdate(@Param("id") UUID id);

	@Query("""
		select r from Reservation r
		where r.storeId = :storeId
			and r.assignedStaffId in :staffIds
			and r.status in :statuses
			and (r.status <> :heldStatus or r.holdExpiresAt > :now)
			and r.occupiedUntil > :rangeStart
			and r.startAt < :rangeEnd
		""")
	List<Reservation> findActiveReservationsForAvailability(
		@Param("storeId") UUID storeId,
		@Param("staffIds") Collection<UUID> staffIds,
		@Param("statuses") Collection<ReservationStatus> statuses,
		@Param("heldStatus") ReservationStatus heldStatus,
		@Param("now") Instant now,
		@Param("rangeStart") Instant rangeStart,
		@Param("rangeEnd") Instant rangeEnd);

	@Query("""
		select case when count(r) > 0 then true else false end from Reservation r
		where r.storeId = :storeId
			and ((:staffId is null and r.assignedStaffId is null) or r.assignedStaffId = :staffId)
			and r.status in :statuses
			and (r.status <> :heldStatus or r.holdExpiresAt > :now)
			and r.occupiedUntil > :rangeStart
			and r.startAt < :rangeEnd
		""")
	boolean existsOverlappingReservation(
		@Param("storeId") UUID storeId,
		@Param("staffId") UUID staffId,
		@Param("statuses") Collection<ReservationStatus> statuses,
		@Param("heldStatus") ReservationStatus heldStatus,
		@Param("now") Instant now,
		@Param("rangeStart") Instant rangeStart,
		@Param("rangeEnd") Instant rangeEnd);

	List<Reservation> findAllByStoreIdAndStartAtBetween(UUID storeId, Instant from, Instant to);

	@Query("""
		select r from Reservation r
		where r.storeId = :storeId
			and r.startAt >= :rangeStart
			and r.startAt < :rangeEnd
		order by r.startAt asc
		""")
	List<Reservation> findDailyReservations(
		@Param("storeId") UUID storeId,
		@Param("rangeStart") Instant rangeStart,
		@Param("rangeEnd") Instant rangeEnd);

	@Query("""
		select case when count(r) > 0 then true else false end from Reservation r
		where r.customerId = :customerId
			and r.status in :statuses
			and (r.status <> :heldStatus or r.holdExpiresAt > :now)
			and r.occupiedUntil > :rangeStart
			and r.startAt < :rangeEnd
		""")
	boolean existsOverlappingCustomerReservation(
		@Param("customerId") UUID customerId,
		@Param("statuses") Collection<ReservationStatus> statuses,
		@Param("heldStatus") ReservationStatus heldStatus,
		@Param("now") Instant now,
		@Param("rangeStart") Instant rangeStart,
		@Param("rangeEnd") Instant rangeEnd);

	@Query("""
		select r.id from Reservation r
		where r.status = :status
			and r.holdExpiresAt <= :now
		order by r.holdExpiresAt asc
		""")
	List<UUID> findExpiredHoldIds(@Param("status") ReservationStatus status, @Param("now") Instant now);

	@Query("""
		select r from Reservation r
		where r.storeId = :storeId
			and r.status in :statuses
			and (r.status <> :heldStatus or r.holdExpiresAt > :now)
			and r.occupiedUntil > :now
		order by r.startAt asc, r.id asc
		""")
	List<Reservation> findFutureActiveReservations(
		@Param("storeId") UUID storeId,
		@Param("statuses") Collection<ReservationStatus> statuses,
		@Param("heldStatus") ReservationStatus heldStatus,
		@Param("now") Instant now);
}
