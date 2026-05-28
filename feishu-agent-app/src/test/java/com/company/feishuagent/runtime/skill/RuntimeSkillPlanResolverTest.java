package com.company.feishuagent.runtime.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeSkillPlanResolverTest {

    @Test
    void planIncludesAllCatalogSkills() throws Exception {
        Path repo = Files.createTempDirectory("skill-plan-repo-");
        writeSkill(repo, "default", "default", "Default", false);
        writeSkill(repo, "feishu-channel-rules", "feishu-channel-rules", "Feishu rules", true);
        writeSkill(repo, "analytics", "analytics", "Analytics", false);

        FileSystemSkillCatalogService catalogService = new FileSystemSkillCatalogService(repo.toString());
        catalogService.refresh();
        RuntimeSkillPlanResolver resolver = new RuntimeSkillPlanResolver(catalogService);

        RuntimeSkillPlan plan = resolver.resolve("analytics", List.of());

        assertEquals("analytics", plan.requestedSkill());
        assertEquals(List.of("analytics", "default", "feishu-channel-rules"), plan.loadableSkillNames());
        assertTrue(plan.isLoadable("analytics"));
        assertTrue(plan.isLoadable("feishu-channel-rules"));
        assertFalse(plan.isLoadable("not_exists"));
    }

    @Test
    void unknownRequestedSkillIsCleared() throws Exception {
        Path repo = Files.createTempDirectory("skill-plan-repo-");
        writeSkill(repo, "default", "default", "Default", false);
        writeSkill(repo, "analytics", "analytics", "Analytics", false);

        FileSystemSkillCatalogService catalogService = new FileSystemSkillCatalogService(repo.toString());
        catalogService.refresh();
        RuntimeSkillPlanResolver resolver = new RuntimeSkillPlanResolver(catalogService);

        RuntimeSkillPlan plan = resolver.resolve("not_exists", List.of());

        assertEquals("", plan.requestedSkill());
        assertEquals(List.of("analytics", "default"), plan.loadableSkillNames());
    }

    private void writeSkill(Path repo, String dirName, String name, String description, boolean alwaysActive) throws Exception {
        Path skillDir = repo.resolve(dirName);
        Files.createDirectories(skillDir);
        Files.writeString(
                skillDir.resolve("SKILL.md"),
                "---\n"
                        + "name: " + name + "\n"
                        + "description: " + description + "\n"
                        + "alwaysActive: " + alwaysActive + "\n"
                        + "---\n\n"
                        + "# " + name + "\n");
    }
}
