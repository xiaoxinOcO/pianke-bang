package com.campus.controller;

import com.campus.dto.AdminAccountRoleRequest;
import com.campus.dto.AdminCreditScoreRequest;
import com.campus.dto.CreateCustomerServiceRequest;
import com.campus.dto.AdminUserDTO;
import com.campus.security.AuthContext;
import com.campus.service.AdminUserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/users")
    public List<AdminUserDTO> listUsers() {
        return adminUserService.listUsers(AuthContext.getCurrentUser());
    }

    @PostMapping("/users/{id}/enable")
    public AdminUserDTO enableUser(@PathVariable("id") Long id) {
        return adminUserService.setUserEnabled(id, true, AuthContext.getCurrentUser());
    }

    @PostMapping("/users/{id}/disable")
    public AdminUserDTO disableUser(@PathVariable("id") Long id) {
        return adminUserService.setUserEnabled(id, false, AuthContext.getCurrentUser());
    }

    @PostMapping("/users/{id}/avatar/approve")
    public AdminUserDTO approveAvatar(@PathVariable("id") Long id) {
        return adminUserService.approveAvatar(id, AuthContext.getCurrentUser());
    }

    @PostMapping("/users/{id}/avatar/reject")
    public AdminUserDTO rejectAvatar(@PathVariable("id") Long id) {
        return adminUserService.rejectAvatar(id, AuthContext.getCurrentUser());
    }

    @PostMapping("/users/{id}/rider/approve")
    public AdminUserDTO approveRider(@PathVariable("id") Long id) {
        return adminUserService.approveRider(id, AuthContext.getCurrentUser());
    }

    @PostMapping("/users/{id}/rider/reject")
    public AdminUserDTO rejectRider(@PathVariable("id") Long id) {
        return adminUserService.rejectRider(id, AuthContext.getCurrentUser());
    }

    @PostMapping("/users/{id}/rider/revoke")
    public AdminUserDTO revokeRiderCertification(@PathVariable("id") Long id) {
        return adminUserService.revokeRiderCertification(id, AuthContext.getCurrentUser());
    }

    @PostMapping("/users/{id}/credit-score")
    public AdminUserDTO updateUserCreditScore(@PathVariable("id") Long id, @RequestBody AdminCreditScoreRequest request) {
        return adminUserService.updateUserCreditScore(id, request, AuthContext.getCurrentUser());
    }

    @GetMapping("/users/summary")
    public Map<String, Long> userSummary() {
        List<AdminUserDTO> users = adminUserService.listUsers(AuthContext.getCurrentUser());
        long enabled = users.stream().filter(AdminUserDTO::isEnabled).count();
        long disabled = users.size() - enabled;
        return Map.of(
                "total", (long) users.size(),
                "enabled", enabled,
                "disabled", disabled
        );
    }

    @GetMapping("/customer-service")
    public List<AdminUserDTO> listCustomerServiceStaff() {
        return adminUserService.listCustomerServiceStaff(AuthContext.getCurrentUser());
    }

    @PostMapping("/customer-service")
    public AdminUserDTO createCustomerServiceStaff(@RequestBody CreateCustomerServiceRequest request) {
        return adminUserService.createCustomerServiceStaff(request, AuthContext.getCurrentUser());
    }

    @PostMapping("/users/{id}/account-role")
    public AdminUserDTO setAccountRole(@PathVariable("id") Long id, @RequestBody AdminAccountRoleRequest request) {
        return adminUserService.setAccountRole(id, request, AuthContext.getCurrentUser());
    }
}
