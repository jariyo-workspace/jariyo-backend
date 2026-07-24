package com.example.jariyo_backend.domain.user.entity;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer_profile")
public class CustomerProfile {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private UserAccount user;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	@Column(name = "marketing_consent", nullable = false)
	private boolean marketingConsent;

	@Column(name = "notification_consent", nullable = false)
	private boolean notificationConsent;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CustomerProfile() {
	}

	public CustomerProfile(UserAccount user, String displayName, boolean marketingConsent, boolean notificationConsent) {
		this.user = user;
		this.displayName = displayName;
		this.marketingConsent = marketingConsent;
		this.notificationConsent = notificationConsent;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return user.getId();
	}

	public String getDisplayName() {
		return displayName;
	}

	public boolean isMarketingConsent() {
		return marketingConsent;
	}

	public boolean isNotificationConsent() {
		return notificationConsent;
	}
}
