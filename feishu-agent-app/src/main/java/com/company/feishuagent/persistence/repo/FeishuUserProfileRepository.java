package com.company.feishuagent.persistence.repo;

import com.company.feishuagent.persistence.entity.FeishuUserProfileEntity;

public interface FeishuUserProfileRepository {

    FeishuUserProfileEntity findByOpenId(String openId);

    FeishuUserProfileEntity findByUnionId(String unionId);

    FeishuUserProfileEntity findByUserId(String userId);

    void upsert(FeishuUserProfileEntity entity);
}
