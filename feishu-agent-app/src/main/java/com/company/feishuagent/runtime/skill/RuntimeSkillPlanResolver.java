package com.company.feishuagent.runtime.skill;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RuntimeSkillPlanResolver {

    private static final String DEFAULT_SKILL = "default";

    private final SkillCatalogService skillCatalogService;

    public RuntimeSkillPlanResolver(SkillCatalogService skillCatalogService) {
        this.skillCatalogService = skillCatalogService;
    }

    public RuntimeSkillPlan resolve(String requestedSkill, Collection<String> allowedSkills) {
        Set<String> loadable = new LinkedHashSet<>();
        addIfAvailable(loadable, DEFAULT_SKILL);
        for (String skillName : skillCatalogService.listAvailableSkillNames()) {
            addIfAvailable(loadable, skillName);
        }
        String effectiveRequestedSkill = normalize(requestedSkill);
        if (!effectiveRequestedSkill.isBlank() && !loadable.contains(effectiveRequestedSkill)) {
            effectiveRequestedSkill = "";
        }
        return RuntimeSkillPlan.of(effectiveRequestedSkill, loadable);
    }

    private void addIfAvailable(Set<String> target, String skillName) {
        String normalized = normalize(skillName);
        if (!normalized.isBlank() && skillCatalogService.contains(normalized)) {
            target.add(normalized);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
