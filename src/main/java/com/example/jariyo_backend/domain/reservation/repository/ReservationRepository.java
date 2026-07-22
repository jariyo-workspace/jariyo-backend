package com.example.jariyo_backend.domain.reservation.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.domain.reservation.entity.Reservation;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
	@Query("""
		select r from Reservation r
		where r.storeId = :storeId
			and r.assignedStaffId in :staffIds
			and r.status in :statuses
			and r.occupiedUntil > :rangeStart
			and r.startAt < :rangeEnd
		""")
	List<Reservation> findActiveReservationsForAvailability(
		@Param("storeId") UUID storeId,
		@Param("staffIds") Collection<UUID> staffIds,
		@Param("statuses") Collection<ReservationStatus> statuses,
		@Param("rangeStart") Instant rangeStart,
		@Param("rangeEnd") Instant rangeEnd);
}
