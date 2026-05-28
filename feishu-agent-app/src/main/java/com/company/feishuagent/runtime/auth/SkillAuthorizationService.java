package com.company.feishuagent.runtime.auth;

import java.util.List;

public interface SkillAuthorizationService {

    boolean isAllowed(Long agentId, String userId, String skillName);

    List<String> listAllowedSkills(Long agentId, String userId);
}
