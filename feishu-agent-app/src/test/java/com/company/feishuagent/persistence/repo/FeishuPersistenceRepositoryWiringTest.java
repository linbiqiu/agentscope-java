package com.company.feishuagent.persistence.repo;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

class FeishuPersistenceRepositoryWiringTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(WiringConfig.class)
                    .withPropertyValues("feishu.rate-limit.backend=jdbc");

    @Test
    void createsJdbcRepositoriesWhenJdbcTemplateExists() {
        contextRunner.run(
                context -> {
                    assertNotNull(context.getBean(JdbcFeishuEventDedupRepository.class));
                    assertNotNull(context.getBean(JdbcFeishuRateLimitRepository.class));
                });
    }

    @Configuration
    static class WiringConfig {

        @Bean
        DataSource dataSource() {
            return new DataSource() {
                @Override
                public java.sql.Connection getConnection() {
                    return null;
                }

                @Override
                public java.sql.Connection getConnection(String username, String password) {
                    return null;
                }

                @Override
                public <T> T unwrap(Class<T> iface) {
                    return null;
                }

                @Override
                public boolean isWrapperFor(Class<?> iface) {
                    return false;
                }

                @Override
                public java.io.PrintWriter getLogWriter() {
                    return null;
                }

                @Override
                public void setLogWriter(java.io.PrintWriter out) {
                }

                @Override
                public void setLoginTimeout(int seconds) {
                }

                @Override
                public int getLoginTimeout() {
                    return 0;
                }

                @Override
                public java.util.logging.Logger getParentLogger() {
                    return java.util.logging.Logger.getGlobal();
                }
            };
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        JdbcFeishuEventDedupRepository jdbcFeishuEventDedupRepository(JdbcTemplate jdbcTemplate) {
            return new JdbcFeishuEventDedupRepository(jdbcTemplate);
        }

        @Bean
        JdbcFeishuRateLimitRepository jdbcFeishuRateLimitRepository(JdbcTemplate jdbcTemplate) {
            return new JdbcFeishuRateLimitRepository(jdbcTemplate);
        }
    }
}
