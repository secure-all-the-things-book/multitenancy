package com.example.service;

import io.arconia.multitenancy.core.context.TenantContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@ResponseBody
class TenantController {

    @GetMapping("/")
    Map<String, String> me() {
        return Map.of("tenant", TenantContext.getTenantIdentifier(),
                "user", SecurityContextHolder.getContextHolderStrategy()
                        .getContext()
                        .getAuthentication()
                        .getName());
    }
}
