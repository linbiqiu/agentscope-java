package com.company.feishuagent.runtime.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.company.feishuagent.runtime.skill.SkillCatalogService;
import com.company.feishuagent.runtime.skill.SkillMetadata;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultSkillAuthorizationServiceTest {

    private final SkillCatalogService catalogService = new SkillCatalogService() {
        @Override
        public boolean contains(String skillName) {
            return listAvailableSkillNames().contains(skillName);
        }

        @Override
        public List<String> listAvailableSkillNames() {
            return List.of("default", "knowledge_search", "feishu-channel-rules");
        }

        @Override
        public List<SkillMetadata> listSkillMetadata() {
            return List.of(
                    new SkillMetadata("default", "Default", false),
                    new SkillMetadata("knowledge_search", "Knowledge search", false),
                    new SkillMetadata("feishu-channel-rules", "Feishu rules", true));
        }
    };

    @Test
    void allowsAllCatalogSkills() {
        DefaultSkillAuthorizationService authorizationService =
                new DefaultSkillAuthorizationService(catalogService);

        assertTrue(authorizationService.isAllowed(7L, "u_1", "knowledge_search"));
        assertTrue(authorizationService.isAllowed(7L, "u_1", "feishu-channel-rules"));
        assertTrue(authorizationService.isAllowed(7L, "u_1", "default"));
        assertFalse(authorizationService.isAllowed(7L, "u_1", "not_exists"));
        assertEquals(
                List.of("default", "feishu-channel-rules", "knowledge_search"),
                authorizationService.listAllowedSkills(7L, "u_1"));
    }
}
