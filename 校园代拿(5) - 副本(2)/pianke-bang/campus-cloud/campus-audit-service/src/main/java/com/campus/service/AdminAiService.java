package com.campus.service;

import com.campus.agent.AgentGraphClient;
import com.campus.config.LlmProperties;
import com.campus.dto.AdminAiConfigRequest;
import com.campus.dto.AdminAiTaskAuditResult;
import com.campus.dto.CustomerSupportChatMessage;
import com.campus.dto.CustomerSupportChatRequest;
import com.campus.model.DeliveryTask;
import com.campus.rag.RagChunk;
import com.campus.rag.RagKnowledgeStore;
import com.campus.security.AuthContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * LLM 调用（任务审核）+ 片刻小新 RAG 智能客服。
 * 知识库权威源：仓库根目录 agent/rag/；运行时读 classpath:rag/。
 */
@Service
public class AdminAiService {
    private static final String AUDIT_SYSTEM_PROMPT =
            "你是内容审核员。请仅判断任务发布内容是否包含以下违规：\n"
                    + "1) 辱骂/侮辱/仇恨言论\n"
                    + "2) 色情/露骨性内容/性暗示/色情图片描述/性服务引流\n"
                    + "如果不包含以上违规内容，则判为通过。\n"
                    + "输出严格 JSON（只能是一个 JSON 对象，不能输出任何多余文本）。\n"
                    + "JSON 结构：{\n"
                    + "  \"decision\": \"APPROVE\" 或 \"REJECT\",\n"
                    + "  \"reason\": \"简要原因（1-2 句即可）\",\n"
                    + "  \"violations\": [\"可选：违规点1\",\"可选：违规点2\"]\n"
                    + "}\n"
                    + "决策规则：如果 violations 为空数组，则 decision 必须为 \"APPROVE\"。";

    private static final String DEFAULT_RAG_SYSTEM =
            "你是「片刻帮」校园互助平台的智能客服「片刻小新」。"
                    + "只根据检索到的知识回答；不足时建议转人工；末尾需要转人工时输出 [[ACTION:suggest_handoff]]。";

    private static final String TOOL_ROUTER_SYSTEM =
            "你是片刻帮智能客服「片刻小新」的工具调度器。"
                    + "根据用户问题决定调用哪些只读工具；可同时调用多个。"
                    + "查个人数据（余额/任务/提现进度/信誉/认证/申诉/优惠券/加急/流水/任务时间线）必须调用对应工具，不要编造数字。"
                    + "问平台规则或怎么操作时调用 search_knowledge。"
                    + "【硬性示例】用户问「我钱包里的钱怎么才能拿出来」「钱怎么拿出来」这类："
                    + "必须同时调用 get_wallet、get_rider_cert、search_knowledge 三个工具，缺一不可。"
                    + "若一句话既问「我的数据」又问「怎么做」，数据工具 + search_knowledge 都要调。"
                    + "闲聊或与平台无关可不调用工具。"
                    + "最多调用 3 个工具。";

    private static final Logger log = LoggerFactory.getLogger(AdminAiService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RagKnowledgeStore ragKnowledgeStore;
    private final SupportAgentToolService supportAgentToolService;
    private final AgentGraphClient agentGraphClient;
    private final String ragSystemPrompt;

    private volatile boolean enabled;
    private volatile boolean autoAuditEnabled = false;
    private volatile String baseUrl;
    private volatile String apiKey;
    private volatile String model;
    private volatile String supportModel;
    private volatile int timeoutSeconds;
    private volatile boolean thinkingEnabled;
    private volatile String reasoningEffort;

    public AdminAiService(
            LlmProperties defaults,
            RagKnowledgeStore ragKnowledgeStore,
            SupportAgentToolService supportAgentToolService,
            AgentGraphClient agentGraphClient
    ) {
        this.ragKnowledgeStore = ragKnowledgeStore;
        this.supportAgentToolService = supportAgentToolService;
        this.agentGraphClient = agentGraphClient;
        this.ragSystemPrompt = loadRagSystemPrompt();
        this.enabled = defaults.isEnabled();
        this.baseUrl = normalizeBaseUrl(defaults.getBaseUrl());
        this.apiKey = defaults.getApiKey() == null ? "" : defaults.getApiKey().trim();
        this.model = safeModel(defaults.getModel());
        String sm = defaults.getSupportModel();
        this.supportModel = (sm == null || sm.isBlank()) ? this.model : safeModel(sm);
        this.timeoutSeconds = parseTimeout(defaults.getTimeoutSeconds());
        this.thinkingEnabled = defaults.isThinkingEnabled();
        this.reasoningEffort = defaults.getReasoningEffort() == null || defaults.getReasoningEffort().isBlank()
                ? "high"
                : defaults.getReasoningEffort().trim();
    }

    public synchronized Map<String, Object> getConfig() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", enabled);
        data.put("baseUrl", baseUrl);
        data.put("apiKeyConfigured", apiKey != null && !apiKey.isBlank());
        data.put("apiKeyMasked", maskApiKey(apiKey));
        data.put("model", model);
        data.put("supportModel", supportModel);
        data.put("timeoutSeconds", timeoutSeconds);
        data.put("thinkingEnabled", thinkingEnabled);
        data.put("reasoningEffort", reasoningEffort);
        data.put("provider", "deepseek");
        return data;
    }

    public synchronized boolean isAutoAuditEnabled() {
        return autoAuditEnabled;
    }

    public synchronized void setAutoAuditEnabled(boolean autoAuditEnabled) {
        this.autoAuditEnabled = autoAuditEnabled;
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized Map<String, Object> updateConfig(AdminAiConfigRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("配置不能为空");
        }
        if (request.getEnabled() != null) {
            enabled = request.getEnabled();
        }
        if (request.getBaseUrl() != null) {
            baseUrl = normalizeBaseUrl(request.getBaseUrl());
        }
        if (request.getApiKey() != null) {
            String key = request.getApiKey().trim();
            // 空字符串或仍是掩码则不覆盖已有密钥
            if (!key.isBlank() && !key.contains("*")) {
                apiKey = key;
            }
        }
        if (request.getModel() != null) {
            model = safeModel(request.getModel());
        }
        if (request.getSupportModel() != null && !request.getSupportModel().isBlank()) {
            supportModel = safeModel(request.getSupportModel());
        }
        if (request.getTimeoutSeconds() != null) {
            int n = request.getTimeoutSeconds();
            if (n < 3 || n > 300) {
                throw new IllegalArgumentException("超时时间必须在 3-300 秒");
            }
            timeoutSeconds = n;
        }
        return getConfig();
    }

    /** OpenAI 兼容：GET /models；失败时返回空列表，不阻断配置页 */
    public List<String> listModels() {
        if (apiKey == null || apiKey.isBlank()) {
            return List.of();
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(chatApiRoot() + "/models"))
                    .timeout(Duration.ofSeconds(Math.min(timeoutSeconds, 20)))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(res.body());
            JsonNode data = root.path("data");
            List<String> names = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode item : data) {
                    String id = item.path("id").asText("");
                    if (!id.isBlank()) {
                        names.add(id);
                    }
                }
            }
            return names;
        } catch (Exception ex) {
            return List.of();
        }
    }

    public Map<String, Object> testChat(String prompt) {
        if (!enabled) {
            throw new IllegalStateException("AI 模块未启用，请先打开开关");
        }
        requireApiKey();
        try {
            String finalPrompt = prompt == null || prompt.isBlank()
                    ? "请用一句话回复：API 连接成功。"
                    : prompt.trim();
            String answer = chatCompletion(model, List.of(
                    Map.of("role", "user", "content", finalPrompt)
            ), timeoutSeconds, thinkingEnabled);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("model", model);
            data.put("reply", answer);
            data.put("baseUrl", baseUrl);
            return data;
        } catch (Exception ex) {
            throw new IllegalStateException("LLM API 测试失败：" + ex.getMessage(), ex);
        }
    }

    /**
     * 片刻小新 RAG：检索知识库 →（可选）DeepSeek 生成；转人工意图直接提示。
     */
    public Map<String, Object> customerSupportChat(CustomerSupportChatRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            throw new IllegalArgumentException("请输入您的问题");
        }
        String userMessage = request.getMessage().trim();
        if (userMessage.length() > 800) {
            throw new IllegalArgumentException("问题过长，请控制在 800 字以内");
        }

        // 去掉引号/标点，避免「"钱怎么拿出来」这类输入干扰意图识别与短句拼接
        String normalized = normalizeSupportText(userMessage);
        List<CustomerSupportChatMessage> history = request.getHistory();

        // 辱骂 / 不当用语：礼貌降温，不接话
        if (isAbusiveMessage(normalized)) {
            return fixedSupportReply(
                    "rag-refuse-abuse",
                    "refuse",
                    "我在呢～这类用语我没法继续接。若你有片刻帮使用上的问题（发布、接单、钱包、认证等），直接问我就可以；"
                            + "若遇到纠纷，也可点击右上角「转人工」。",
                    List.of()
            );
        }

        boolean wantHandoff = normalized.contains("转人工")
                || normalized.contains("转接人工")
                || normalized.contains("转接客服")
                || normalized.contains("人工客服")
                || normalized.contains("找客服")
                || normalized.contains("找人工")
                || normalized.contains("要人工")
                || normalized.contains("接通人工")
                || normalized.contains("连人工")
                || normalized.contains("投诉")
                || normalized.contains("举报");
        if (wantHandoff) {
            // 明确要求转接时由前端直接调用分配接口；这里只做确认提示兜底
            boolean directTransfer = normalized.contains("转人工")
                    || normalized.contains("转接人工")
                    || normalized.contains("转接客服")
                    || normalized.contains("找人工")
                    || normalized.contains("要人工")
                    || normalized.contains("接通人工")
                    || normalized.contains("连人工");
            return fixedSupportReply(
                    "rag-handoff",
                    "handoff",
                    directTransfer
                            ? "正在为你接入人工客服，请稍候…"
                            : "这类问题更适合人工处理。直接发送「转人工」，或点顶部转接入口即可接入人工客服。",
                    List.of("suggest_handoff")
            );
        }

        // 越权 / 无法办理：明确拒绝，不要扯到平台介绍
        if (isOutOfScopeRequest(normalized)) {
            return fixedSupportReply(
                    "rag-refuse-scope",
                    "refuse",
                    "抱歉，这个问题我没法为你办理。"
                            + "我是片刻帮的智能客服，只能解答平台怎么用（例如发布任务、接单、钱包提现、接单员认证），"
                            + "不能创建管理员账号、改权限或处理站外操作。"
                            + "如果你想了解平台功能，换个具体问题问我；需要人工协助可点右上角「转人工」。",
                    List.of()
            );
        }

        // 对话记忆：回答「我刚刚问你什么」等元问题
        Map<String, Object> memoryHit = tryAnswerFromMemory(normalized, history);
        if (memoryHit != null) {
            return memoryHit;
        }

        boolean llmReady = enabled && apiKey != null && !apiKey.isBlank();

        // 优先 LangGraph 编排（失败则回落现有 Java Agent）
        if (agentGraphClient != null && agentGraphClient.isEnabled()) {
            try {
                Map<String, Object> graphHit = agentGraphClient.chat(
                        userMessage,
                        AuthContext.getCurrentUser(),
                        AuthContext.getCurrentUserId(),
                        history
                );
                if (graphHit != null && graphHit.get("reply") != null
                        && !String.valueOf(graphHit.get("reply")).isBlank()
                        && !isWeakGraphMiss(graphHit)) {
                    return graphHit;
                }
            } catch (Exception ex) {
                log.warn("langgraph path failed, fallback Java agent: {}", ex.getMessage());
            }
        }

        // LLM 选工具（查数据 / 查知识）；失败则关键词工具兜底
        if (llmReady) {
            try {
                Map<String, Object> agentHit = tryLlmToolAgent(userMessage, normalized, history);
                if (agentHit != null) {
                    return agentHit;
                }
            } catch (Exception ex) {
                log.warn("llm tool agent failed, fallback keyword/RAG: {}", ex.getMessage());
            }
        }

        // 关键词工具兜底（LLM 未开或未选工具时）
        SupportAgentToolService.ToolIntent toolIntent = supportAgentToolService.detectIntent(normalized);
        if (toolIntent != SupportAgentToolService.ToolIntent.NONE
                && toolIntent != SupportAgentToolService.ToolIntent.SEARCH_KNOWLEDGE) {
            return supportAgentToolService.run(toolIntent, normalized);
        }

        // 「你是谁」类：给人设短答，不要整页倒知识库
        if (isIdentityQuestion(normalized)) {
            return fixedSupportReply(
                    "rag-persona",
                    "persona",
                    "我是「片刻帮」的智能客服片刻小新，专门帮你解答校园互助相关的问题～"
                            + "你可以问我：如何发布任务、接单赚钱、提现到账、认证接单员等。"
                            + "有什么需要帮忙的吗？",
                    List.of()
            );
        }

        boolean smallTalk = isSmallTalk(normalized);

        // 未接 DeepSeek 时的闲聊：本地短回复，不走「无法解决」
        if (smallTalk && !llmReady) {
            return fixedSupportReply("rag-chat", "chat", pickSmallTalkReply(normalized), List.of());
        }

        // 短追问拼上上一轮；口语改写后再检索（如「钱怎么拿出来」→ 提现）
        String retrieveQuery = rewriteRetrieveQuery(enrichQueryWithHistory(normalized, history));
        List<RagKnowledgeStore.ScoredChunk> hits = ragKnowledgeStore.retrieve(retrieveQuery, 3);
        // 关键词弱相关常见 1.5～4 分；门槛过高会导致明明有库却「未检索到」
        if (!hits.isEmpty() && hits.get(0).score() < 1.5) {
            hits = List.of();
        }
        if (hits.isEmpty() && isWithdrawHowToQuestion(normalized)) {
            hits = ragKnowledgeStore.findByIdContains("04-wallet");
        }
        List<String> citations = new ArrayList<>();
        for (RagKnowledgeStore.ScoredChunk hit : hits) {
            citations.add(hit.chunk().getTitle());
        }

        // 无 DeepSeek：业务走知识库；闲聊已在上方处理
        if (!llmReady) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("model", "rag-extractive");
            data.put("source", "rag");
            data.put("citations", citations);
            if (hits.isEmpty()) {
                data.put("reply",
                        "抱歉，这个问题我暂时无法为你解决。"
                                + "我更擅长回答片刻帮的使用问题，比如怎么发布、怎么接单、酬劳何时到账、如何认证接单员。"
                                + "你可以换个说法再问；若仍无法处理，请点击右上角「转人工」。");
                data.put("actions", List.of("suggest_handoff"));
            } else {
                data.put("reply", buildConversationalReply(userMessage, hits.get(0).chunk()));
                data.put("actions", List.of());
            }
            return data;
        }

        // 已接入 DeepSeek：检索 + 多轮 + 可闲聊
        String context = hits.isEmpty()
                ? "（本次未检索到强相关业务知识；若是闲聊请自然回应，若是业务问题请礼貌说明并引导换问法或转人工。）"
                : buildRagContext(hits);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", ragSystemPrompt
                + "\n你可以参考对话历史理解指代；业务事实以检索知识为准。"));

        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - 8);
            for (int i = start; i < history.size(); i++) {
                CustomerSupportChatMessage item = history.get(i);
                if (item == null || item.getContent() == null || item.getContent().isBlank()) {
                    continue;
                }
                String role = normalizeChatRole(item.getRole());
                if (role == null) {
                    continue;
                }
                messages.add(Map.of("role", role, "content", item.getContent().trim()));
            }
        }
        messages.add(Map.of(
                "role", "user",
                "content", "【检索到的知识】\n" + context + "\n\n【用户问题】\n" + userMessage
        ));

        try {
            String raw = chatCompletion(supportModel, messages, Math.max(timeoutSeconds, 45), false);
            List<String> actions = new ArrayList<>();
            String answer = stripActionMarkers(raw, actions);
            if (answer.isBlank()) {
                if (smallTalk) {
                    answer = pickSmallTalkReply(normalized);
                } else if (hits.isEmpty()) {
                    answer = "抱歉，这个问题我暂时无法为你解决。我主要解答片刻帮怎么用；换个平台相关问题，或点击「转人工」也可以。";
                    actions = List.of("suggest_handoff");
                } else {
                    answer = buildConversationalReply(userMessage, hits.get(0).chunk());
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("model", supportModel);
            data.put("source", hits.isEmpty() ? "llm-chat" : "rag+llm");
            data.put("reply", answer);
            data.put("actions", actions);
            data.put("citations", citations);
            return data;
        } catch (Exception ex) {
            if (smallTalk) {
                return fixedSupportReply("rag-chat-fallback", "chat", pickSmallTalkReply(normalized), List.of());
            }
            if (!hits.isEmpty()) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("model", "rag-fallback");
                data.put("source", "rag");
                data.put("reply", buildConversationalReply(userMessage, hits.get(0).chunk()));
                data.put("actions", List.of());
                data.put("citations", citations);
                return data;
            }
            throw new IllegalStateException("智能客服暂时不可用：" + ex.getMessage(), ex);
        }
    }

    private boolean isSmallTalk(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        return normalized.equals("你在干嘛")
                || normalized.equals("在干嘛")
                || normalized.equals("干嘛呢")
                || normalized.equals("忙什么")
                || normalized.equals("忙啥")
                || normalized.contains("在干嘛")
                || normalized.contains("干什么呢")
                || normalized.equals("聊聊天")
                || normalized.equals("陪我聊会")
                || normalized.equals("好无聊")
                || normalized.equals("无聊")
                || normalized.equals("哈哈")
                || normalized.equals("呵呵")
                || normalized.equals("嗯")
                || normalized.equals("哦")
                || normalized.equals("嘿")
                || normalized.equals("嗨")
                || normalized.equals("早上好")
                || normalized.equals("中午好")
                || normalized.equals("晚上好")
                || normalized.equals("晚安")
                || normalized.equals("加油")
                || normalized.equals("谢谢")
                || normalized.equals("谢谢你")
                || normalized.equals("辛苦了")
                || normalized.equals("吃了吗")
                || normalized.equals("在吗呀");
    }

    private String pickSmallTalkReply(String normalized) {
        if (normalized != null && (normalized.contains("谢谢") || normalized.contains("辛苦"))) {
            return "不客气呀～有发布、接单或钱包方面的问题，随时叫我片刻小新就好。";
        }
        if (normalized != null && (normalized.contains("晚安") || normalized.contains("晚上好"))) {
            return "晚上好～我在线帮你看平台问题。想发单、接单或查酬劳，直接说就行。";
        }
        if (normalized != null && (normalized.contains("无聊") || normalized.contains("聊聊天"))) {
            return "哈哈我也在值班看消息呢～可以跟我聊聊片刻帮怎么用，或者直接问「怎么发布任务」。";
        }
        return "我在这儿值班呀，随时准备帮你解答片刻帮的问题～"
                + "想发单、接单、提现或认证接单员，直接问我就行；也可以先随便聊聊。";
    }

    private Map<String, Object> fixedSupportReply(String model, String source, String reply, List<String> actions) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("model", model);
        data.put("source", source);
        data.put("reply", reply);
        data.put("actions", actions == null ? List.of() : actions);
        data.put("citations", List.of());
        return data;
    }

    private boolean isAbusiveMessage(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        String[] bad = {
                "草泥马", "操你妈", "操你", "傻逼", "傻b", "sb", "nm", "nmsl",
                "滚你", "去死", "妈的", "他妈的", "混蛋", "白痴"
        };
        for (String w : bad) {
            if (normalized.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** 越权、站外操作、无法由客服办理的请求 */
    private boolean isOutOfScopeRequest(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        return normalized.contains("管理员账号")
                || normalized.contains("生成管理员")
                || normalized.contains("创建管理员")
                || normalized.contains("开管理员")
                || normalized.contains("给我管理员")
                || normalized.contains("升管理员")
                || normalized.contains("改成管理员")
                || normalized.contains("系统权限")
                || normalized.contains("数据库")
                || normalized.contains("破解")
                || normalized.contains("开挂")
                || normalized.contains("外挂")
                || normalized.contains("刷余额")
                || normalized.contains("白嫖")
                || normalized.contains("免费充值")
                || normalized.contains("盗号")
                || normalized.contains("窃取")
                || (normalized.contains("生成") && normalized.contains("账号") && normalized.contains("管理"))
                || (normalized.contains("帮我") && normalized.contains("管理员"));
    }

    /** 「我刚刚问你什么」等：用前端传来的 history 作会话记忆 */
    private Map<String, Object> tryAnswerFromMemory(String normalized, List<CustomerSupportChatMessage> history) {
        if (!isMemoryQuestion(normalized)) {
            return null;
        }
        String lastUser = findLastUserUtterance(history);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("model", "memory");
        data.put("source", "memory");
        data.put("citations", List.of());
        data.put("actions", List.of());
        if (lastUser == null || lastUser.isBlank()) {
            data.put("reply", "这轮对话里我还没记下你更早的问题。你可以再问一次，我会接着帮你解答～");
        } else {
            data.put("reply", "你上一句问的是：「" + lastUser + "」。需要我继续帮你解答这个问题吗？");
        }
        return data;
    }

    private boolean isMemoryQuestion(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        return normalized.contains("刚刚问")
                || normalized.contains("刚才问")
                || normalized.contains("上一句")
                || normalized.contains("上一个问题")
                || normalized.contains("我问你什么")
                || normalized.contains("我说了什么")
                || normalized.contains("还记得吗")
                || normalized.contains("记得我")
                || normalized.equals("我刚刚问你的是什么")
                || normalized.equals("我刚才问你什么");
    }

    private String findLastUserUtterance(List<CustomerSupportChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            CustomerSupportChatMessage item = history.get(i);
            if (item == null || item.getContent() == null || item.getContent().isBlank()) {
                continue;
            }
            String role = normalizeChatRole(item.getRole());
            if (!"user".equals(role)) {
                continue;
            }
            String content = item.getContent().trim();
            String n = content.replaceAll("[？?。！!，,\\s]", "").toLowerCase(Locale.ROOT);
            // 跳过记忆类元问题本身
            if (isMemoryQuestion(n) || isIdentityQuestion(n)) {
                continue;
            }
            return content;
        }
        return null;
    }

    /** 客服输入归一化：去空白与常见标点（含中英文引号），便于意图/检索。 */
    private static String normalizeSupportText(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s　]+", "")
                .replaceAll("[？?。！!，,、；;：:\\-—_（）()【】\\[\\]《》\"'‘’“”]", "");
    }

    private static boolean isWithdrawHowToQuestion(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        if (normalized.contains("怎么提现")
                || normalized.contains("如何提现")
                || normalized.contains("提现规则")
                || normalized.contains("提现手续费")
                || normalized.contains("钱怎么拿")
                || normalized.contains("怎么拿钱")
                || normalized.contains("钱怎么提")
                || normalized.contains("怎么提取")
                || normalized.contains("如何提取")
                || normalized.contains("拿出来")
                || normalized.contains("取出来")
                || normalized.contains("提出去")
                || normalized.contains("提取出来")
                || (normalized.contains("提现") && (normalized.contains("怎么") || normalized.contains("如何") || normalized.contains("规则")))) {
            return true;
        }
        // 「钱怎么…」口语，只要不是查余额金额
        boolean talkMoney = normalized.contains("钱") || normalized.contains("余额") || normalized.contains("现金");
        boolean takeOut = normalized.contains("拿") || normalized.contains("取") || normalized.contains("提");
        boolean notBalanceQuery = !(normalized.contains("多少") || normalized.contains("还有"));
        return talkMoney && takeOut && notBalanceQuery;
    }

    /** 把口语问法改写成更利检索的查询。 */
    private static String rewriteRetrieveQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        String q = query.trim();
        if (isWithdrawHowToQuestion(normalizeSupportText(q))) {
            return q + " 提现 钱包 余额 手续费";
        }
        if (SupportAgentToolService.looksLikeTaskPolicyHowTo(normalizeSupportText(q))) {
            return q + " 取消 接单 配送中 待发布方确认 退回大厅";
        }
        return q;
    }

    private String enrichQueryWithHistory(String normalizedMessage, List<CustomerSupportChatMessage> history) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return "";
        }
        String n = normalizedMessage;
        // 完整问句（含怎么/如何等）不要仅因字数短就拼历史，否则带引号的短问会干扰检索
        boolean completeQuestion = n.contains("怎么") || n.contains("如何") || n.contains("什么")
                || n.contains("多少") || n.contains("为什么") || n.contains("能否") || n.contains("可以吗");
        boolean shortFollowUp = !completeQuestion && (n.length() <= 6
                || n.startsWith("那")
                || n.startsWith("然后")
                || n.contains("怎么弄")
                || n.contains("怎么办")
                || n.equals("呢")
                || n.equals("然后呢"));
        if (!shortFollowUp) {
            return n;
        }
        String lastUser = findLastUserUtterance(history);
        if (lastUser == null || lastUser.isBlank()) {
            return n;
        }
        return normalizeSupportText(lastUser) + " " + n;
    }

    private boolean isIdentityQuestion(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        return normalized.equals("你是谁")
                || normalized.equals("你是")
                || normalized.equals("你谁")
                || normalized.contains("你是谁")
                || normalized.contains("你叫什么")
                || normalized.contains("介绍一下你")
                || normalized.equals("hi")
                || normalized.equals("你好")
                || normalized.equals("在吗");
    }

    private String buildRagContext(List<RagKnowledgeStore.ScoredChunk> hits) {
        if (hits == null || hits.isEmpty()) {
            return "（未检索到相关知识）";
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (RagKnowledgeStore.ScoredChunk hit : hits) {
            RagChunk c = hit.chunk();
            sb.append("### 资料").append(i++).append("：").append(c.getTitle()).append('\n')
                    .append(c.getContent()).append("\n\n");
        }
        return sb.toString().trim();
    }

    /** 把知识片段转成客服口吻的短答，避免原文大段倾倒 */
    private String buildConversationalReply(String question, RagChunk chunk) {
        String id = chunk.getId() == null ? "" : chunk.getId();
        if (id.contains("05-rider") || chunk.getTitle().contains("接单员")) {
            return "成为接单员很简单：登录后打开头像菜单里的「接单员申请」，填写实名信息提交，"
                    + "等管理员审核通过后就能在大厅接单啦。审核中暂时还不能接单哦。";
        }
        if (id.contains("04-wallet") || chunk.getTitle().contains("钱包")) {
            return "酬劳会在发布者确认完成后进入你的平台余额，可在「我的钱包」查看。"
                    + "提现需要在钱包里发起申请，管理员审核通过后才会打款。";
        }
        if (id.contains("02-publish") || chunk.getTitle().contains("发布")) {
            return "发布任务：进「发布」页填写取送地点、酬劳和说明 → 预付赏金 → 等待审核通过后进入大厅。"
                    + "平台没有额外手续费，赏金取消或驳回后会按规则退还。";
        }
        if (id.contains("03-claim") || chunk.getTitle().contains("接单")) {
            return "接单前需先通过接单员认证。在任务大厅选好单，可先和发布者私聊确认，"
                    + "接单配送完成后提交凭证，等对方确认，酬劳就会进钱包。";
        }
        if (id.contains("06-chat") || chunk.getTitle().contains("客服")) {
            return "我是智能客服片刻小新，可以先问我平台怎么用；"
                    + "若是资金纠纷或投诉，建议点击右上角「转人工」。";
        }
        if (id.contains("07-rules") || chunk.getTitle().contains("信誉")) {
            return "信誉分用来约束恶意取消等行为，过低可能暂时无法接单。"
                    + "任务内容也请避免违规描述。复杂纠纷建议转人工处理。";
        }
        if (id.contains("01-platform") || chunk.getTitle().contains("概览")) {
            return "片刻帮是校园互助跑腿平台，可以发布/接取任务、用钱包收付款，"
                    + "有事也能找我或转人工客服。你想先了解发布、接单还是钱包呢？";
        }
        // 兜底：取前几条非空行，压缩成两三句
        String body = chunk.getContent() == null ? "" : chunk.getContent().trim();
        String[] lines = body.split("\\R");
        StringBuilder brief = new StringBuilder();
        for (String line : lines) {
            String t = line.trim();
            if (t.isBlank() || t.startsWith("#") || t.startsWith("标签")) {
                continue;
            }
            if (t.startsWith("- ")) {
                t = t.substring(2).trim();
            }
            if (t.isBlank()) {
                continue;
            }
            if (brief.length() > 0) {
                brief.append(' ');
            }
            brief.append(t);
            if (brief.length() >= 120) {
                break;
            }
        }
        String text = brief.toString().trim();
        if (text.length() > 160) {
            text = text.substring(0, 160).trim() + "…";
        }
        if (text.isBlank()) {
            text = "关于「" + chunk.getTitle() + "」我这里有说明，你可以再具体一点，我帮你拆步骤。";
        }
        return text + " 还有想问的可以直接说～";
    }

    private String loadRagSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("rag/prompts/system.md");
            if (!resource.exists()) {
                return DEFAULT_RAG_SYSTEM;
            }
            try (InputStream in = resource.getInputStream()) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                return text.isBlank() ? DEFAULT_RAG_SYSTEM : text;
            }
        } catch (Exception ignore) {
            return DEFAULT_RAG_SYSTEM;
        }
    }

    private String normalizeChatRole(String raw) {
        if (raw == null) {
            return null;
        }
        String role = raw.trim().toLowerCase(Locale.ROOT);
        if ("user".equals(role) || "assistant".equals(role)) {
            return role;
        }
        if ("bot".equals(role) || "ai".equals(role)) {
            return "assistant";
        }
        return null;
    }

    private String stripActionMarkers(String raw, List<String> actionsOut) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.contains("[[ACTION:suggest_handoff]]")) {
            actionsOut.add("suggest_handoff");
            text = text.replace("[[ACTION:suggest_handoff]]", "").trim();
        }
        return text;
    }

    public AdminAiTaskAuditResult auditTask(DeliveryTask task) {
        if (task == null) {
            throw new IllegalArgumentException("任务不能为空");
        }
        if (!enabled) {
            throw new IllegalStateException("AI 模块未启用，请先打开开关");
        }
        requireApiKey();

        String userContent = buildTaskAuditUserContent(task);
        try {
            String content = chatCompletion(model, List.of(
                    Map.of("role", "system", "content", AUDIT_SYSTEM_PROMPT),
                    Map.of("role", "user", "content", userContent)
            ), timeoutSeconds, thinkingEnabled);
            JsonNode json = parseJsonFromContent(content);

            List<String> violations = new ArrayList<>();
            JsonNode vNode = json.path("violations");
            if (vNode.isArray()) {
                for (JsonNode n : vNode) {
                    String s = n.asText("");
                    if (!s.isBlank()) {
                        violations.add(s);
                    }
                }
            }

            String decisionRaw = json.path("decision").asText("");
            String reason = json.path("reason").asText("");
            List<String> seriousViolations = filterSeriousViolations(violations);
            String decision;
            if (seriousViolations.isEmpty()) {
                decision = "APPROVE";
                if (reason == null || reason.isBlank()) {
                    reason = "未发现辱骂/色情等明显不良内容";
                }
            } else {
                decision = (decisionRaw == null || decisionRaw.isBlank()) ? "REJECT" : normalizeDecision(decisionRaw);
                if (reason == null || reason.isBlank()) {
                    reason = "检测到疑似违规内容";
                }
            }

            AdminAiTaskAuditResult result = new AdminAiTaskAuditResult();
            result.setTaskId(task.getId());
            result.setDecision(decision);
            result.setReason(reason);
            result.setViolations(seriousViolations);
            return result;
        } catch (Exception ex) {
            AdminAiTaskAuditResult result = new AdminAiTaskAuditResult();
            result.setTaskId(task.getId());
            result.setDecision("REJECT");
            result.setReason("AI 审核失败");
            result.setViolations(List.of("AI 审核失败"));
            result.setError(ex.getMessage());
            return result;
        }
    }

    /**
     * LLM function calling：选工具查数据 / 查知识；纯知识问题返回 null 走原 RAG 路径。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> tryLlmToolAgent(
            String userMessage,
            String normalized,
            List<CustomerSupportChatMessage> history
    ) throws Exception {
        requireApiKey();
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", TOOL_ROUTER_SYSTEM));
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - 4);
            for (int i = start; i < history.size(); i++) {
                CustomerSupportChatMessage item = history.get(i);
                if (item == null || item.getContent() == null || item.getContent().isBlank()) {
                    continue;
                }
                String role = normalizeChatRole(item.getRole());
                if (role == null) {
                    continue;
                }
                String content = item.getContent().trim();
                if (content.length() > 400) {
                    content = content.substring(0, 400) + "…";
                }
                messages.add(Map.of("role", role, "content", content));
            }
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        JsonNode messageNode = chatCompletionMessage(
                supportModel,
                messages,
                supportAgentToolService.openAiToolDefinitions(),
                Math.min(Math.max(timeoutSeconds, 45), 90),
                false
        );
        JsonNode toolCalls = messageNode.get("tool_calls");

        List<Map<String, Object>> toolResults = new ArrayList<>();
        java.util.LinkedHashSet<String> usedTools = new java.util.LinkedHashSet<>();
        boolean needRag = false;
        String ragQuery = normalized;

        if (toolCalls != null && toolCalls.isArray()) {
            int executed = 0;
            for (JsonNode call : toolCalls) {
                if (call == null || executed >= 3) {
                    break;
                }
                String name = call.path("function").path("name").asText("").trim();
                if (name.isBlank()) {
                    continue;
                }
                String argsJson = call.path("function").path("arguments").asText("{}");
                Map<String, Object> args;
                try {
                    JsonNode argsNode = objectMapper.readTree(argsJson == null || argsJson.isBlank() ? "{}" : argsJson);
                    args = objectMapper.convertValue(argsNode, Map.class);
                    if (args == null) {
                        args = Map.of();
                    }
                } catch (Exception ex) {
                    args = Map.of();
                }
                Map<String, Object> result = supportAgentToolService.runNamed(name, args, normalized);
                executed++;
                usedTools.add(name.toLowerCase(Locale.ROOT));
                if (Boolean.TRUE.equals(result.get("needRag"))) {
                    needRag = true;
                    Object rq = result.get("ragQuery");
                    if (rq != null && !String.valueOf(rq).isBlank()) {
                        ragQuery = String.valueOf(rq).trim();
                    }
                } else {
                    toolResults.add(result);
                }
            }
        }

        // 模型常只调 search_knowledge：对「我的钱怎么拿」强制补钱包+认证
        if (isPersonalWithdrawHowTo(normalized)) {
            needRag = true;
            ensureNamedTool(toolResults, usedTools, "get_wallet", normalized);
            ensureNamedTool(toolResults, usedTools, "get_rider_cert", normalized);
            log.info("llm tool agent personal-withdraw: tools={}", usedTools);
        } else if (SupportAgentToolService.looksLikeTaskPolicyHowTo(normalized)) {
            // 「可以取消已接的任务吗」等：丢掉误选的任务列表，强制走知识库
            needRag = true;
            toolResults.removeIf(r -> {
                Object src = r.get("source");
                return src != null && String.valueOf(src).toLowerCase(Locale.ROOT).contains("task");
            });
            usedTools.remove("list_my_tasks");
            usedTools.remove("get_task_detail");
            log.info("llm tool agent policy-howto strip tasks: tools={}", usedTools);
        } else if (supportAgentToolService.detectIntent(normalized) == SupportAgentToolService.ToolIntent.CREDIT) {
            // 短问「信誉分 / 信用积分」LLM 常漏选工具
            ensureNamedTool(toolResults, usedTools, "get_credit", normalized);
            log.info("llm tool agent force credit: tools={}", usedTools);
        } else if (isHowToOrPolicyQuestion(normalized)) {
            needRag = true;
        }

        if (toolResults.isEmpty() && needRag) {
            // 纯规则 FAQ，无个人数据工具
            return null;
        }
        if (toolResults.isEmpty()) {
            return null;
        }

        List<RagKnowledgeStore.ScoredChunk> hits = List.of();
        if (needRag) {
            String retrieveQuery = rewriteRetrieveQuery(ragQuery);
            hits = ragKnowledgeStore.retrieve(retrieveQuery, 3);
            if (!hits.isEmpty() && hits.get(0).score() < 1.5) {
                hits = List.of();
            }
            if (hits.isEmpty() && isWithdrawHowToQuestion(normalizeSupportText(ragQuery))) {
                hits = ragKnowledgeStore.findByIdContains("04-wallet");
            }
        }

        if (hits.isEmpty() && !needRag) {
            return mergeToolOnlyReplies(toolResults);
        }

        if (hits.isEmpty()) {
            hits = ragKnowledgeStore.retrieve(rewriteRetrieveQuery(normalized), 3);
            if (hits.isEmpty() && isWithdrawHowToQuestion(normalized)) {
                hits = ragKnowledgeStore.findByIdContains("04-wallet");
            }
        }
        return synthesizeToolAndRagReply(userMessage, history, toolResults, hits);
    }

    /**
     * LangGraph 空答 / 弱拒答：不当作成功命中，继续走 Java 关键词工具与 RAG。
     */
    private static boolean isWeakGraphMiss(Map<String, Object> graphHit) {
        if (graphHit == null) {
            return true;
        }
        Object model = graphHit.get("model");
        if (model != null && String.valueOf(model).contains("langgraph-empty")) {
            return true;
        }
        String reply = String.valueOf(graphHit.get("reply") == null ? "" : graphHit.get("reply"));
        if (reply.contains("暂时没有足够信息")) {
            Object payload = graphHit.get("toolPayload");
            return payload == null;
        }
        return false;
    }

    /** 「我钱包/我的钱怎么拿出来」：需要个人数据 + 提现规则 */
    private static boolean isPersonalWithdrawHowTo(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        boolean aboutMoney = normalized.contains("钱")
                || normalized.contains("钱包")
                || normalized.contains("余额")
                || normalized.contains("提现");
        boolean howTo = isHowToOrPolicyQuestion(normalized) || isWithdrawHowToQuestion(normalized);
        boolean personal = normalized.contains("我")
                || normalized.contains("钱包")
                || normalized.contains("余额");
        return aboutMoney && howTo && personal;
    }

    private void ensureNamedTool(
            List<Map<String, Object>> toolResults,
            java.util.Set<String> usedTools,
            String toolName,
            String normalized
    ) {
        String key = toolName.toLowerCase(Locale.ROOT);
        if (usedTools.contains(key)) {
            return;
        }
        Map<String, Object> result = supportAgentToolService.runNamed(toolName, Map.of(), normalized);
        usedTools.add(key);
        if (!Boolean.TRUE.equals(result.get("needRag"))) {
            toolResults.add(result);
        }
    }

    private static boolean isHowToOrPolicyQuestion(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        return normalized.contains("怎么")
                || normalized.contains("如何")
                || normalized.contains("怎样")
                || normalized.contains("规则")
                || normalized.contains("流程")
                || normalized.contains("手续费")
                || normalized.contains("条件")
                || isWithdrawHowToQuestion(normalized);
    }

    private Map<String, Object> mergeToolOnlyReplies(List<Map<String, Object>> toolResults) {
        if (toolResults.size() == 1) {
            return toolResults.get(0);
        }
        StringBuilder reply = new StringBuilder();
        Object card = null;
        List<String> actions = new ArrayList<>();
        for (Map<String, Object> r : toolResults) {
            Object part = r.get("reply");
            if (part != null && !String.valueOf(part).isBlank()) {
                if (reply.length() > 0) {
                    reply.append('\n');
                }
                reply.append(String.valueOf(part).trim());
            }
            if (card == null && r.get("toolPayload") != null) {
                card = r.get("toolPayload");
            }
            Object acts = r.get("actions");
            if (acts instanceof List<?> list) {
                for (Object a : list) {
                    if (a != null) {
                        actions.add(String.valueOf(a));
                    }
                }
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("model", "agent-tool-llm");
        data.put("source", "tool:multi");
        data.put("reply", reply.length() == 0 ? "已查询到相关信息。" : reply.toString());
        data.put("actions", actions);
        data.put("citations", List.of());
        if (card != null) {
            data.put("toolPayload", card);
        }
        return data;
    }

    private Map<String, Object> synthesizeToolAndRagReply(
            String userMessage,
            List<CustomerSupportChatMessage> history,
            List<Map<String, Object>> toolResults,
            List<RagKnowledgeStore.ScoredChunk> hits
    ) throws Exception {
        StringBuilder toolCtx = new StringBuilder();
        Object card = null;
        List<String> actions = new ArrayList<>();
        for (int i = 0; i < toolResults.size(); i++) {
            Map<String, Object> r = toolResults.get(i);
            toolCtx.append("### 工具结果").append(i + 1).append('\n');
            toolCtx.append("source=").append(r.get("source")).append('\n');
            toolCtx.append("reply=").append(r.get("reply")).append('\n');
            if (r.get("toolPayload") != null) {
                toolCtx.append("payload=").append(objectMapper.writeValueAsString(r.get("toolPayload"))).append('\n');
                if (card == null) {
                    card = r.get("toolPayload");
                }
            }
            Object acts = r.get("actions");
            if (acts instanceof List<?> list) {
                for (Object a : list) {
                    if (a != null) {
                        actions.add(String.valueOf(a));
                    }
                }
            }
        }

        List<String> citations = new ArrayList<>();
        String ragCtx = hits == null || hits.isEmpty()
                ? "（无额外知识库片段）"
                : buildRagContext(hits);
        if (hits != null) {
            for (RagKnowledgeStore.ScoredChunk hit : hits) {
                citations.add(hit.chunk().getTitle());
            }
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", ragSystemPrompt
                        + "\n你已获得【工具查询结果】（真实用户数据）与【检索到的知识】。"
                        + "请综合两者用客服口吻简洁回答；数据以工具为准，规则以知识为准；不要编造。"
        ));
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - 6);
            for (int i = start; i < history.size(); i++) {
                CustomerSupportChatMessage item = history.get(i);
                if (item == null || item.getContent() == null || item.getContent().isBlank()) {
                    continue;
                }
                String role = normalizeChatRole(item.getRole());
                if (role == null) {
                    continue;
                }
                messages.add(Map.of("role", role, "content", item.getContent().trim()));
            }
        }
        messages.add(Map.of(
                "role", "user",
                "content", "【工具查询结果】\n" + toolCtx
                        + "\n【检索到的知识】\n" + ragCtx
                        + "\n\n【用户问题】\n" + userMessage
        ));

        String raw = chatCompletion(supportModel, messages, Math.max(timeoutSeconds, 45), false);
        List<String> moreActions = new ArrayList<>();
        String answer = stripActionMarkers(raw, moreActions);
        actions.addAll(moreActions);
        if (answer.isBlank()) {
            answer = mergeToolOnlyReplies(toolResults).get("reply") == null
                    ? "已查到相关信息，请稍后再试或换个问法。"
                    : String.valueOf(mergeToolOnlyReplies(toolResults).get("reply"));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("model", supportModel);
        data.put("source", "tool+rag+llm");
        data.put("reply", answer);
        data.put("actions", actions);
        data.put("citations", citations);
        if (card != null) {
            data.put("toolPayload", card);
        }
        return data;
    }

    private String chatCompletion(String useModel, List<Map<String, String>> messages, int timeoutSec, boolean useThinking) throws Exception {
        JsonNode message = chatCompletionMessage(useModel, castMessages(messages), null, timeoutSec, useThinking);
        String answer = message.path("content").asText("").trim();
        if (answer.isBlank()) {
            answer = message.path("reasoning_content").asText("").trim();
        }
        return answer;
    }

    private List<Map<String, Object>> castMessages(List<Map<String, String>> messages) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (messages == null) {
            return out;
        }
        for (Map<String, String> m : messages) {
            out.add(new LinkedHashMap<>(m));
        }
        return out;
    }

    private JsonNode chatCompletionMessage(
            String useModel,
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            int timeoutSec,
            boolean useThinking
    ) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", useModel);
        body.put("messages", messages);
        body.put("stream", false);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }
        if (useThinking) {
            body.put("thinking", Map.of("type", "enabled"));
            if (reasoningEffort != null && !reasoningEffort.isBlank()) {
                body.put("reasoning_effort", reasoningEffort);
            }
        }
        String payload = objectMapper.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(chatApiRoot() + "/chat/completions"))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            String detail = res.body() == null ? "" : res.body();
            if (detail.length() > 240) {
                detail = detail.substring(0, 240) + "...";
            }
            throw new IllegalStateException("状态码 " + res.statusCode() + (detail.isBlank() ? "" : "：" + detail));
        }
        JsonNode root = objectMapper.readTree(res.body());
        return root.path("choices").path(0).path("message");
    }

    /**
     * DeepSeek 官方示例 base 为 https://api.deepseek.com，路径 /chat/completions。
     * 若填了 .../v1 也原样保留。
     */
    private String chatApiRoot() {
        String root = baseUrl;
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        return root;
    }

    private void requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 API Key，请设置环境变量 DEEPSEEK_API_KEY 或在后台填写");
        }
    }

    private String buildTaskAuditUserContent(DeliveryTask task) {
        String desc = task.getDescription() == null ? "" : task.getDescription();
        return "任务信息：\n"
                + "title=" + safe(task.getTitle()) + "\n"
                + "pickupLocation=" + safe(task.getPickupLocation()) + "\n"
                + "dropoffLocation=" + safe(task.getDropoffLocation()) + "\n"
                + "reward=" + (task.getReward() == null ? "" : task.getReward()) + "\n"
                + "description=" + desc + "\n";
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private List<String> filterSeriousViolations(List<String> rawViolations) {
        if (rawViolations == null || rawViolations.isEmpty()) {
            return List.of();
        }
        List<String> kept = new ArrayList<>();
        for (String v : rawViolations) {
            if (v == null || v.isBlank()) continue;
            if (isSeriousViolation(v)) {
                kept.add(v);
            }
        }
        return kept;
    }

    private boolean isSeriousViolation(String v) {
        String s = v.toLowerCase(Locale.ROOT);
        return v.contains("辱骂") ||
                v.contains("侮辱") ||
                v.contains("仇恨") ||
                v.contains("骂") ||
                v.contains("色情") ||
                v.contains("露骨") ||
                v.contains("性暗示") ||
                v.contains("性服务") ||
                v.contains("性") && (v.contains("服务") || v.contains("交易") || v.contains("引流") || v.contains("暗示")) ||
                v.contains("引流") ||
                v.contains("诈骗") ||
                v.contains("非法") ||
                v.contains("违法") ||
                v.contains("赌博") ||
                v.contains("暴力") ||
                v.contains("恐怖") ||
                v.contains("盗窃") ||
                v.contains("毒品") ||
                s.contains("porn") ||
                s.contains("sex") ||
                s.contains("violence") ||
                s.contains("hateful") ||
                s.contains("scam");
    }

    private String normalizeDecision(String raw) {
        if (raw == null) return "REJECT";
        String v = raw.trim().toUpperCase(Locale.ROOT);
        if ("APPROVE".equals(v) || "REJECT".equals(v)) return v;
        if (v.contains("APPROVE") || v.contains("通过") || v.contains("PASS") || v.contains("OK") || v.contains("SAFE") || v.contains("不违规") || v.contains("正常")) {
            return "APPROVE";
        }
        if (v.contains("REJECT") || v.contains("驳回") || v.contains("FAIL") || v.contains("NO") || v.contains("违规") || v.contains("色情") || v.contains("辱骂")) {
            return "REJECT";
        }
        return "APPROVE";
    }

    private JsonNode parseJsonFromContent(String content) throws IOException {
        if (content == null) {
            return objectMapper.createObjectNode();
        }
        String text = content.trim();
        if (text.isEmpty()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception ignore) {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return objectMapper.readTree(text.substring(start, end + 1));
            }
            return objectMapper.createObjectNode();
        }
    }

    private String normalizeBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("API 地址不能为空");
        }
        String v = raw.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        if (!v.startsWith("http://") && !v.startsWith("https://")) {
            throw new IllegalArgumentException("API 地址必须以 http:// 或 https:// 开头");
        }
        return v;
    }

    private String safeModel(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("模型名称不能为空");
        }
        return raw.trim();
    }

    private String maskApiKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    private int parseTimeout(String raw) {
        if (raw == null || raw.isBlank()) return 120;
        try {
            int n = Integer.parseInt(raw.trim());
            if (n < 3 || n > 300) return 120;
            return n;
        } catch (Exception ignore) {
            return 120;
        }
    }
}
