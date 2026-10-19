package com.campus.controller;

import com.campus.dto.HallActivityUpsertRequest;
import com.campus.model.HallActivity;
import com.campus.security.AdminPolicy;
import com.campus.security.AuthContext;
import com.campus.service.HallActivityService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/hall-activities")
public class AdminHallActivityController {

    private final HallActivityService hallActivityService;
    private final AdminPolicy adminPolicy;

    public AdminHallActivityController(HallActivityService hallActivityService, AdminPolicy adminPolicy) {
        this.hallActivityService = hallActivityService;
        this.adminPolicy = adminPolicy;
    }

    @GetMapping
    public List<HallActivity> list() {
        requireAdmin();
        return hallActivityService.listAll();
    }

    @PostMapping
    public HallActivity create(@RequestBody HallActivityUpsertRequest request) {
        requireAdmin();
        return hallActivityService.create(request);
    }

    @PutMapping("/{id}")
    public HallActivity update(@PathVariable("id") Long id, @RequestBody HallActivityUpsertRequest request) {
        requireAdmin();
        return hallActivityService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public HallActivity updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        requireAdmin();
        String status = body == null ? null : body.get("status");
        return hallActivityService.updateStatus(id, status);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable("id") Long id) {
        requireAdmin();
        hallActivityService.delete(id);
        return Map.of("message", "已删除", "id", id);
    }

    private void requireAdmin() {
        String operator = AuthContext.getCurrentUser();
        if (operator == null || operator.isBlank() || !adminPolicy.isAdmin(operator)) {
            throw new IllegalStateException("无管理员权限");
        }
    }
}
