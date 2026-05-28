package com.company.feishuagent.runtime.auth;

import com.company.feishuagent.runtime.skill.SkillCatalogService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DefaultSkillAuthorizationService implements SkillAuthorizationService {

    private final SkillCatalogService skillCatalogService;

    public DefaultSkillAuthorizationService(SkillCatalogService skillCatalogService) {
        this.skillCatalogService = skillCatalogService;
    }

    @Override
    public boolean isAllowed(Long agentId, String userId, String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return false;
        }
        return skillCatalogService.contains(skillName.trim());
    }

    @Override
    public List<String> listAllowedSkills(Long agentId, String userId) {
        List<String> all = skillCatalogService.listAvailableSkillNames();
        return all.stream().sorted().toList();
    }
}
