package com.campus.controller;

import com.campus.dto.AdminTaskStatsResponse;
import com.campus.elasticsearch.TaskElasticsearchService;
import com.campus.security.AdminPolicy;
import com.campus.security.AuthContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tasks")
public class AdminTaskStatsController {

    private final AdminPolicy adminPolicy;
    private final TaskElasticsearchService taskElasticsearchService;

    public AdminTaskStatsController(
            AdminPolicy adminPolicy,
            @Autowired(required = false) TaskElasticsearchService taskElasticsearchService
    ) {
        this.adminPolicy = adminPolicy;
        this.taskElasticsearchService = taskElasticsearchService;
    }

    @GetMapping("/stats")
    public AdminTaskStatsResponse stats(@RequestParam(value = "days", defaultValue = "7") int days) {
        assertAdmin();
        if (taskElasticsearchService == null) {
            throw new IllegalStateException("Elasticsearch 未启用，无法加载任务统计");
        }
        AdminTaskStatsResponse response = taskElasticsearchService.buildAdminStats(days);
        if (response == null) {
            throw new IllegalStateException("任务统计加载失败，请确认 Elasticsearch 已启动");
        }
        return response;
    }

    private void assertAdmin() {
        String operator = AuthContext.getCurrentUser();
        if (operator == null || !adminPolicy.isAdmin(operator)) {
            throw new IllegalStateException("仅管理员可执行该操作");
        }
    }
}
