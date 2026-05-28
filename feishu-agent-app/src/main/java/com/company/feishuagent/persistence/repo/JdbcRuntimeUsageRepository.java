package com.company.feishuagent.persistence.repo;

import com.company.feishuagent.persistence.entity.RuntimeCallUsageHourlyEntity;
import com.company.feishuagent.persistence.entity.RuntimeCallUsageLogEntity;
import java.sql.Timestamp;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "feishu.runtime.usage.backend", havingValue = "jdbc", matchIfMissing = true)
public class JdbcRuntimeUsageRepository implements RuntimeUsageRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRuntimeUsageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveCallLog(RuntimeCallUsageLogEntity entity) {
        jdbcTemplate.update(
                """
                insert into runtime_call_usage_log(
                    trace_id, agent_id, user_id, conversation_id, chat_type,
                    actor_open_id, actor_mobile, actor_employee_no, actor_name,
                    provider, model_name, org_key, session_key,
                    requested_skill, resolved_skill, call_status, error_message,
                    prompt_tokens, completion_tokens, total_tokens,
                    cache_creation_input_tokens, cache_read_input_tokens,
                    called_at, created_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                entity.traceId(),
                entity.agentId(),
                entity.userId(),
                entity.conversationId(),
                entity.chatType(),
                entity.actorOpenId(),
                entity.actorMobile(),
                entity.actorEmployeeNo(),
                entity.actorName(),
                entity.provider(),
                entity.modelName(),
                entity.orgKey(),
                entity.sessionKey(),
                entity.requestedSkill(),
                entity.resolvedSkill(),
                entity.callStatus(),
                entity.errorMessage(),
                entity.promptTokens(),
                entity.completionTokens(),
                entity.totalTokens(),
                entity.cacheCreationInputTokens(),
                entity.cacheReadInputTokens(),
                Timestamp.from(entity.calledAt().toInstant()),
                Timestamp.from(entity.createdAt().toInstant()));
    }

    @Override
    public void upsertHourly(RuntimeCallUsageHourlyEntity entity) {
        jdbcTemplate.update(
                """
                insert into runtime_call_usage_hourly(
                    hour_bucket, agent_id, provider, model_name, org_key,
                    request_count, success_count, failure_count,
                    prompt_tokens, completion_tokens, total_tokens,
                    cache_creation_input_tokens, cache_read_input_tokens, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (hour_bucket, agent_id, provider, model_name, org_key)
                do update set
                    request_count = runtime_call_usage_hourly.request_count + excluded.request_count,
                    success_count = runtime_call_usage_hourly.success_count + excluded.success_count,
                    failure_count = runtime_call_usage_hourly.failure_count + excluded.failure_count,
                    prompt_tokens = runtime_call_usage_hourly.prompt_tokens + excluded.prompt_tokens,
                    completion_tokens = runtime_call_usage_hourly.completion_tokens + excluded.completion_tokens,
                    total_tokens = runtime_call_usage_hourly.total_tokens + excluded.total_tokens,
                    cache_creation_input_tokens = runtime_call_usage_hourly.cache_creation_input_tokens + excluded.cache_creation_input_tokens,
                    cache_read_input_tokens = runtime_call_usage_hourly.cache_read_input_tokens + excluded.cache_read_input_tokens,
                    updated_at = excluded.updated_at
                """,
                Timestamp.from(entity.hourBucket().toInstant()),
                entity.agentId(),
                entity.provider(),
                entity.modelName(),
                entity.orgKey(),
                entity.requestCount(),
                entity.successCount(),
                entity.failureCount(),
                entity.promptTokens(),
                entity.completionTokens(),
                entity.totalTokens(),
                entity.cacheCreationInputTokens(),
                entity.cacheReadInputTokens(),
                Timestamp.from(entity.updatedAt().toInstant()));
    }
}
