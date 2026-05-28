package com.company.feishuagent.feishu.service;

import com.company.feishuagent.feishu.model.EnterpriseIdentity;

public interface FeishuIdentityResolver {

    EnterpriseIdentity resolve(String openId);

    EnterpriseIdentity resolveByUserId(String userId);

    static FeishuIdentityResolver noOp() {
        return new FeishuIdentityResolver() {
            @Override
            public EnterpriseIdentity resolve(String openId) {
                return null;
            }

            @Override
            public EnterpriseIdentity resolveByUserId(String userId) {
                return null;
            }
        };
    }
}
