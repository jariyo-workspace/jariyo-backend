package com.example.jariyo_backend.domain.user;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserAccount {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(nullable = false, length = 320)
	private String email;

	@Column(name = "phone_number", nullable = false, length = 32)
	private String phoneNumber;

	@Column(name = "password_hash", nullable = false, length = 512)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private UserStatus status;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	protected UserAccount() {
	}

	public UserAccount(String email, String phoneNumber, String passwordHash) {
		this.email = email;
		this.phoneNumber = phoneNumber;
		this.passwordHash = passwordHash;
		this.status = UserStatus.ACTIVE;
	}

	public void recordLogin(Instant loggedInAt) {
		lastLoginAt = loggedInAt;
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public UserStatus getStatus() {
		return status;
	}
}
