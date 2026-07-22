package com.example.jariyo_backend.domain.walkin.entity;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "call_history")
public class CallHistory {
	@Id @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;
	@Column(name = "walk_in_entry_id", nullable = false) private UUID walkInEntryId;
	@Column(name = "call_sequence", nullable = false) private int callSequence;
	@Column(name = "called_by_member_id", nullable = false) private UUID calledByMemberId;
	@Column(name = "called_at", nullable = false) private Instant calledAt;
	@Column(name = "expires_at", nullable = false) private Instant expiresAt;
	@Enumerated(EnumType.STRING) @Column(name = "response_status", nullable = false, length = 32)
	private CallResponseStatus responseStatus;
	@Column(name = "responded_at") private Instant respondedAt;
	@Column(length = 500) private String note;

	protected CallHistory() { }

	public CallHistory(UUID walkInEntryId, int callSequence, UUID calledByMemberId, Instant calledAt, Instant expiresAt) {
		this.walkInEntryId = walkInEntryId;
		this.callSequence = callSequence;
		this.calledByMemberId = calledByMemberId;
		this.calledAt = calledAt;
		this.expiresAt = expiresAt;
		this.responseStatus = CallResponseStatus.WAITING;
	}

	public void respond(CallResponseStatus status, Instant at, String note) {
		responseStatus = status;
		respondedAt = at;
		this.note = note;
	}

	public CallResponseStatus getResponseStatus() { return responseStatus; }
	public Instant getExpiresAt() { return expiresAt; }
}
