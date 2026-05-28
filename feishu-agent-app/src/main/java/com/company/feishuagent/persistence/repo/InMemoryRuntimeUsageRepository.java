package com.company.feishuagent.persistence.repo;

import com.company.feishuagent.persistence.entity.RuntimeCallUsageHourlyEntity;
import com.company.feishuagent.persistence.entity.RuntimeCallUsageLogEntity;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "feishu.runtime.usage.backend", havingValue = "memory")
public class InMemoryRuntimeUsageRepository implements RuntimeUsageRepository {

    private final List<RuntimeCallUsageLogEntity> logs = new CopyOnWriteArrayList<>();
    private final List<RuntimeCallUsageHourlyEntity> hourly = new CopyOnWriteArrayList<>();

    @Override
    public void saveCallLog(RuntimeCallUsageLogEntity entity) {
        logs.add(entity);
    }

    @Override
    public void upsertHourly(RuntimeCallUsageHourlyEntity entity) {
        hourly.add(entity);
    }

    List<RuntimeCallUsageLogEntity> logs() {
        return List.copyOf(logs);
    }

    List<RuntimeCallUsageHourlyEntity> hourly() {
        return List.copyOf(hourly);
    }
}
