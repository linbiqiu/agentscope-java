package com.company.feishuagent.feishu.auth;

import java.util.Optional;

public interface FeishuUserTokenRepository {

    void save(String appId, String userOpenId, FeishuUserToken token);

    Optional<FeishuUserToken> findByAppIdAndUserOpenId(String appId, String userOpenId);

    void delete(String appId, String userOpenId);
}
