package com.company.feishuagent.persistence.repo;

public interface FeishuRateLimitRepository {

    int incrementAndGet(String userOpenId, long minuteBucket);
}
