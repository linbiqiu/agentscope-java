package com.company.feishuagent.feishu.service;

import com.company.feishuagent.persistence.repo.FeishuEventDedupRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

@Service
public class FeishuEventIdempotencyService {

    private final FeishuEventDedupRepository feishuEventDedupRepository;

    public FeishuEventIdempotencyService(FeishuEventDedupRepository feishuEventDedupRepository) {
        this.feishuEventDedupRepository = feishuEventDedupRepository;
    }

    public boolean markIfFirst(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return true;
        }
        return feishuEventDedupRepository.markIfFirst(eventId, OffsetDateTime.now(ZoneOffset.UTC));
    }
}
