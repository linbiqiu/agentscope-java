CREATE TABLE IF NOT EXISTS runtime_call_usage_log (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(128) NOT NULL,
    agent_id BIGINT NOT NULL,
    user_id VARCHAR(128),
    conversation_id VARCHAR(128),
    chat_type VARCHAR(32),
    actor_open_id VARCHAR(128),
    actor_mobile VARCHAR(64),
    actor_employee_no VARCHAR(64),
    provider VARCHAR(64),
    model_name VARCHAR(128),
    org_key VARCHAR(128),
    session_key VARCHAR(256),
    requested_skill VARCHAR(128),
    resolved_skill VARCHAR(128),
    call_status VARCHAR(32) NOT NULL,
    error_message VARCHAR(1024),
    prompt_tokens INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL DEFAULT 0,
    cache_creation_input_tokens INTEGER NOT NULL DEFAULT 0,
    cache_read_input_tokens INTEGER NOT NULL DEFAULT 0,
    called_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_runtime_call_usage_log_called_at
    ON runtime_call_usage_log (called_at);

CREATE INDEX IF NOT EXISTS idx_runtime_call_usage_log_org_hour
    ON runtime_call_usage_log (org_key, called_at);

CREATE INDEX IF NOT EXISTS idx_runtime_call_usage_log_actor
    ON runtime_call_usage_log (actor_open_id, called_at);

CREATE UNIQUE INDEX IF NOT EXISTS uk_runtime_call_usage_log_trace
    ON runtime_call_usage_log (trace_id);

CREATE TABLE IF NOT EXISTS runtime_call_usage_hourly (
    hour_bucket TIMESTAMP NOT NULL,
    agent_id BIGINT NOT NULL,
    provider VARCHAR(64),
    model_name VARCHAR(128),
    org_key VARCHAR(128),
    request_count BIGINT NOT NULL DEFAULT 0,
    success_count BIGINT NOT NULL DEFAULT 0,
    failure_count BIGINT NOT NULL DEFAULT 0,
    prompt_tokens BIGINT NOT NULL DEFAULT 0,
    completion_tokens BIGINT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    cache_creation_input_tokens BIGINT NOT NULL DEFAULT 0,
    cache_read_input_tokens BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (hour_bucket, agent_id, provider, model_name, org_key)
);

CREATE INDEX IF NOT EXISTS idx_runtime_call_usage_hourly_org_hour
    ON runtime_call_usage_hourly (org_key, hour_bucket);
