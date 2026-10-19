package com.campus.client;

import com.campus.client.fallback.TaskInternalClientFallback;
import com.campus.config.ServiceConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(
        name = ServiceConstants.TASK_SERVICE,
        contextId = "taskInternalClient",
        fallback = TaskInternalClientFallback.class
)
public interface TaskInternalClient {

    @GetMapping("/internal/tasks/{id}/alipay-bounty")
    Map<String, Object> getAlipayBountyInfo(
            @PathVariable("id") Long id,
            @RequestParam("username") String username
    );

    @PostMapping("/internal/tasks/{id}/prepare-alipay-bounty")
    Map<String, Object> prepareAlipayBounty(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @PostMapping("/internal/tasks/{id}/confirm-alipay-bounty")
    void confirmAlipayBounty(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @GetMapping("/internal/tasks/agent-summary")
    Map<String, Object> agentSummary(
            @RequestParam("username") String username,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "scope", required = false, defaultValue = "active") String scope,
            @RequestParam(value = "limit", required = false) Integer limit
    );

    @GetMapping("/internal/tasks/agent-detail")
    Map<String, Object> agentDetail(
            @RequestParam("username") String username,
            @RequestParam("taskId") Long taskId
    );

    @GetMapping("/internal/tasks/agent-timeline")
    Map<String, Object> agentTimeline(
            @RequestParam("username") String username,
            @RequestParam("taskId") Long taskId
    );

    @GetMapping("/internal/tasks/agent-appeals")
    Map<String, Object> agentAppeals(
            @RequestParam("username") String username,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "limit", required = false) Integer limit
    );

    @GetMapping("/internal/tasks/agent-urgent-quota")
    Map<String, Object> agentUrgentQuota(
            @RequestParam("username") String username,
            @RequestParam(value = "userId", required = false) Long userId
    );
}
