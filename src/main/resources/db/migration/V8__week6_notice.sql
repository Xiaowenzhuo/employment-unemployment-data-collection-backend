INSERT INTO id_allocator VALUES ('province_notice', 1);
INSERT INTO id_allocator VALUES ('province_notice_event', 1);

CREATE TABLE province_notice (
    id BIGINT PRIMARY KEY,
    province_org_id BIGINT NOT NULL,
    publisher_user_id BIGINT NOT NULL,
    publisher_unit_name VARCHAR(120) NOT NULL,
    title VARCHAR(50) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    current_version INT NOT NULL,
    published_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at DATETIME(3),
    CONSTRAINT fk_notice_province FOREIGN KEY (province_org_id) REFERENCES org_unit(id),
    CONSTRAINT fk_notice_publisher FOREIGN KEY (publisher_user_id) REFERENCES user_account(id)
);
CREATE INDEX idx_notice_visible ON province_notice(province_org_id, deleted, published_at, id);

CREATE TABLE province_notice_event (
    id BIGINT PRIMARY KEY,
    notice_id BIGINT NOT NULL,
    action VARCHAR(8) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    from_version INT NOT NULL,
    to_version INT NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_notice_event_notice FOREIGN KEY (notice_id) REFERENCES province_notice(id),
    CONSTRAINT fk_notice_event_actor FOREIGN KEY (actor_user_id) REFERENCES user_account(id)
);
CREATE INDEX idx_notice_event_notice ON province_notice_event(notice_id, id);
