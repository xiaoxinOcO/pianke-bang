package com.campus.agent;

import com.campus.config.AgentGraphProperties;
import com.campus.dto.CustomerSupportChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调用 Python LangGraph 编排服务 POST /api/agent/chat。
 */
@Component
public class AgentGraphClient {
    private static final Logger log = LoggerFactory.getLogger(AgentGraphClient.class);

    private final AgentGraphProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public AgentGraphClient(AgentGraphProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * @return 成功时返回客服响应 Map；失败返回 null（由调用方回落）
     */
    public Map<String, Object> chat(
            String message,
            String username,
            Long userId,
            List<CustomerSupportChatMessage> history
    ) {
        if (!properties.isEnabled()) {
            return null;
        }
        String base = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().trim();
        if (base.isBlank() || message == null || message.isBlank()) {
            return null;
        }
        if (username == null || username.isBlank()) {
            return null;
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", message.trim());
            body.put("username", username.trim());
            if (userId != null) {
                body.put("userId", userId);
            }
            List<Map<String, String>> hist = new ArrayList<>();
            if (history != null) {
                int start = Math.max(0, history.size() - 8);
                for (int i = start; i < history.size(); i++) {
                    CustomerSupportChatMessage item = history.get(i);
                    if (item == null || item.getContent() == null || item.getContent().isBlank()) {
                        continue;
                    }
                    String role = item.getRole() == null ? "user" : item.getRole().trim().toLowerCase();
                    if (!"user".equals(role) && !"assistant".equals(role)) {
                        continue;
                    }
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("role", role);
                    row.put("content", item.getContent().trim());
                    hist.add(row);
                }
            }
            body.put("history", hist);

            String json = objectMapper.writeValueAsString(body);
            int timeoutMs = Math.max(5000, properties.getTimeoutMs());
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(base.replaceAll("/+$", "") + "/api/agent/chat"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            String token = properties.getInternalToken();
            if (token != null && !token.isBlank()) {
                builder.header("X-Campus-Agent-Token", token.trim());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("agent graph HTTP {}: {}", response.statusCode(), abbreviate(response.body()));
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            String reply = root.path("reply").asText("").trim();
            if (reply.isBlank()) {
                log.warn("agent graph empty reply");
                return null;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("model", root.path("model").asText("langgraph"));
            data.put("source", root.path("source").asText("langgraph"));
            data.put("reply", reply);
            if (root.has("actions") && root.get("actions").isArray()) {
                List<String> actions = new ArrayList<>();
                root.get("actions").forEach(n -> {
                    if (n != null && !n.asText("").isBlank()) {
                        actions.add(n.asText());
                    }
                });
                data.put("actions", actions);
            } else {
                data.put("actions", List.of());
            }
            if (root.has("citations") && root.get("citations").isArray()) {
                List<String> citations = new ArrayList<>();
                root.get("citations").forEach(n -> {
                    if (n != null && !n.asText("").isBlank()) {
                        citations.add(n.asText());
                    }
                });
                data.put("citations", citations);
            } else {
                data.put("citations", List.of());
            }
            if (root.has("toolPayload") && !root.get("toolPayload").isNull()) {
                data.put("toolPayload", objectMapper.convertValue(root.get("toolPayload"), Map.class));
            }
            if (root.has("trace") && !root.get("trace").isNull()) {
                data.put("trace", objectMapper.convertValue(root.get("trace"), Object.class));
            }
            return data;
        } catch (Exception ex) {
            log.warn("agent graph failed, fallback Java agent: {}", ex.getMessage());
            return null;
        }
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= 200 ? t : t.substring(0, 200) + "...";
    }
}
