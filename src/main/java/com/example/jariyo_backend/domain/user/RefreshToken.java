package com.example.jariyo_backend.domain.user;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Column(name = "family_id", nullable = false)
	private UUID familyId;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private RefreshTokenStatus status;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "replaced_by_token_id")
	private RefreshToken replacedByToken;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected RefreshToken() {
	}

	public RefreshToken(UserAccount user, UUID familyId, String tokenHash, Instant expiresAt) {
		this.user = user;
		this.familyId = familyId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.status = RefreshTokenStatus.ACTIVE;
	}

	public void rotate(RefreshToken replacement, Instant usedAt) {
		status = RefreshTokenStatus.ROTATED;
		this.usedAt = usedAt;
		replacedByToken = replacement;
	}

	public void markReused(Instant usedAt) {
		status = RefreshTokenStatus.REUSED;
		this.usedAt = usedAt;
	}

	public void revoke(Instant revokedAt) {
		if (status == RefreshTokenStatus.ACTIVE) {
			status = RefreshTokenStatus.REVOKED;
		}
		this.revokedAt = revokedAt;
	}

	public UserAccount getUser() {
		return user;
	}

	public UUID getFamilyId() {
		return familyId;
	}

	public RefreshTokenStatus getStatus() {
		return status;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}
}
