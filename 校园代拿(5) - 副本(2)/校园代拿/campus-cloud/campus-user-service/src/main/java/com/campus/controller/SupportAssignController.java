package com.campus.controller;

import com.campus.security.AuthContext;
import com.campus.service.CustomerServiceStaffService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/support")
public class SupportAssignController {

    private final CustomerServiceStaffService customerServiceStaffService;

    public SupportAssignController(CustomerServiceStaffService customerServiceStaffService) {
        this.customerServiceStaffService = customerServiceStaffService;
    }

    @GetMapping("/assign-agent")
    public Map<String, Object> assignAgent() {
        return customerServiceStaffService.assignAgentForUser(AuthContext.getCurrentUser());
    }

    @GetMapping("/agent-availability")
    public Map<String, Object> agentAvailability() {
        return customerServiceStaffService.getAgentAvailability();
    }
}
