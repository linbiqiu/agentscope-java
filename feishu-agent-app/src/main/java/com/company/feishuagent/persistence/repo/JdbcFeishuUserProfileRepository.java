package com.company.feishuagent.persistence.repo;

import com.company.feishuagent.persistence.entity.FeishuUserProfileEntity;
import java.sql.Timestamp;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "feishu.user-profile.backend", havingValue = "jdbc", matchIfMissing = true)
public class JdbcFeishuUserProfileRepository implements FeishuUserProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcFeishuUserProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public FeishuUserProfileEntity findByOpenId(String openId) {
        if (openId == null || openId.isBlank()) {
            return null;
        }
        return findOneBy("actor_open_id", openId);
    }

    @Override
    public FeishuUserProfileEntity findByUnionId(String unionId) {
        if (unionId == null || unionId.isBlank()) {
            return null;
        }
        return findOneBy("actor_union_id", unionId);
    }

    @Override
    public FeishuUserProfileEntity findByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return findOneBy("actor_user_id", userId);
    }

    @Override
    public void upsert(FeishuUserProfileEntity entity) {
        jdbcTemplate.update(
                """
                insert into feishu_user_profile(
                    actor_open_id, actor_union_id, actor_user_id,
                    actor_name, actor_mobile, actor_employee_no, actor_email,
                    last_seen_at, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (actor_open_id)
                do update set
                    actor_union_id = coalesce(excluded.actor_union_id, feishu_user_profile.actor_union_id),
                    actor_user_id = coalesce(excluded.actor_user_id, feishu_user_profile.actor_user_id),
                    actor_name = coalesce(excluded.actor_name, feishu_user_profile.actor_name),
                    actor_mobile = coalesce(excluded.actor_mobile, feishu_user_profile.actor_mobile),
                    actor_employee_no = coalesce(excluded.actor_employee_no, feishu_user_profile.actor_employee_no),
                    actor_email = coalesce(excluded.actor_email, feishu_user_profile.actor_email),
                    last_seen_at = excluded.last_seen_at,
                    updated_at = excluded.updated_at
                """,
                entity.actorOpenId(),
                entity.actorUnionId(),
                entity.actorUserId(),
                entity.actorName(),
                entity.actorMobile(),
                entity.actorEmployeeNo(),
                entity.actorEmail(),
                Timestamp.from(entity.lastSeenAt().toInstant()),
                Timestamp.from(entity.createdAt().toInstant()),
                Timestamp.from(entity.updatedAt().toInstant()));
    }

    private FeishuUserProfileEntity findOneBy(String field, String value) {
        return jdbcTemplate.query(
                        """
                        select actor_open_id, actor_union_id, actor_user_id,
                               actor_name, actor_mobile, actor_employee_no, actor_email,
                               last_seen_at, created_at, updated_at
                          from feishu_user_profile
                         where %s = ?
                        """.formatted(field),
                        rs -> {
                            if (!rs.next()) {
                                return null;
                            }
                            Timestamp lastSeenAt = rs.getTimestamp("last_seen_at");
                            Timestamp createdAt = rs.getTimestamp("created_at");
                            Timestamp updatedAt = rs.getTimestamp("updated_at");
                            return new FeishuUserProfileEntity(
                                    rs.getString("actor_open_id"),
                                    rs.getString("actor_union_id"),
                                    rs.getString("actor_user_id"),
                                    rs.getString("actor_name"),
                                    rs.getString("actor_mobile"),
                                    rs.getString("actor_employee_no"),
                                    rs.getString("actor_email"),
                                    lastSeenAt == null ? null : lastSeenAt.toInstant().atOffset(java.time.ZoneOffset.UTC),
                                    createdAt == null ? null : createdAt.toInstant().atOffset(java.time.ZoneOffset.UTC),
                                    updatedAt == null ? null : updatedAt.toInstant().atOffset(java.time.ZoneOffset.UTC));
                        },
                        value);
    }
}
