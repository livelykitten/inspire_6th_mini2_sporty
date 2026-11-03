-- W-02 match_db additions. Existing tables/data are not modified.
-- JPA ddl-auto=update creates these in normal application startup. For validate-only
-- environments, apply this once before starting the new match-service version.
CREATE TABLE IF NOT EXISTS match_user_guard (
    user_id BIGINT NOT NULL PRIMARY KEY,
    is_blocked BIT(1) NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS processed_event (
    event_id VARCHAR(36) NOT NULL PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS match_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    topic VARCHAR(100) NOT NULL,
    message_key VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    sent_at DATETIME NULL,
    INDEX ix_match_outbox_status_id (status, id)
);
