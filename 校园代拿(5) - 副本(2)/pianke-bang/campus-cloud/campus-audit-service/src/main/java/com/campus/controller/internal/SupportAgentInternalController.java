package com.campus.controller.internal;

import com.campus.config.AgentGraphProperties;
import com.campus.security.AuthContext;
import com.campus.service.SupportAgentToolService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 供 LangGraph 回调的只读工具执行入口（本机 / 内网）。
 * 不经过用户 JWT；用内部令牌 + 显式 username 注入 AuthContext。
 */
@RestController
@RequestMapping("/internal/support")
public class SupportAgentInternalController {

    private final SupportAgentToolService supportAgentToolService;
    private final AgentGraphProperties graphProperties;

    public SupportAgentInternalController(
            SupportAgentToolService supportAgentToolService,
            AgentGraphProperties graphProperties
    ) {
        this.supportAgentToolService = supportAgentToolService;
        this.graphProperties = graphProperties;
    }

    /**
     * body: { username, userId?, normalized?, tools: [{ name, arguments? }] }
     */
    @PostMapping("/run-tools")
    public Map<String, Object> runTools(
            @RequestHeader(value = "X-Campus-Agent-Token", required = false) String token,
            @RequestBody Map<String, Object> body
    ) {
        assertToken(token);
        if (body == null) {
            throw new IllegalArgumentException("body 不能为空");
        }
        String username = stringVal(body.get("username"));
        if (username.isBlank()) {
            throw new IllegalArgumentException("username 不能为空");
        }
        Long userId = longVal(body.get("userId"));
        String normalized = stringVal(body.get("normalized"));
        Object toolsRaw = body.get("tools");
        if (!(toolsRaw instanceof List<?> toolList) || toolList.isEmpty()) {
            throw new IllegalArgumentException("tools 不能为空");
        }

        AuthContext.setCurrentUser(username.trim());
        if (userId != null) {
            AuthContext.setCurrentUserId(userId);
        }
        try {
            List<Map<String, Object>> results = new ArrayList<>();
            int n = 0;
            for (Object item : toolList) {
                if (n >= 5) {
                    break;
                }
                if (!(item instanceof Map<?, ?> row)) {
                    continue;
                }
                String name = stringVal(row.get("name"));
                if (name.isBlank()) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> args = row.get("arguments") instanceof Map<?, ?> m
                        ? (Map<String, Object>) m
                        : Map.of();
                results.add(supportAgentToolService.runNamed(name, args, normalized));
                n++;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("results", results);
            return out;
        } finally {
            AuthContext.clear();
        }
    }

    private void assertToken(String token) {
        String expected = graphProperties.getInternalToken();
        if (expected == null || expected.isBlank()) {
            return;
        }
        if (token == null || !expected.trim().equals(token.trim())) {
            throw new IllegalStateException("无权调用内部工具接口");
        }
    }

    private static String stringVal(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static Long longVal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            String s = String.valueOf(v).trim();
            if (s.isBlank()) {
                return null;
            }
            return Long.parseLong(s.replaceAll("\\.0$", ""));
        } catch (Exception ex) {
            return null;
        }
    }
}
