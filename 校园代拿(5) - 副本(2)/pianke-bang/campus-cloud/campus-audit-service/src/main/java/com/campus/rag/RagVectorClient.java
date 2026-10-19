package com.campus.rag;

import com.campus.config.RagVectorProperties;
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
import java.util.List;
import java.util.Map;

/**
 * 调用 Python 向量检索服务 POST /api/retrieve。
 * 失败时返回空列表，由 {@link RagKnowledgeStore} 回退关键词检索。
 */
@Component
public class RagVectorClient {
    private static final Logger log = LoggerFactory.getLogger(RagVectorClient.class);

    private final RagVectorProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            // uvicorn/FastAPI 走 HTTP/1.1；默认 HTTP/2 会出现 upgrade/422
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public RagVectorClient(RagVectorProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public List<RagKnowledgeStore.ScoredChunk> retrieve(String query, int topK) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        String base = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().trim();
        if (base.isBlank()) {
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int k = Math.max(1, Math.min(topK <= 0 ? 3 : topK, 10));
        int timeoutMs = Math.max(500, properties.getTimeoutMs());

        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "query", query.trim(),
                    "top_k", k
            ));
            String url = base.replaceAll("/+$", "") + "/api/retrieve";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("vector retrieve HTTP {}: {}", response.statusCode(), abbreviate(response.body()));
                return List.of();
            }
            return parseHits(response.body());
        } catch (Exception ex) {
            log.warn("vector retrieve failed, fallback to keyword: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<RagKnowledgeStore.ScoredChunk> parseHits(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode hits = root.get("hits");
        if (hits == null || !hits.isArray() || hits.isEmpty()) {
            return List.of();
        }
        List<RagKnowledgeStore.ScoredChunk> out = new ArrayList<>();
        for (JsonNode hit : hits) {
            if (hit == null || hit.isNull()) {
                continue;
            }
            String id = text(hit, "id");
            String title = text(hit, "title");
            String tags = text(hit, "tags");
            String content = text(hit, "content");
            if (id.isBlank()) {
                id = text(hit, "source").replaceAll("\\.md$", "");
            }
            if (title.isBlank()) {
                title = id;
            }
            if (content.isBlank()) {
                continue;
            }
            double score = hit.has("score") && hit.get("score").isNumber()
                    ? hit.get("score").asDouble()
                    : 8.0;
            // 保证能通过 AdminAiService 的 score>=5 有效命中门槛
            if (score > 0 && score < 5.0) {
                score = 5.0 + score;
            }
            RagChunk chunk = new RagChunk(id, title, tags, content);
            out.add(new RagKnowledgeStore.ScoredChunk(chunk, score));
        }
        return out;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return "";
        }
        return v.asText("").trim();
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= 200 ? t : t.substring(0, 200) + "...";
    }
}
