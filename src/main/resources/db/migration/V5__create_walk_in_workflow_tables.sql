CREATE TABLE queue_sequence (
	id UUID PRIMARY KEY,
	store_id UUID NOT NULL REFERENCES store (id),
	operation_date DATE NOT NULL,
	last_issued_number INTEGER NOT NULL CHECK (last_issued_number > 0),
	updated_at TIMESTAMPTZ NOT NULL,
	CONSTRAINT uk_queue_sequence_store_date UNIQUE (store_id, operation_date)
);

-- 현장 운영 워크스트림
CREATE TABLE walk_in_entry (
	id UUID PRIMARY KEY,
	store_id UUID NOT NULL REFERENCES store (id),
	customer_id UUID REFERENCES customer_profile (id),
	guest_name VARCHAR(100),
	guest_phone_number VARCHAR(32),
	service_id UUID NOT NULL REFERENCES service (id),
	preferred_staff_id UUID REFERENCES store_member (id),
	party_size INTEGER NOT NULL CHECK (party_size > 0),
	operation_date DATE NOT NULL,
	queue_number INTEGER NOT NULL CHECK (queue_number > 0),
	status VARCHAR(32) NOT NULL CHECK (status IN ('WAITING', 'CALLED', 'CHECKED_IN', 'IN_SERVICE', 'COMPLETED', 'SKIPPED', 'CANCELLED', 'NO_SHOW')),
	estimated_wait_minutes INTEGER NOT NULL CHECK (estimated_wait_minutes >= 0),
	checked_in_at TIMESTAMPTZ,
	called_at TIMESTAMPTZ,
	call_expires_at TIMESTAMPTZ,
	service_started_at TIMESTAMPTZ,
	completed_at TIMESTAMPTZ,
	cancelled_at TIMESTAMPTZ,
	version BIGINT NOT NULL DEFAULT 0,
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL,
	CONSTRAINT ck_walk_in_customer_or_guest CHECK (
		(customer_id IS NOT NULL AND guest_name IS NULL AND guest_phone_number IS NULL)
		OR (customer_id IS NULL AND guest_name IS NOT NULL AND guest_phone_number IS NOT NULL)
	),
	CONSTRAINT uk_walk_in_store_date_queue UNIQUE (store_id, operation_date, queue_number)
);

CREATE INDEX idx_walk_in_queue ON walk_in_entry (store_id, operation_date, status, queue_number);
CREATE INDEX idx_walk_in_customer_created ON walk_in_entry (customer_id, created_at DESC);
CREATE UNIQUE INDEX uk_walk_in_active_customer
	ON walk_in_entry (store_id, customer_id)
	WHERE customer_id IS NOT NULL AND status IN ('WAITING', 'CALLED', 'CHECKED_IN', 'IN_SERVICE', 'SKIPPED');
CREATE UNIQUE INDEX uk_walk_in_active_guest_phone
	ON walk_in_entry (store_id, guest_phone_number)
	WHERE guest_phone_number IS NOT NULL AND status IN ('WAITING', 'CALLED', 'CHECKED_IN', 'IN_SERVICE', 'SKIPPED');

CREATE TABLE call_history (
	id UUID PRIMARY KEY,
	walk_in_entry_id UUID NOT NULL REFERENCES walk_in_entry (id),
	call_sequence INTEGER NOT NULL CHECK (call_sequence > 0),
	called_by_member_id UUID NOT NULL REFERENCES store_member (id),
	called_at TIMESTAMPTZ NOT NULL,
	expires_at TIMESTAMPTZ NOT NULL,
	response_status VARCHAR(32) NOT NULL CHECK (response_status IN ('WAITING', 'RESPONDED', 'MISSED', 'CANCELLED')),
	responded_at TIMESTAMPTZ,
	note VARCHAR(500),
	CONSTRAINT uk_call_history_entry_sequence UNIQUE (walk_in_entry_id, call_sequence)
);

CREATE UNIQUE INDEX uk_call_history_active
	ON call_history (walk_in_entry_id)
	WHERE response_status = 'WAITING';

CREATE TABLE check_in (
	id UUID PRIMARY KEY,
	store_id UUID NOT NULL REFERENCES store (id),
	customer_id UUID REFERENCES customer_profile (id),
	walk_in_entry_id UUID NOT NULL REFERENCES walk_in_entry (id),
	method VARCHAR(32) NOT NULL CHECK (method IN ('CUSTOMER_APP', 'STAFF_MANUAL')),
	status VARCHAR(32) NOT NULL CHECK (status IN ('VALID', 'CANCELLED')),
	checked_in_at TIMESTAMPTZ NOT NULL,
	cancelled_at TIMESTAMPTZ,
	processed_by_member_id UUID REFERENCES store_member (id),
	created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_check_in_valid_walk_in
	ON check_in (walk_in_entry_id)
	WHERE status = 'VALID';

CREATE TABLE service_session (
	id UUID PRIMARY KEY,
	store_id UUID NOT NULL REFERENCES store (id),
	customer_id UUID REFERENCES customer_profile (id),
	walk_in_entry_id UUID NOT NULL REFERENCES walk_in_entry (id),
	service_id UUID NOT NULL REFERENCES service (id),
	staff_id UUID NOT NULL REFERENCES store_member (id),
	status VARCHAR(32) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
	actual_start_at TIMESTAMPTZ NOT NULL,
	actual_end_at TIMESTAMPTZ,
	completion_note VARCHAR(1000),
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_service_session_active_walk_in
	ON service_session (walk_in_entry_id)
	WHERE status = 'IN_PROGRESS';

CREATE TABLE walk_in_status_history (
	id UUID PRIMARY KEY,
	walk_in_entry_id UUID NOT NULL REFERENCES walk_in_entry (id),
	previous_status VARCHAR(32),
	new_status VARCHAR(32) NOT NULL,
	actor_type VARCHAR(32) NOT NULL CHECK (actor_type IN ('CUSTOMER', 'STORE_MEMBER', 'SYSTEM')),
	actor_id UUID,
	reason VARCHAR(500),
	occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_walk_in_status_history_entry_time
	ON walk_in_status_history (walk_in_entry_id, occurred_at);

CREATE TABLE idempotency_request (
	id UUID PRIMARY KEY,
	actor_id UUID NOT NULL REFERENCES users (id),
	operation VARCHAR(160) NOT NULL,
	idempotency_key VARCHAR(200) NOT NULL,
	request_hash VARCHAR(64) NOT NULL,
	response_body TEXT NOT NULL,
	expires_at TIMESTAMPTZ NOT NULL,
	created_at TIMESTAMPTZ NOT NULL,
	completed_at TIMESTAMPTZ NOT NULL,
	CONSTRAINT uk_idempotency_actor_operation_key UNIQUE (actor_id, operation, idempotency_key)
);

CREATE INDEX idx_idempotency_request_expires_at ON idempotency_request (expires_at);
