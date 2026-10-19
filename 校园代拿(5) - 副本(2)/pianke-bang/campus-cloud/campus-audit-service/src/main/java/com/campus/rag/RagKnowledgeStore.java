package com.campus.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 classpath:rag/knowledge/*.md 加载片刻帮知识库。
 * 检索：优先 Python 向量服务（可关），失败则关键词相关度检索。
 * 知识权威源见仓库根目录 agent/rag/knowledge/。
 */
@Component
public class RagKnowledgeStore {
    private static final Logger log = LoggerFactory.getLogger(RagKnowledgeStore.class);
    private static final Pattern TAG_LINE = Pattern.compile("^标签[:：]\\s*(.+)$");

    private final List<RagChunk> chunks = new ArrayList<>();
    private final RagVectorClient vectorClient;

    public RagKnowledgeStore(RagVectorClient vectorClient) {
        this.vectorClient = vectorClient;
        reload();
    }

    public synchronized void reload() {
        chunks.clear();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:rag/knowledge/*.md");
            for (Resource resource : resources) {
                if (resource == null || !resource.exists()) {
                    continue;
                }
                String filename = resource.getFilename() == null ? "doc" : resource.getFilename();
                if (filename.startsWith("00-")) {
                    continue;
                }
                String raw = readResource(resource);
                chunks.add(parseMarkdown(filename, raw));
            }
            log.info("RAG knowledge loaded: {} chunk(s); vectorEnabled={}",
                    chunks.size(), vectorClient != null && vectorClient.isEnabled());
        } catch (Exception ex) {
            log.warn("RAG knowledge load failed: {}", ex.getMessage());
        }
    }

    public List<RagChunk> all() {
        return List.copyOf(chunks);
    }

    /**
     * 返回相关度最高的 topK 片段。
     * 向量服务启用且有结果时优先；否则关键词检索。
     */
    public List<ScoredChunk> retrieve(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (vectorClient != null && vectorClient.isEnabled()) {
            List<ScoredChunk> vectorHits = vectorClient.retrieve(query, topK);
            if (vectorHits != null && !vectorHits.isEmpty()) {
                log.info("RAG retrieve backend=vector hits={} top={}",
                        vectorHits.size(),
                        vectorHits.get(0).chunk().getTitle());
                return vectorHits;
            }
            log.info("RAG retrieve backend=keyword (vector empty/unavailable)");
        }
        return retrieveByKeyword(query, topK);
    }

    /** 仅关键词检索（评测 / 兜底） */
    public List<ScoredChunk> retrieveByKeyword(String query, int topK) {
        if (query == null || query.isBlank() || chunks.isEmpty()) {
            return List.of();
        }
        Set<String> terms = tokenize(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        List<ScoredChunk> scored = new ArrayList<>();
        for (RagChunk chunk : chunks) {
            double score = score(chunk, terms, query);
            if (score > 0) {
                scored.add(new ScoredChunk(chunk, score));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        if (scored.size() > topK) {
            return new ArrayList<>(scored.subList(0, topK));
        }
        return scored;
    }

    /** 按知识库 id 片段强制取文档（口语问法兜底）。 */
    public List<ScoredChunk> findByIdContains(String idPart) {
        if (idPart == null || idPart.isBlank() || chunks.isEmpty()) {
            return List.of();
        }
        String key = idPart.trim().toLowerCase(Locale.ROOT);
        for (RagChunk chunk : chunks) {
            String id = chunk.getId() == null ? "" : chunk.getId().toLowerCase(Locale.ROOT);
            if (id.contains(key)) {
                return List.of(new ScoredChunk(chunk, 9.0));
            }
        }
        return List.of();
    }

    private double score(RagChunk chunk, Set<String> queryTerms, String rawQuery) {
        String hay = normalize(chunk.searchableText());
        String title = normalize(chunk.getTitle());
        String tags = normalize(chunk.getTags());
        double score = 0;
        String qn = normalize(rawQuery);
        if (!title.isBlank() && (qn.contains(title) || title.contains(qn))) {
            score += 8;
        }
        for (String term : queryTerms) {
            if (term.length() < 2) {
                continue;
            }
            if (title.contains(term)) {
                score += 4;
            }
            if (tags.contains(term)) {
                score += 3;
            }
            if (hay.contains(term)) {
                score += 1.5;
            }
        }
        if (containsAny(qn, "你是谁", "小新", "智能客服") && containsAny(hay, "片刻小新", "平台概览", "片刻帮")) {
            score += 6;
        }
        if (containsAny(qn, "发布", "发单", "手续费") && containsAny(hay, "发布")) {
            score += 3;
        }
        if (containsAny(qn, "接单", "配送", "完成") && containsAny(hay, "接单")) {
            score += 3;
        }
        if (containsAny(qn, "钱包", "提现", "酬劳", "到账", "余额", "拿出来", "取出来", "提出去", "提现")
                && containsAny(hay, "钱包", "提现", "酬劳")) {
            score += 3;
        }
        // 「钱怎么拿出来」口语 → 提现/钱包
        if (containsAny(qn, "拿出来", "取出来", "提出去", "怎么拿钱", "钱怎么拿")
                && containsAny(hay, "提现", "钱包")) {
            score += 6;
        }
        if (containsAny(qn, "接单员", "认证", "申请") && containsAny(hay, "接单员")) {
            score += 3;
        }
        if (containsAny(qn, "信誉", "信用", "信用积分", "封禁", "违规") && containsAny(hay, "信誉", "信用")) {
            score += 3;
        }
        if (containsAny(qn, "取消", "已接", "退回大厅") && containsAny(hay, "取消", "接单", "配送")) {
            score += 5;
        }
        return score;
    }

    private boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(normalize(k))) {
                return true;
            }
        }
        return false;
    }

    private RagChunk parseMarkdown(String filename, String raw) {
        String id = filename.replaceAll("\\.md$", "");
        String title = id;
        String tags = "";
        StringBuilder body = new StringBuilder();
        String[] lines = raw.replace("\r\n", "\n").split("\n");
        boolean firstHeading = true;
        for (String line : lines) {
            String trimmed = line.trim();
            if (firstHeading && trimmed.startsWith("# ")) {
                title = trimmed.substring(2).trim();
                firstHeading = false;
                continue;
            }
            Matcher m = TAG_LINE.matcher(trimmed);
            if (m.matches()) {
                tags = m.group(1).trim();
                continue;
            }
            body.append(line).append('\n');
        }
        return new RagChunk(id, title, tags, body.toString().trim());
    }

    private String readResource(Resource resource) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    static Set<String> tokenize(String text) {
        String n = normalize(text);
        Set<String> terms = new LinkedHashSet<>();
        if (n.isBlank()) {
            return terms;
        }
        Matcher wm = Pattern.compile("[a-z0-9]{2,}").matcher(n);
        while (wm.find()) {
            terms.add(wm.group());
        }
        Matcher cm = Pattern.compile("[\\u4e00-\\u9fff]{2,}").matcher(n);
        while (cm.find()) {
            String seg = cm.group();
            terms.add(seg);
            for (int i = 0; i < seg.length() - 1; i++) {
                terms.add(seg.substring(i, i + 2));
            }
            if (seg.length() >= 3) {
                for (int i = 0; i < seg.length() - 2; i++) {
                    terms.add(seg.substring(i, i + 3));
                }
            }
        }
        return terms;
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s　]+", "")
                .replaceAll("[？?。！!，,、；;：:\\-—_（）()【】\\[\\]《》\"'‘’“”]", "");
    }

    public record ScoredChunk(RagChunk chunk, double score) {
    }
}
