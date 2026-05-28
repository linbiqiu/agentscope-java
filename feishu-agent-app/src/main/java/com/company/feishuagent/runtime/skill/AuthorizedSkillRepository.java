package com.company.feishuagent.runtime.skill;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class AuthorizedSkillRepository implements AgentSkillRepository {

    private final AgentSkillRepository delegate;
    private final Set<String> allowedSkillNames;

    public AuthorizedSkillRepository(AgentSkillRepository delegate, List<String> allowedSkillNames) {
        this.delegate = delegate;
        this.allowedSkillNames = new TreeSet<>();
        if (allowedSkillNames != null) {
            for (String skillName : allowedSkillNames) {
                if (skillName != null && !skillName.isBlank()) {
                    this.allowedSkillNames.add(skillName.trim());
                }
            }
        }
    }

    @Override
    public AgentSkill getSkill(String name) {
        assertAllowed(name);
        return delegate.getSkill(name);
    }

    @Override
    public List<String> getAllSkillNames() {
        return delegate.getAllSkillNames().stream()
                .filter(this::isAllowed)
                .sorted()
                .toList();
    }

    @Override
    public List<AgentSkill> getAllSkills() {
        return allowedSkillNames.stream()
                .filter(delegate::skillExists)
                .map(delegate::getSkill)
                .toList();
    }

    @Override
    public boolean save(List<AgentSkill> skills, boolean force) {
        return delegate.save(skills, force);
    }

    @Override
    public boolean delete(String skillName) {
        assertAllowed(skillName);
        return delegate.delete(skillName);
    }

    @Override
    public boolean skillExists(String skillName) {
        return isAllowed(skillName) && delegate.skillExists(skillName);
    }

    @Override
    public AgentSkillRepositoryInfo getRepositoryInfo() {
        return delegate.getRepositoryInfo();
    }

    @Override
    public String getSource() {
        return delegate.getSource();
    }

    @Override
    public void setWriteable(boolean writeable) {
        delegate.setWriteable(writeable);
    }

    @Override
    public boolean isWriteable() {
        return delegate.isWriteable();
    }

    @Override
    public void close() {
        delegate.close();
    }

    private void assertAllowed(String skillName) {
        if (!isAllowed(skillName)) {
            throw new IllegalArgumentException("Skill is not authorized: " + skillName);
        }
    }

    private boolean isAllowed(String skillName) {
        return skillName != null && allowedSkillNames.contains(skillName.trim());
    }
}
