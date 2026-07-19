ALTER TABLE store_member
	ADD CONSTRAINT fk_store_member_store
	FOREIGN KEY (store_id) REFERENCES store (id);

ALTER TABLE schedule_exception
	ADD CONSTRAINT fk_schedule_exception_created_by_member
	FOREIGN KEY (created_by_member_id) REFERENCES store_member (id);

ALTER TABLE staff_schedule_exception
	ADD CONSTRAINT fk_staff_schedule_exception_created_by_member
	FOREIGN KEY (created_by_member_id) REFERENCES store_member (id);
