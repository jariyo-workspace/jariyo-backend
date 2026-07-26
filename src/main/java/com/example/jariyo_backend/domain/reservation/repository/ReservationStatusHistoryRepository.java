package com.example.jariyo_backend.domain.reservation.repository;

import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationStatusHistoryRepository extends JpaRepository<ReservationStatusHistory, UUID> {
	List<ReservationStatusHistory> findAllByReservationIdOrderByOccurredAtAscIdAsc(UUID reservationId);
}
