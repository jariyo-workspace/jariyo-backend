CREATE TABLE audit_log (
	id UUID PRIMARY KEY,
	store_id UUID NOT NULL REFERENCES store (id),
	actor_type VARCHAR(32) NOT NULL CHECK (actor_type IN ('CUSTOMER', 'STORE_MEMBER', 'SYSTEM')),
	actor_id UUID,
	action VARCHAR(100) NOT NULL,
	target_type VARCHAR(100) NOT NULL,
	target_id UUID NOT NULL,
	reason VARCHAR(500),
	previous_data VARCHAR(4000),
	changed_data VARCHAR(4000),
	request_id VARCHAR(200),
	occurred_at TIMESTAMPTZ NOT NULL,
	created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_log_store_occurred
	ON audit_log (store_id, occurred_at DESC, id DESC);
