package com.example.jariyo_backend.domain.store.repository;

import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.domain.store.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, UUID> {
	List<Store> findAllByOrderByNameAsc();
}

