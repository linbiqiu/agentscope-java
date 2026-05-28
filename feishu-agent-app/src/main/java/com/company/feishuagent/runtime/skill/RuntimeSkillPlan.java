package com.company.feishuagent.runtime.skill;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public record RuntimeSkillPlan(String requestedSkill, List<String> loadableSkillNames) {

    public RuntimeSkillPlan {
        requestedSkill = requestedSkill == null ? "" : requestedSkill.trim();
        Set<String> normalized = new TreeSet<>();
        if (loadableSkillNames != null) {
            for (String skillName : loadableSkillNames) {
                if (skillName != null && !skillName.isBlank()) {
                    normalized.add(skillName.trim());
                }
            }
        }
        loadableSkillNames = List.copyOf(normalized);
    }

    public boolean isLoadable(String skillName) {
        return skillName != null && loadableSkillNames.contains(skillName.trim());
    }

    public static RuntimeSkillPlan of(String requestedSkill, Collection<String> loadableSkillNames) {
        return new RuntimeSkillPlan(
                requestedSkill,
                loadableSkillNames == null ? List.of() : loadableSkillNames.stream().toList());
    }
}
