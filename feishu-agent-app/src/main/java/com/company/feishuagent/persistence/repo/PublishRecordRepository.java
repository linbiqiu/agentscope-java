package com.company.feishuagent.persistence.repo;

import com.company.feishuagent.persistence.entity.PublishRecordEntity;
import java.util.List;

public interface PublishRecordRepository {

    void save(PublishRecordEntity entity);

    List<PublishRecordEntity> findAll();
}
