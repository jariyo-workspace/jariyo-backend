CREATE TABLE store (
	id UUID PRIMARY KEY,
	name VARCHAR(120) NOT NULL,
	description VARCHAR(1000),
	phone_number VARCHAR(32) NOT NULL,
	address VARCHAR(255) NOT NULL,
	timezone VARCHAR(64) NOT NULL,
	status VARCHAR(32) NOT NULL CHECK (status IN ('PREPARING', 'ACTIVE', 'TEMPORARILY_CLOSED', 'CLOSED')),
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE store_policy (
	id UUID PRIMARY KEY,
	store_id UUID NOT NULL UNIQUE REFERENCES store (id),
	booking_open_days INTEGER NOT NULL,
	minimum_booking_notice_minutes INTEGER NOT NULL,
	cancellation_deadline_minutes INTEGER NOT NULL,
	check_in_open_before_minutes INTEGER NOT NULL,
	late_tolerance_minutes INTEGER NOT NULL,
	no_show_after_minutes INTEGER NOT NULL,
	reservation_hold_minutes INTEGER NOT NULL,
	slot_offer_expiration_minutes INTEGER NOT NULL,
	walk_in_call_timeout_minutes INTEGER NOT NULL,
	waitlist_enabled BOOLEAN NOT NULL,
	walk_in_enabled BOOLEAN NOT NULL,
	auto_no_show_enabled BOOLEAN NOT NULL,
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE business_hour (
	id UUID PRIMARY KEY,
	store_id UUID NOT NULL REFERENCES store (id),
	day_of_week VARCHAR(16) NOT NULL CHECK (
		day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
	open_time TIME,
	close_time TIME,
	is_closed BOOLEAN NOT NULL
);

CREATE INDEX idx_business_hour_store_day ON business_hour (store_id, day_of_week);

CREATE TABLE schedule_exception (
	id UUID PRIMARY KEY,
	store_id UUID NOT NULL REFERENCES store (id),
	target_date DATE NOT NULL,
	type VARCHAR(32) NOT NULL CHECK (type IN ('CLOSED_ALL_DAY', 'SPECIAL_OPENING_HOURS', 'BLOCKED_PERIOD')),
	start_time TIME,
	end_time TIME,
	reason VARCHAR(255),
	created_by_member_id UUID REFERENCES store_member (id),
	created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_schedule_exception_store_date ON schedule_exception (store_id, target_date);

CREATE TABLE service (
	id UUID PRIMARY KEY,
	store_id UUID NOT NULL REFERENCES store (id),
	name VARCHAR(120) NOT NULL,
	description VARCHAR(1000),
	duration_minutes INTEGER NOT NULL,
	cleanup_minutes INTEGER NOT NULL,
	capacity INTEGER NOT NULL,
	status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_service_store_status ON service (store_id, status);

CREATE TABLE staff_service (
	id UUID PRIMARY KEY,
	store_member_id UUID NOT NULL REFERENCES store_member (id),
	service_id UUID NOT NULL REFERENCES service (id),
	custom_duration_minutes INTEGER,
	active BOOLEAN NOT NULL,
	CONSTRAINT uk_staff_service_member_service UNIQUE (store_member_id, service_id)
);

CREATE INDEX idx_staff_service_service_active ON staff_service (service_id, active);

CREATE TABLE staff_schedule (
	id UUID PRIMARY KEY,
	store_member_id UUID NOT NULL REFERENCES store_member (id),
	day_of_week VARCHAR(16) NOT NULL CHECK (
		day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
	start_time TIME NOT NULL,
	end_time TIME NOT NULL,
	valid_from DATE NOT NULL,
	valid_until DATE,
	created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_staff_schedule_member_day ON staff_schedule (store_member_id, day_of_week);

CREATE TABLE staff_schedule_exception (
	id UUID PRIMARY KEY,
	store_member_id UUID NOT NULL REFERENCES store_member (id),
	target_date DATE NOT NULL,
	type VARCHAR(32) NOT NULL CHECK (type IN ('DAY_OFF', 'CUSTOM_WORKING_HOURS', 'BLOCKED_PERIOD')),
	start_time TIME,
	end_time TIME,
	reason VARCHAR(255),
	created_by_member_id UUID REFERENCES store_member (id),
	created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_staff_schedule_exception_member_date
	ON staff_schedule_exception (store_member_id, target_date);

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
