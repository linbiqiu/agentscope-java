package com.company.feishuagent.persistence.repo;

import com.company.feishuagent.persistence.entity.RuntimeCallUsageHourlyEntity;
import com.company.feishuagent.persistence.entity.RuntimeCallUsageLogEntity;

public interface RuntimeUsageRepository {

    void saveCallLog(RuntimeCallUsageLogEntity entity);

    void upsertHourly(RuntimeCallUsageHourlyEntity entity);
}
