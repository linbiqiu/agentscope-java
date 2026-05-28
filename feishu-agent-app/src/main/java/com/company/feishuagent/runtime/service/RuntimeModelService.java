package com.company.feishuagent.runtime.service;

import com.company.feishuagent.feishu.model.EnterpriseIdentity;
import com.company.feishuagent.runtime.config.RuntimeRoutingConfig;
import com.company.feishuagent.runtime.skill.RuntimeSkillPlan;

public interface RuntimeModelService {

    RuntimeModelResult invoke(
            RuntimeRoutingConfig config,
            Long agentId,
            String userId,
            String sessionKey,
            String message,
            RuntimeSkillPlan skillPlan,
            String identityContextJson,
            EnterpriseIdentity enterpriseIdentity);
}
