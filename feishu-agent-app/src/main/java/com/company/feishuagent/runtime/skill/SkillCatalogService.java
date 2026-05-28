package com.company.feishuagent.runtime.skill;

import java.util.List;

public interface SkillCatalogService {

    boolean contains(String skillName);

    List<String> listAvailableSkillNames();

    List<SkillMetadata> listSkillMetadata();

    default List<String> listAlwaysActiveSkillNames() {
        return listSkillMetadata().stream()
                .filter(SkillMetadata::alwaysActive)
                .map(SkillMetadata::name)
                .sorted()
                .toList();
    }
}
