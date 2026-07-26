CREATE TABLE async_event_outbox (
	id UUID PRIMARY KEY,
	type VARCHAR(64) NOT NULL CHECK (
		type IN ('RESERVATION_CANCELLED', 'SLOT_OFFER_CREATED', 'SLOT_OFFER_ACCEPTED', 'WALK_IN_CALLED', 'WALK_IN_STATUS_CHANGED')),
	store_id UUID REFERENCES store (id),
	reference_type VARCHAR(64) NOT NULL,
	reference_id UUID NOT NULL,
	payload_json VARCHAR(4000) NOT NULL,
	status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
	attempt_count INTEGER NOT NULL DEFAULT 0,
	processed_at TIMESTAMPTZ,
	last_error_code VARCHAR(128),
	last_error_message VARCHAR(1000),
	version BIGINT NOT NULL DEFAULT 0,
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_async_event_outbox_status_created
	ON async_event_outbox (status, created_at);

CREATE INDEX idx_async_event_outbox_reference
	ON async_event_outbox (reference_type, reference_id);

CREATE TABLE failed_async_job (
	id UUID PRIMARY KEY,
	store_id UUID REFERENCES store (id),
	type VARCHAR(64) NOT NULL CHECK (
		type IN ('RESERVATION_CANCELLED', 'SLOT_OFFER_CREATED', 'SLOT_OFFER_ACCEPTED', 'WALK_IN_CALLED', 'WALK_IN_STATUS_CHANGED')),
	reference_type VARCHAR(64) NOT NULL,
	reference_id UUID NOT NULL,
	status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'FAILED', 'RESOLVED', 'IGNORED')),
	attempt_count INTEGER NOT NULL DEFAULT 0,
	last_error_code VARCHAR(128),
	last_error_message VARCHAR(1000),
	failed_at TIMESTAMPTZ NOT NULL,
	ignored_reason VARCHAR(1000),
	version BIGINT NOT NULL DEFAULT 0,
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL,
	CONSTRAINT uk_failed_async_job_reference UNIQUE (type, reference_type, reference_id)
);

CREATE INDEX idx_failed_async_job_store_status
	ON failed_async_job (store_id, status, failed_at DESC);
