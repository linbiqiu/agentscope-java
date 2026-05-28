package com.company.feishuagent.persistence.repo;

import com.company.feishuagent.persistence.entity.PublishRecordEntity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnMissingBean(PublishRecordRepository.class)
public class InMemoryPublishRecordRepository implements PublishRecordRepository {

    private final List<PublishRecordEntity> storage = new ArrayList<>();

    @Override
    public void save(PublishRecordEntity entity) {
        storage.add(entity);
    }

    @Override
    public List<PublishRecordEntity> findAll() {
        return List.copyOf(storage);
    }
}
