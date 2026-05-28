package com.company.feishuagent.admin.rbac;

import org.springframework.stereotype.Service;

@Service
public class DefaultRbacService implements RbacService {

    @Override
    public void assertOperator(String operatorId) {
        if (operatorId == null || operatorId.isBlank()) {
            throw new IllegalArgumentException("operatorId is required");
        }
    }
}
