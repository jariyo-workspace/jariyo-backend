package com.example.jariyo_backend.domain.user.repository;

import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.user.entity.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, UUID> {
	Optional<CustomerProfile> findByUser_Id(UUID userId);
}
