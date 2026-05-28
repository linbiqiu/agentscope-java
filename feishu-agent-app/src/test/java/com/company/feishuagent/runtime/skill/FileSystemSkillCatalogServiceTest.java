package com.company.feishuagent.runtime.skill;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FileSystemSkillCatalogServiceTest {

    @Test
    void refreshLoadsAndUpdatesSkillsFromRepository() throws Exception {
        Path repo = Files.createTempDirectory("skill-repo-");
        Path skillA = repo.resolve("knowledge_search");
        Files.createDirectories(skillA);
        Files.writeString(skillA.resolve("SKILL.md"), "name: knowledge_search");

        FileSystemSkillCatalogService service = new FileSystemSkillCatalogService(repo.toString());
        service.refresh();

        assertTrue(service.contains("knowledge_search"));

        Files.deleteIfExists(skillA.resolve("SKILL.md"));
        Files.deleteIfExists(skillA);
        service.refresh();

        assertFalse(service.contains("knowledge_search"));
        assertTrue(service.contains("default"));
    }
}
