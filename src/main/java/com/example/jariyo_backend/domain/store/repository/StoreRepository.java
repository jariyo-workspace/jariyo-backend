package com.example.jariyo_backend.domain.store.repository;

import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.entity.StoreStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, UUID> {
	Optional<Store> findByIdAndStatus(UUID id, StoreStatus status);
}
