package com.example.jariyo_backend.domain.waitlist.repository;

import java.util.UUID;
import com.example.jariyo_backend.domain.waitlist.entity.SlotOfferStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlotOfferStatusHistoryRepository extends JpaRepository<SlotOfferStatusHistory, UUID> {
}
