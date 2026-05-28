package com.company.feishuagent.observability.service;

import com.company.feishuagent.observability.api.AuditRecordView;
import com.company.feishuagent.persistence.entity.SensitiveAccessAuditEntity;
import com.company.feishuagent.persistence.repo.SensitiveAccessAuditRepository;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DefaultAuditService implements AuditService {

    private final SensitiveAccessAuditRepository sensitiveAccessAuditRepository;

    public DefaultAuditService(SensitiveAccessAuditRepository sensitiveAccessAuditRepository) {
        this.sensitiveAccessAuditRepository = sensitiveAccessAuditRepository;
    }

    @Override
    public List<AuditRecordView> listSensitiveAccess(String traceId, String skillName) {
        List<SensitiveAccessAuditEntity> records =
                sensitiveAccessAuditRepository.findByTraceOrSkill(traceId, skillName);
        return toViews(records);
    }

    @Override
    public List<AuditRecordView> listSensitiveAccessByTraceIds(List<String> traceIds, String skillName) {
        List<SensitiveAccessAuditEntity> records =
                sensitiveAccessAuditRepository.findByTraceIdsOrSkill(traceIds, skillName);
        return toViews(records);
    }

    private List<AuditRecordView> toViews(List<SensitiveAccessAuditEntity> records) {
        return records.stream()
                .map(
                        item ->
                                new AuditRecordView(
                                        item.traceId(),
                                        item.conversationId(),
                                        item.skillName(),
                                        item.fieldName(),
                                        item.accessReason(),
                                        item.createdAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
                .toList();
    }
}
