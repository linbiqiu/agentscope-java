package com.company.feishuagent.runtime.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RuntimeOpsPageController {

    @GetMapping("/admin/runtime")
    public String runtimeOpsPage() {
        return "forward:/admin/runtime/index.html";
    }
}
