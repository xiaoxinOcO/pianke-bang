package com.campus.controller;

import com.campus.dto.CsRatingDTO;
import com.campus.dto.CsRatingPageResponse;
import com.campus.dto.CsRatingSubmitRequest;
import com.campus.dto.CustomerSupportChatRequest;
import com.campus.security.AuthContext;
import com.campus.service.AdminAiService;
import com.campus.service.CsDeskContextService;
import com.campus.service.CsRatingService;
import com.campus.service.CustomerServiceStaffService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/support")
public class CustomerSupportController {

    private final AdminAiService adminAiService;
    private final CustomerServiceStaffService customerServiceStaffService;
    private final CsRatingService csRatingService;
    private final CsDeskContextService csDeskContextService;

    public CustomerSupportController(
            AdminAiService adminAiService,
            CustomerServiceStaffService customerServiceStaffService,
            CsRatingService csRatingService,
            CsDeskContextService csDeskContextService
    ) {
        this.adminAiService = adminAiService;
        this.customerServiceStaffService = customerServiceStaffService;
        this.csRatingService = csRatingService;
        this.csDeskContextService = csDeskContextService;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody CustomerSupportChatRequest request) {
        if (AuthContext.getCurrentUser() == null || AuthContext.getCurrentUser().isBlank()) {
            throw new IllegalStateException("请先登录后再使用智能客服");
        }
        Map<String, Object> data = adminAiService.customerSupportChat(request);
        data.put("message", "ok");
        return data;
    }

    @GetMapping("/assign-agent")
    public Map<String, Object> assignAgent() {
        return customerServiceStaffService.assignAgentForUser(AuthContext.getCurrentUser());
    }

    @PostMapping("/ratings")
    public Map<String, Object> submitRating(@RequestBody CsRatingSubmitRequest request) {
        CsRatingDTO saved = csRatingService.submit(AuthContext.getCurrentUser(), request);
        return Map.of(
                "message", "评价已提交，感谢反馈",
                "id", saved.getId(),
                "rating", saved
        );
    }

    @GetMapping("/ratings/mine")
    public CsRatingPageResponse myRatings(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return csRatingService.listForAgent(AuthContext.getCurrentUser(), page, size);
    }

    /** 人工客服工作台：按用户名只读查询钱包与最近任务 */
    @GetMapping("/cs/user-context")
    public Map<String, Object> csUserContext(@RequestParam("username") String username) {
        return csDeskContextService.userContext(AuthContext.getCurrentUser(), username);
    }
}
