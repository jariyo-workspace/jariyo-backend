CREATE TABLE reservation_status_history (
	id UUID PRIMARY KEY,
	reservation_id UUID NOT NULL REFERENCES reservation (id),
	previous_status VARCHAR(32),
	next_status VARCHAR(32) NOT NULL,
	changed_by_type VARCHAR(32) NOT NULL,
	changed_by_id UUID,
	reason_code VARCHAR(64) NOT NULL,
	note VARCHAR(500),
	occurred_at TIMESTAMPTZ NOT NULL,
	CONSTRAINT ck_reservation_history_previous_status CHECK (
		previous_status IS NULL OR previous_status IN (
			'HELD', 'CONFIRMED', 'CHECKED_IN', 'IN_SERVICE', 'COMPLETED', 'CANCELLED', 'NO_SHOW', 'EXPIRED')),
	CONSTRAINT ck_reservation_history_next_status CHECK (
		next_status IN ('HELD', 'CONFIRMED', 'CHECKED_IN', 'IN_SERVICE', 'COMPLETED', 'CANCELLED', 'NO_SHOW', 'EXPIRED')),
	CONSTRAINT ck_reservation_history_actor_type CHECK (
		changed_by_type IN ('CUSTOMER', 'STORE_MEMBER', 'SYSTEM'))
);

CREATE INDEX idx_reservation_status_history_reservation_occurred
	ON reservation_status_history (reservation_id, occurred_at, id);

INSERT INTO reservation_status_history (
	id, reservation_id, previous_status, next_status, changed_by_type, changed_by_id, reason_code, note, occurred_at)
SELECT
	md5(id::text || ':reservation-status-history')::uuid,
	id,
	NULL,
	status,
	'SYSTEM',
	NULL,
	'DATA_MIGRATION',
	NULL,
	created_at
FROM reservation;
