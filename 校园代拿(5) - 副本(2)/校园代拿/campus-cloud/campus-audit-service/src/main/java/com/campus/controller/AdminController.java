package com.campus.controller;

import com.campus.dto.AdminAiConfigRequest;
import com.campus.dto.AdminAiTestRequest;
import com.campus.dto.AdminLogPageResponse;
import com.campus.dto.CsRatingPageResponse;
import com.campus.dto.NoticeUpsertRequest;
import com.campus.dto.SystemMessageCreateRequest;
import com.campus.dto.UserFeedbackDTO;
import com.campus.dto.UserFeedbackPageResponse;
import com.campus.model.Notice;
import com.campus.model.UserSystemMessage;
import com.campus.security.AdminPolicy;
import com.campus.security.AuthContext;
import com.campus.service.AdminLogService;
import com.campus.service.AdminAiService;
import com.campus.service.CsRatingService;
import com.campus.service.NoticeService;
import com.campus.service.UserFeedbackService;
import com.campus.service.UserSystemMessageService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminLogService adminLogService;
    private final AdminAiService adminAiService;
    private final NoticeService noticeService;
    private final UserFeedbackService userFeedbackService;
    private final UserSystemMessageService userSystemMessageService;
    private final CsRatingService csRatingService;
    private final AdminPolicy adminPolicy;

    public AdminController(
            AdminLogService adminLogService,
            AdminAiService adminAiService,
            NoticeService noticeService,
            UserFeedbackService userFeedbackService,
            UserSystemMessageService userSystemMessageService,
            CsRatingService csRatingService,
            AdminPolicy adminPolicy
    ) {
        this.adminLogService = adminLogService;
        this.adminAiService = adminAiService;
        this.noticeService = noticeService;
        this.userFeedbackService = userFeedbackService;
        this.userSystemMessageService = userSystemMessageService;
        this.csRatingService = csRatingService;
        this.adminPolicy = adminPolicy;
    }

    @GetMapping("/ai/config")
    public Map<String, Object> getAiConfig() {
        assertAdmin();
        Map<String, Object> data = adminAiService.getConfig();
        // 拉取模型列表失败时不阻断配置页
        data.put("models", adminAiService.listModels());
        return data;
    }

    @PostMapping("/ai/config")
    public Map<String, Object> updateAiConfig(@RequestBody AdminAiConfigRequest request) {
        assertAdmin();
        Map<String, Object> data = adminAiService.updateConfig(request);
        data.put("models", adminAiService.listModels());
        data.put("message", "AI 配置已更新（OpenAI 兼容 API）");
        return data;
    }

    @PostMapping("/ai/test")
    public Map<String, Object> testAi(@RequestBody(required = false) AdminAiTestRequest request) {
        assertAdmin();
        String prompt = request == null ? null : request.getPrompt();
        Map<String, Object> data = adminAiService.testChat(prompt);
        data.put("message", "测试成功");
        return data;
    }

    @GetMapping("/logs")
    public AdminLogPageResponse listLogs(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        String operator = AuthContext.getCurrentUser();
        if (operator == null || !adminPolicy.isAdmin(operator)) {
            throw new IllegalStateException("仅管理员可查看操作日志");
        }
        return adminLogService.listLogs(page, size);
    }

    @GetMapping("/notices")
    public List<Notice> listNotices() {
        return noticeService.adminList(AuthContext.getCurrentUser());
    }

    @PostMapping("/notices")
    public Notice createNotice(@RequestBody NoticeUpsertRequest request) {
        return noticeService.create(AuthContext.getCurrentUser(), request);
    }

    @PutMapping("/notices/{id}")
    public Notice updateNotice(@PathVariable("id") Long id, @RequestBody NoticeUpsertRequest request) {
        return noticeService.update(AuthContext.getCurrentUser(), id, request);
    }

    @PostMapping("/notices/{id}/publish")
    public Notice publish(@PathVariable("id") Long id, @RequestParam("value") boolean value) {
        return noticeService.setPublish(AuthContext.getCurrentUser(), id, value);
    }

    @PostMapping("/notices/{id}/pin")
    public Notice pin(@PathVariable("id") Long id, @RequestParam("value") boolean value) {
        return noticeService.setPinned(AuthContext.getCurrentUser(), id, value);
    }

    @DeleteMapping("/notices/{id}")
    public Map<String, Object> deleteNotice(@PathVariable("id") Long id) {
        noticeService.delete(AuthContext.getCurrentUser(), id);
        return Map.of("message", "删除成功");
    }

    @GetMapping("/feedback")
    public UserFeedbackPageResponse listFeedback(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "read", required = false) Boolean read
    ) {
        return userFeedbackService.listForAdmin(AuthContext.getCurrentUser(), page, size, type, read);
    }

    @PostMapping("/feedback/{id}/read")
    public UserFeedbackDTO markFeedbackRead(
            @PathVariable("id") Long id,
            @RequestParam(value = "value", defaultValue = "true") boolean value
    ) {
        return userFeedbackService.setReadFlag(AuthContext.getCurrentUser(), id, value);
    }

    @GetMapping("/cs-ratings")
    public CsRatingPageResponse listCsRatings(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "agent", required = false) String agent,
            @RequestParam(value = "scoreMin", required = false) Integer scoreMin,
            @RequestParam(value = "scoreMax", required = false) Integer scoreMax
    ) {
        return csRatingService.listForAdmin(
                AuthContext.getCurrentUser(),
                page,
                size,
                agent,
                scoreMin,
                scoreMax
        );
    }

    @PostMapping("/system-messages")
    public UserSystemMessage createSystemMessage(@RequestBody SystemMessageCreateRequest request) {
        assertAdmin();
        UserSystemMessage message = userSystemMessageService.create(request);
        if (message == null) {
            throw new IllegalStateException("系统消息已存在，未重复发送");
        }
        return message;
    }

    private void assertAdmin() {
        String operator = AuthContext.getCurrentUser();
        if (operator == null || !adminPolicy.isAdmin(operator)) {
            throw new IllegalStateException("仅管理员可执行该操作");
        }
    }
}
