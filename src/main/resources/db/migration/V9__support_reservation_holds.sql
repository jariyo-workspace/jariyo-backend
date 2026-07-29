ALTER TABLE reservation
	DROP CONSTRAINT reservation_customer_id_fkey;

ALTER TABLE reservation
	ADD CONSTRAINT fk_reservation_customer_profile
	FOREIGN KEY (customer_id) REFERENCES customer_profile (id);

ALTER TABLE async_event_outbox
	DROP CONSTRAINT async_event_outbox_type_check;

ALTER TABLE async_event_outbox
	ADD CONSTRAINT async_event_outbox_type_check CHECK (
		type IN (
			'RESERVATION_HOLD_CREATED',
			'RESERVATION_CONFIRMED',
			'RESERVATION_HOLD_EXPIRED',
			'RESERVATION_CANCELLED',
			'SLOT_OFFER_CREATED',
			'SLOT_OFFER_ACCEPTED',
			'WALK_IN_CALLED',
			'WALK_IN_STATUS_CHANGED'
		));

ALTER TABLE failed_async_job
	DROP CONSTRAINT failed_async_job_type_check;

ALTER TABLE failed_async_job
	ADD CONSTRAINT failed_async_job_type_check CHECK (
		type IN (
			'RESERVATION_HOLD_CREATED',
			'RESERVATION_CONFIRMED',
			'RESERVATION_HOLD_EXPIRED',
			'RESERVATION_CANCELLED',
			'SLOT_OFFER_CREATED',
			'SLOT_OFFER_ACCEPTED',
			'WALK_IN_CALLED',
			'WALK_IN_STATUS_CHANGED'
		));
