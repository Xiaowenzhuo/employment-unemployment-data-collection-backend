INSERT INTO id_allocator VALUES ('report_review_event', 1);

ALTER TABLE report_snapshot ADD COLUMN filing_id BIGINT;
ALTER TABLE report_snapshot ADD COLUMN filing_version INT;
ALTER TABLE report_snapshot ADD CONSTRAINT fk_snapshot_filing_version
    FOREIGN KEY (filing_id, filing_version) REFERENCES filing_version(filing_id, version);

CREATE TABLE report_review_event (
    id BIGINT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    submission_version INT NOT NULL,
    level VARCHAR(8) NOT NULL,
    action VARCHAR(8) NOT NULL,
    from_status VARCHAR(24) NOT NULL,
    to_status VARCHAR(24) NOT NULL,
    reviewer_user_id BIGINT NOT NULL,
    reviewer_org_id BIGINT NOT NULL,
    reviewer_display_name VARCHAR(80) NOT NULL,
    reviewed_at DATETIME(3) NOT NULL,
    comment VARCHAR(500),
    CONSTRAINT fk_report_review_snapshot FOREIGN KEY (report_id, submission_version)
        REFERENCES report_snapshot(report_id, submission_version),
    CONSTRAINT fk_report_review_user FOREIGN KEY (reviewer_user_id) REFERENCES user_account(id),
    CONSTRAINT fk_report_review_org FOREIGN KEY (reviewer_org_id) REFERENCES org_unit(id)
);
CREATE INDEX idx_report_review_report ON report_review_event(report_id, reviewed_at, id);
