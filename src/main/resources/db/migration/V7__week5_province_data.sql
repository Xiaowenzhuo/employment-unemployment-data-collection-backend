ALTER TABLE enterprise_report ADD COLUMN voided BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE enterprise_report ADD COLUMN voided_at DATETIME(3);

INSERT INTO id_allocator VALUES ('report_correction_event', 1);
INSERT INTO id_allocator VALUES ('report_void_event', 1);

CREATE TABLE report_correction_event (
    id BIGINT PRIMARY KEY,
    operation_id BIGINT NOT NULL,
    report_id BIGINT NOT NULL,
    submission_version INT NOT NULL,
    field_name VARCHAR(64) NOT NULL,
    old_value VARCHAR(500),
    new_value VARCHAR(500),
    reason VARCHAR(500) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    actor_display_name VARCHAR(80) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_correction_report FOREIGN KEY (report_id) REFERENCES enterprise_report(id),
    CONSTRAINT fk_correction_actor FOREIGN KEY (actor_user_id) REFERENCES user_account(id)
);
CREATE INDEX idx_correction_report ON report_correction_event(report_id, id);

CREATE TABLE report_void_event (
    id BIGINT PRIMARY KEY,
    report_id BIGINT NOT NULL UNIQUE,
    submission_version INT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    actor_display_name VARCHAR(80) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_void_report FOREIGN KEY (report_id) REFERENCES enterprise_report(id),
    CONSTRAINT fk_void_actor FOREIGN KEY (actor_user_id) REFERENCES user_account(id)
);
