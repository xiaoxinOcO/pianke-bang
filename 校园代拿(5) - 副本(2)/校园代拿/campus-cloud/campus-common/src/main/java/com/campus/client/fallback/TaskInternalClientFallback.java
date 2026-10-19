package com.campus.client.fallback;

import com.campus.client.TaskInternalClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TaskInternalClientFallback implements TaskInternalClient {

    private static final Logger log = LoggerFactory.getLogger(TaskInternalClientFallback.class);

    @Override
    public Map<String, Object> getAlipayBountyInfo(Long id, String username) {
        log.warn("task alipay-bounty fallback, id={}", id);
        throw new IllegalStateException("任务服务暂时不可用");
    }

    @Override
    public Map<String, Object> prepareAlipayBounty(Long id, Map<String, Object> body) {
        log.warn("task prepare-alipay-bounty fallback, id={}", id);
        throw new IllegalStateException("任务服务暂时不可用");
    }

    @Override
    public void confirmAlipayBounty(Long id, Map<String, Object> body) {
        log.warn("task confirm-alipay-bounty fallback, id={}", id);
        throw new IllegalStateException("任务服务暂时不可用");
    }

    @Override
    public Map<String, Object> agentSummary(String username, Long userId, String scope, Integer limit) {
        log.warn("task agent-summary fallback, user={}, scope={}", username, scope);
        return Map.of(
                "error", "任务服务暂时不可用，请稍后再试",
                "total", 0,
                "matched", 0,
                "shown", 0,
                "scope", scope == null ? "active" : scope,
                "tasks", List.of()
        );
    }

    @Override
    public Map<String, Object> agentDetail(String username, Long taskId) {
        log.warn("task agent-detail fallback, user={}, taskId={}", username, taskId);
        return Map.of("error", "任务服务暂时不可用，请稍后再试", "code", "UNAVAILABLE");
    }

    @Override
    public Map<String, Object> agentTimeline(String username, Long taskId) {
        log.warn("task agent-timeline fallback, user={}, taskId={}", username, taskId);
        return Map.of("error", "任务服务暂时不可用，请稍后再试", "code", "UNAVAILABLE");
    }

    @Override
    public Map<String, Object> agentAppeals(String username, Long userId, Integer limit) {
        log.warn("task agent-appeals fallback, user={}", username);
        return Map.of(
                "error", "任务服务暂时不可用，请稍后再试",
                "matched", 0,
                "shown", 0,
                "items", List.of()
        );
    }

    @Override
    public Map<String, Object> agentUrgentQuota(String username, Long userId) {
        log.warn("task agent-urgent-quota fallback, user={}", username);
        return Map.of("error", "任务服务暂时不可用，请稍后再试", "code", "UNAVAILABLE");
    }
}
