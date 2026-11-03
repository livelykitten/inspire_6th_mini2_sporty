-- W-02 match_db additions. Existing tables/data are not modified.
-- JPA ddl-auto=update creates these in normal application startup. For validate-only
-- environments, apply this once before starting the new match-service version.
CREATE TABLE IF NOT EXISTS match_user_guard (
    user_id BIGINT NOT NULL PRIMARY KEY,
    is_blocked BIT(1) NOT NULL DEFAULT 0
);

