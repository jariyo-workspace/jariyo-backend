package com.example.jariyo_backend.domain.walkin.repository;

import java.util.UUID;
import com.example.jariyo_backend.domain.walkin.entity.WalkInStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalkInStatusHistoryRepository extends JpaRepository<WalkInStatusHistory, UUID> {
}
