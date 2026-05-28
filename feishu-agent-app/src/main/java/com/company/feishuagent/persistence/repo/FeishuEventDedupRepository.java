package com.company.feishuagent.persistence.repo;

import java.time.OffsetDateTime;

public interface FeishuEventDedupRepository {

    boolean markIfFirst(String eventId, OffsetDateTime createdAt);
}
