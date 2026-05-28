package com.company.feishuagent.feishu.model;

public record EnterpriseIdentity(
        String openId,
        String unionId,
        String userId,
        String name,
        String mobile,
        String employeeNo,
        String email,
        String departmentId) {

    public boolean hasMobile() {
        return mobile != null && !mobile.isBlank();
    }

    public boolean hasEmployeeNo() {
        return employeeNo != null && !employeeNo.isBlank();
    }

    public boolean isComplete() {
        return openId != null && !openId.isBlank()
                && mobile != null && !mobile.isBlank()
                && employeeNo != null && !employeeNo.isBlank();
    }
}
