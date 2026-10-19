package com.campus.service;

import com.campus.dto.UserFeedbackDTO;
import com.campus.dto.UserFeedbackPageResponse;
import com.campus.dto.UserFeedbackSubmitRequest;
import com.campus.model.UserFeedback;
import com.campus.repository.UserFeedbackRepository;
import com.campus.security.AdminPolicy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class UserFeedbackService {

    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int MAX_IMAGES = 5;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserFeedbackRepository repository;
    private final AdminPolicy adminPolicy;
    private final ObjectMapper objectMapper;

    public UserFeedbackService(
            UserFeedbackRepository repository,
            AdminPolicy adminPolicy,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.adminPolicy = adminPolicy;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UserFeedbackDTO submit(String username, UserFeedbackSubmitRequest request) {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("请先登录后再提交反馈");
        }
        if (request == null) {
            throw new IllegalArgumentException("反馈内容不能为空");
        }

        String type = normalizeType(request.getType());
        String page = normalizePage(request.getPage());
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("请填写反馈内容");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("反馈内容不能超过 " + MAX_CONTENT_LENGTH + " 字");
        }

        List<String> images = sanitizeImages(request.getImages());

        UserFeedback feedback = new UserFeedback();
        feedback.setUsername(username.trim());
        feedback.setFeedbackType(type);
        feedback.setPageName(page);
        feedback.setContent(content);
        feedback.setImagesJson(writeImagesJson(images));
        feedback.setReadFlag(false);

        UserFeedback saved = repository.save(feedback);
        return toDto(saved);
    }

    public UserFeedbackPageResponse listForAdmin(
            String operator,
            int page,
            int size,
            String typeFilter,
            Boolean readFilter
    ) {
        assertAdmin(operator);

        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        String type = normalizeTypeFilter(typeFilter);
        Boolean readFlag = readFilter;

        Page<UserFeedback> result = repository.search(
                type,
                readFlag,
                PageRequest.of(safePage - 1, safeSize)
        );

        List<UserFeedbackDTO> records = result.getContent().stream().map(this::toDto).toList();
        long unreadCount = repository.countByReadFlagFalse();
        return new UserFeedbackPageResponse(
                records,
                safePage,
                safeSize,
                result.getTotalElements(),
                Math.max(1, result.getTotalPages()),
                unreadCount
        );
    }

    @Transactional
    public UserFeedbackDTO setReadFlag(String operator, Long id, boolean readFlag) {
        assertAdmin(operator);
        UserFeedback feedback = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("反馈不存在"));
        feedback.setReadFlag(readFlag);
        return toDto(repository.save(feedback));
    }

    private void assertAdmin(String operator) {
        if (operator == null || !adminPolicy.isAdmin(operator)) {
            throw new IllegalStateException("仅管理员可查看用户反馈");
        }
    }

    private String normalizeType(String raw) {
        String type = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if ("bug".equals(type)) {
            return "bug";
        }
        return "suggest";
    }

    private String normalizeTypeFilter(String raw) {
        if (raw == null || raw.isBlank() || "all".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        return normalizeType(raw);
    }

    private String normalizePage(String raw) {
        String page = raw == null ? "" : raw.trim();
        if (page.isEmpty()) {
            return "其它";
        }
        return page.length() > 40 ? page.substring(0, 40) : page;
    }

    private List<String> sanitizeImages(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String image : raw) {
            if (image == null) {
                continue;
            }
            String trimmed = image.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            out.add(trimmed);
            if (out.size() >= MAX_IMAGES) {
                break;
            }
        }
        return out;
    }

    private String writeImagesJson(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception ex) {
            throw new IllegalArgumentException("截图数据无效");
        }
    }

    private List<String> readImagesJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private UserFeedbackDTO toDto(UserFeedback feedback) {
        return new UserFeedbackDTO(
                feedback.getId(),
                feedback.getUsername(),
                feedback.getFeedbackType(),
                feedback.getPageName(),
                feedback.getContent(),
                readImagesJson(feedback.getImagesJson()),
                feedback.isReadFlag(),
                feedback.getCreatedAt()
        );
    }
}
