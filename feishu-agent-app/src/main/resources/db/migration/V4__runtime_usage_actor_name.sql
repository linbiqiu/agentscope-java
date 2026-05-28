ALTER TABLE runtime_call_usage_log
    ADD COLUMN IF NOT EXISTS actor_name VARCHAR(128);
