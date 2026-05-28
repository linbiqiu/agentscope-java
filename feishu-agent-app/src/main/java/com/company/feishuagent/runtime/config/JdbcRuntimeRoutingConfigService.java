package com.company.feishuagent.runtime.config;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "feishu.runtime.routing.backend", havingValue = "jdbc", matchIfMissing = true)
public class JdbcRuntimeRoutingConfigService implements RuntimeRoutingConfigService {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRuntimeRoutingConfigService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RuntimeRoutingConfig get(Long agentId) {
        return jdbcTemplate
                .query(
                        """
                        select agent_id, provider, model, api_key, base_url, fallback_model,
                               dispatch_mode, manual_skill, temperature, max_tokens, updated_by
                        from runtime_routing_config where agent_id = ?
                        """,
                        ps -> ps.setLong(1, agentId),
                        rs -> rs.next() ? mapRow(rs) : null)
                ;
    }

    @Override
    public RuntimeRoutingConfig save(RuntimeRoutingConfig config) {
        jdbcTemplate.update(
                """
                insert into runtime_routing_config(agent_id, provider, model, api_key, base_url, fallback_model,
                                                   dispatch_mode, manual_skill, temperature, max_tokens, updated_by, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                on conflict (agent_id)
                do update set provider = excluded.provider,
                              model = excluded.model,
                              api_key = excluded.api_key,
                              base_url = excluded.base_url,
                              fallback_model = excluded.fallback_model,
                              dispatch_mode = excluded.dispatch_mode,
                              manual_skill = excluded.manual_skill,
                              temperature = excluded.temperature,
                              max_tokens = excluded.max_tokens,
                              updated_by = excluded.updated_by,
                              updated_at = now()
                """,
                config.agentId(),
                config.provider(),
                config.model(),
                config.apiKey(),
                config.baseUrl(),
                config.fallbackModel(),
                config.dispatchMode(),
                config.manualSkill(),
                config.temperature(),
                config.maxTokens(),
                config.updatedBy());
        return config;
    }

    private RuntimeRoutingConfig mapRow(ResultSet rs) throws SQLException {
        return new RuntimeRoutingConfig(
                rs.getLong("agent_id"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("api_key"),
                rs.getString("base_url"),
                rs.getString("fallback_model"),
                rs.getString("dispatch_mode"),
                rs.getString("manual_skill"),
                rs.getDouble("temperature"),
                rs.getInt("max_tokens"),
                rs.getString("updated_by"));
    }
}
