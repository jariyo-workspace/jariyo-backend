ALTER TABLE reservation
	ADD COLUMN checked_in_at TIMESTAMPTZ,
	ADD COLUMN service_started_at TIMESTAMPTZ;

ALTER TABLE service_session
	ADD COLUMN reservation_id UUID REFERENCES reservation (id);

ALTER TABLE service_session
	ALTER COLUMN walk_in_entry_id DROP NOT NULL;

ALTER TABLE service_session
	ADD CONSTRAINT ck_service_session_target
	CHECK (
		(walk_in_entry_id IS NOT NULL AND reservation_id IS NULL)
		OR (walk_in_entry_id IS NULL AND reservation_id IS NOT NULL)
	);

CREATE UNIQUE INDEX uk_service_session_active_reservation
	ON service_session (reservation_id)
	WHERE reservation_id IS NOT NULL AND status = 'IN_PROGRESS';
