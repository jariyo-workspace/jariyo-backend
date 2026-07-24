CREATE TABLE waitlist_entry (
	id UUID PRIMARY KEY,
	store_id UUID NOT NULL REFERENCES store (id),
	customer_id UUID NOT NULL REFERENCES customer_profile (id),
	service_id UUID NOT NULL REFERENCES service (id),
	preferred_staff_id UUID REFERENCES store_member (id),
	staff_preference_type VARCHAR(32) NOT NULL CHECK (
		staff_preference_type IN ('SPECIFIC_ONLY', 'SPECIFIC_PREFERRED', 'ANY_STAFF')),
	desired_date DATE NOT NULL,
	acceptable_start_time TIME NOT NULL,
	acceptable_end_time TIME NOT NULL,
	party_size INTEGER NOT NULL,
	status VARCHAR(32) NOT NULL CHECK (
		status IN ('WAITING', 'OFFERED', 'RESERVED', 'CANCELLED', 'EXPIRED')),
	sequence_number INTEGER NOT NULL,
	priority INTEGER NOT NULL DEFAULT 0,
	expires_at TIMESTAMPTZ NOT NULL,
	cancelled_at TIMESTAMPTZ,
	reserved_at TIMESTAMPTZ,
	version BIGINT NOT NULL DEFAULT 0,
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_waitlist_lookup
	ON waitlist_entry (store_id, service_id, desired_date, status, sequence_number);

CREATE INDEX idx_waitlist_customer
	ON waitlist_entry (customer_id, created_at DESC);

CREATE TABLE slot_offer (
	id UUID PRIMARY KEY,
	waitlist_entry_id UUID NOT NULL REFERENCES waitlist_entry (id),
	store_id UUID NOT NULL REFERENCES store (id),
	service_id UUID NOT NULL REFERENCES service (id),
	staff_id UUID REFERENCES store_member (id),
	start_at TIMESTAMPTZ NOT NULL,
	service_end_at TIMESTAMPTZ NOT NULL,
	occupied_until TIMESTAMPTZ NOT NULL,
	source_reservation_id UUID REFERENCES reservation (id),
	status VARCHAR(32) NOT NULL CHECK (
		status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'REVOKED')),
	expires_at TIMESTAMPTZ NOT NULL,
	accepted_at TIMESTAMPTZ,
	declined_at TIMESTAMPTZ,
	resulting_reservation_id UUID REFERENCES reservation (id),
	version BIGINT NOT NULL DEFAULT 0,
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_slot_offer_pending_slot
	ON slot_offer (store_id, service_id, staff_id, start_at)
	WHERE status = 'PENDING';

CREATE UNIQUE INDEX uk_slot_offer_pending_waitlist
	ON slot_offer (waitlist_entry_id)
	WHERE status = 'PENDING';

CREATE INDEX idx_slot_offer_waitlist
	ON slot_offer (waitlist_entry_id, status, created_at DESC);

CREATE INDEX idx_slot_offer_expiration
	ON slot_offer (status, expires_at);

CREATE TABLE slot_offer_status_history (
	id UUID PRIMARY KEY,
	slot_offer_id UUID NOT NULL REFERENCES slot_offer (id),
	previous_status VARCHAR(32),
	next_status VARCHAR(32) NOT NULL,
	changed_by_type VARCHAR(32) NOT NULL,
	changed_by_id UUID,
	reason_code VARCHAR(64) NOT NULL,
	occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_slot_offer_status_history_offer
	ON slot_offer_status_history (slot_offer_id, occurred_at);
