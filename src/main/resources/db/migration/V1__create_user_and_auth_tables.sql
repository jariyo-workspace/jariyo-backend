CREATE TABLE users (
	id UUID PRIMARY KEY,
	email VARCHAR(320) NOT NULL,
	phone_number VARCHAR(32) NOT NULL,
	password_hash VARCHAR(512) NOT NULL,
	status VARCHAR(32) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL,
	last_login_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_users_active_email
	ON users (email)
	WHERE status <> 'WITHDRAWN';

CREATE UNIQUE INDEX uk_users_active_phone_number
	ON users (phone_number)
	WHERE status <> 'WITHDRAWN';

CREATE TABLE customer_profile (
	id UUID PRIMARY KEY,
	user_id UUID NOT NULL UNIQUE REFERENCES users (id),
	display_name VARCHAR(100) NOT NULL,
	marketing_consent BOOLEAN NOT NULL,
	notification_consent BOOLEAN NOT NULL,
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE store_member (
	id UUID PRIMARY KEY,
	store_id UUID NOT NULL,
	user_id UUID NOT NULL REFERENCES users (id),
	role VARCHAR(32) NOT NULL CHECK (role IN ('OWNER', 'MANAGER', 'STAFF')),
	display_name VARCHAR(100) NOT NULL,
	status VARCHAR(32) NOT NULL CHECK (status IN ('ACTIVE', 'ON_LEAVE', 'INACTIVE')),
	booking_enabled BOOLEAN NOT NULL,
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL,
	CONSTRAINT uk_store_member_user_store UNIQUE (user_id, store_id)
);

CREATE INDEX idx_store_member_store_status ON store_member (store_id, status);

CREATE TABLE refresh_token (
	id UUID PRIMARY KEY,
	user_id UUID NOT NULL REFERENCES users (id),
	family_id UUID NOT NULL,
	token_hash VARCHAR(64) NOT NULL UNIQUE,
	status VARCHAR(32) NOT NULL CHECK (status IN ('ACTIVE', 'ROTATED', 'REVOKED', 'REUSED')),
	expires_at TIMESTAMPTZ NOT NULL,
	used_at TIMESTAMPTZ,
	replaced_by_token_id UUID UNIQUE REFERENCES refresh_token (id),
	revoked_at TIMESTAMPTZ,
	created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_refresh_token_family_id ON refresh_token (family_id);
CREATE INDEX idx_refresh_token_user_id ON refresh_token (user_id);
