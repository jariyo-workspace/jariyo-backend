package com.example.jariyo_backend.domain.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
	Optional<UserAccount> findByEmailAndStatusNot(String email, UserStatus status);

	boolean existsByEmailAndStatusNot(String email, UserStatus status);

	boolean existsByPhoneNumberAndStatusNot(String phoneNumber, UserStatus status);
}
