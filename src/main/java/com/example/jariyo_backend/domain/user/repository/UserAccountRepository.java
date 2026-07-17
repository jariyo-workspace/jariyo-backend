package com.example.jariyo_backend.domain.user.repository;

import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.user.entity.UserAccount;
import com.example.jariyo_backend.domain.user.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
	Optional<UserAccount> findByEmailAndStatusNot(String email, UserStatus status);
}
