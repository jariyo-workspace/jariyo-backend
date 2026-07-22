CREATE TABLE reservation (
	id UUID PRIMARY KEY,
	store_id UUID NOT NULL REFERENCES store (id),
	customer_id UUID NOT NULL REFERENCES users (id),
	service_id UUID NOT NULL REFERENCES service (id),
	assigned_staff_id UUID REFERENCES store_member (id),
	source VARCHAR(32) NOT NULL CHECK (
		source IN ('CUSTOMER_BOOKING', 'STORE_MANUAL', 'WAITLIST_OFFER', 'WALK_IN_CONVERSION')),
	status VARCHAR(32) NOT NULL CHECK (
		status IN ('HELD', 'CONFIRMED', 'CHECKED_IN', 'IN_SERVICE', 'COMPLETED', 'CANCELLED', 'NO_SHOW', 'EXPIRED')),
	start_at TIMESTAMPTZ NOT NULL,
	service_end_at TIMESTAMPTZ NOT NULL,
	occupied_until TIMESTAMPTZ NOT NULL,
	party_size INTEGER NOT NULL,
	customer_note VARCHAR(1000),
	store_note VARCHAR(1000),
	hold_expires_at TIMESTAMPTZ,
	cancellation_reason VARCHAR(255),
	cancelled_by_type VARCHAR(32),
	cancelled_by_id UUID,
	confirmed_at TIMESTAMPTZ,
	cancelled_at TIMESTAMPTZ,
	completed_at TIMESTAMPTZ,
	version BIGINT NOT NULL DEFAULT 0,
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_reservation_availability_lookup
	ON reservation (store_id, assigned_staff_id, status, start_at, occupied_until);
