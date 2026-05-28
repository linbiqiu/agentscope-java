package com.company.feishuagent.runtime.skill;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class FileSystemSkillCatalogService implements SkillCatalogService {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemSkillCatalogService.class);

    private final Path repositoryDir;
    private final AtomicReference<Map<String, SkillMetadata>> cachedSkills;

    public FileSystemSkillCatalogService(@Value("${feishu.runtime.skill-repo.dir:}") String repositoryDir) {
        this.repositoryDir = repositoryDir == null || repositoryDir.isBlank() ? null : Path.of(repositoryDir.trim());
        this.cachedSkills = new AtomicReference<>(Map.of("default", new SkillMetadata("default", "Default skill", false)));
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${feishu.runtime.skill-repo.refresh-interval-ms:5000}")
    public void refresh() {
        if (repositoryDir == null || !Files.exists(repositoryDir) || !Files.isDirectory(repositoryDir)) {
            cachedSkills.set(Map.of("default", new SkillMetadata("default", "Default skill", false)));
            return;
        }
        try (Stream<Path> stream = Files.list(repositoryDir)) {
            Map<String, SkillMetadata> skills = stream
                    .filter(Files::isDirectory)
                    .filter(item -> Files.exists(item.resolve("SKILL.md")))
                    .map(this::loadMetadata)
                    .filter(metadata -> metadata.name() != null && !metadata.name().isBlank())
                    .collect(Collectors.toMap(SkillMetadata::name, metadata -> metadata, (left, right) -> left));
            if (!skills.containsKey("default")) {
                skills = new java.util.HashMap<>(skills);
                skills.put("default", new SkillMetadata("default", "Default skill", false));
            }
            cachedSkills.set(Map.copyOf(skills));
            logger.info("skill_catalog_refreshed dir={} count={}", repositoryDir, skills.size());
        } catch (IOException ex) {
            logger.warn("skill_catalog_refresh_failed dir={} reason={}", repositoryDir, ex.getMessage());
        }
    }

    @Override
    public boolean contains(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return false;
        }
        return cachedSkills.get().containsKey(skillName.trim());
    }

    @Override
    public List<String> listAvailableSkillNames() {
        return cachedSkills.get().keySet().stream().sorted().toList();
    }

    @Override
    public List<SkillMetadata> listSkillMetadata() {
        return cachedSkills.get().values().stream()
                .sorted(java.util.Comparator.comparing(SkillMetadata::name))
                .toList();
    }

    private SkillMetadata loadMetadata(Path skillDir) {
        String fallbackName = skillDir.getFileName().toString();
        try {
            String content = Files.readString(skillDir.resolve("SKILL.md"));
            Map<String, String> frontMatter = parseFrontMatter(content);
            String name = firstNonBlank(frontMatter.get("name"), fallbackName);
            String description = firstNonBlank(frontMatter.get("description"), "");
            boolean alwaysActive = Boolean.parseBoolean(firstNonBlank(frontMatter.get("alwaysActive"), frontMatter.get("always_active"), "false"));
            return new SkillMetadata(name.trim(), description.trim(), alwaysActive);
        } catch (IOException ex) {
            logger.warn("skill_metadata_load_failed path={} reason={}", skillDir, ex.getMessage());
            return new SkillMetadata(fallbackName, "", false);
        }
    }

    private Map<String, String> parseFrontMatter(String content) {
        if (content == null || !content.startsWith("---")) {
            return Map.of();
        }
        int end = content.indexOf("\n---", 3);
        if (end < 0) {
            return Map.of();
        }
        String raw = content.substring(3, end);
        Map<String, String> result = new java.util.HashMap<>();
        List<String> lines = new ArrayList<>(List.of(raw.split("\\R")));
        Set<String> supportedKeys = new HashSet<>(List.of("name", "description", "alwaysactive", "always_active"));
        for (String line : lines) {
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).trim();
            String normalizedKey = key.toLowerCase(Locale.ROOT);
            if (!supportedKeys.contains(normalizedKey)) {
                continue;
            }
            String value = line.substring(separator + 1).trim();
            result.put(key, unquote(value));
        }
        return Map.copyOf(result);
    }

    private String unquote(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
