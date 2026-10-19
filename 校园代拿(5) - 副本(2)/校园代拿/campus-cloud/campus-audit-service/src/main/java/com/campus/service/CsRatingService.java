package com.campus.service;

import com.campus.dto.CsRatingDTO;
import com.campus.dto.CsRatingPageResponse;
import com.campus.dto.CsRatingSubmitRequest;
import com.campus.model.CsRating;
import com.campus.model.UserAccount;
import com.campus.repository.CsRatingRepository;
import com.campus.repository.UserAccountRepository;
import com.campus.security.AdminPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class CsRatingService {

    private static final int MAX_COMMENT = 200;
    private static final int MAX_TAGS = 5;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_TAGS = Set.of(
            "解决了问题", "态度很好", "回复及时", "专业耐心", "有待改进"
    );

    private final CsRatingRepository repository;
    private final UserAccountRepository userAccountRepository;
    private final AdminPolicy adminPolicy;

    public CsRatingService(
            CsRatingRepository repository,
            UserAccountRepository userAccountRepository,
            AdminPolicy adminPolicy
    ) {
        this.repository = repository;
        this.userAccountRepository = userAccountRepository;
        this.adminPolicy = adminPolicy;
    }

    @Transactional
    public CsRatingDTO submit(String fromUsername, CsRatingSubmitRequest request) {
        if (fromUsername == null || fromUsername.isBlank()) {
            throw new IllegalStateException("请先登录后再评价");
        }
        if (request == null) {
            throw new IllegalArgumentException("评价内容不能为空");
        }
        String agent = request.getAgentUsername() == null ? "" : request.getAgentUsername().trim();
        if (agent.isEmpty()) {
            throw new IllegalArgumentException("缺少客服账号");
        }
        if (agent.equalsIgnoreCase(fromUsername.trim())) {
            throw new IllegalArgumentException("不能评价自己");
        }

        UserAccount agentAccount = userAccountRepository.findByUsernameIgnoreCase(agent)
                .orElseThrow(() -> new IllegalArgumentException("客服账号不存在"));
        if (!agentAccount.isCustomerServiceStaff()) {
            throw new IllegalArgumentException("只能评价人工客服账号");
        }

        int score = request.getScore() == null ? 0 : request.getScore();
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("评分需为 1～5 星");
        }

        List<String> tags = sanitizeTags(request.getTags());
        String comment = request.getComment() == null ? "" : request.getComment().trim();
        if (comment.length() > MAX_COMMENT) {
            throw new IllegalArgumentException("评价留言不能超过 " + MAX_COMMENT + " 字");
        }

        CsRating rating = new CsRating();
        rating.setFromUsername(fromUsername.trim());
        rating.setAgentUsername(agentAccount.getUsername());
        rating.setScore(score);
        rating.setTags(tags.isEmpty() ? null : String.join(",", tags));
        rating.setCommentText(comment.isEmpty() ? null : comment);

        return toDto(repository.save(rating));
    }

    public CsRatingPageResponse listForAdmin(
            String operator,
            int page,
            int size,
            String agentFilter,
            Integer scoreMin,
            Integer scoreMax
    ) {
        assertAdmin(operator);
        return search(page, size, normalizeAgent(agentFilter), scoreMin, scoreMax);
    }

    public CsRatingPageResponse listForAgent(String agentUsername, int page, int size) {
        if (agentUsername == null || agentUsername.isBlank()) {
            throw new IllegalStateException("请先登录");
        }
        UserAccount me = userAccountRepository.findByUsernameIgnoreCase(agentUsername.trim())
                .orElseThrow(() -> new IllegalStateException("账号不存在"));
        if (!me.isCustomerServiceStaff()) {
            throw new IllegalStateException("仅人工客服可查看本人评价");
        }
        return search(page, size, me.getUsername(), null, null);
    }

    private CsRatingPageResponse search(
            int page,
            int size,
            String agent,
            Integer scoreMin,
            Integer scoreMax
    ) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Integer min = normalizeScoreBound(scoreMin);
        Integer max = normalizeScoreBound(scoreMax);
        if (min != null && max != null && min > max) {
            int tmp = min;
            min = max;
            max = tmp;
        }

        Page<CsRating> result = repository.search(agent, min, max, PageRequest.of(safePage - 1, safeSize));
        List<CsRatingDTO> records = result.getContent().stream().map(this::toDto).toList();
        Double avg = repository.averageScore(agent, min, max);
        double average = avg == null ? 0D : Math.round(avg * 10.0) / 10.0;
        return new CsRatingPageResponse(
                records,
                safePage,
                safeSize,
                result.getTotalElements(),
                Math.max(1, result.getTotalPages()),
                average,
                result.getTotalElements()
        );
    }

    private void assertAdmin(String operator) {
        if (operator == null || !adminPolicy.isAdmin(operator)) {
            throw new IllegalStateException("仅管理员可查看客服评价");
        }
    }

    private String normalizeAgent(String agent) {
        if (agent == null) return null;
        String t = agent.trim();
        return t.isEmpty() ? null : t;
    }

    private Integer normalizeScoreBound(Integer score) {
        if (score == null) return null;
        if (score < 1 || score > 5) return null;
        return score;
    }

    private List<String> sanitizeTags(List<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String item : raw) {
            if (item == null) continue;
            String tag = item.trim();
            if (tag.isEmpty()) continue;
            if (!ALLOWED_TAGS.contains(tag)) continue;
            out.add(tag);
            if (out.size() >= MAX_TAGS) break;
        }
        return new ArrayList<>(out);
    }

    private CsRatingDTO toDto(CsRating rating) {
        return new CsRatingDTO(
                rating.getId(),
                rating.getFromUsername(),
                rating.getAgentUsername(),
                rating.getScore(),
                parseTags(rating.getTags()),
                rating.getCommentText() == null ? "" : rating.getCommentText(),
                rating.getCreatedAt()
        );
    }

    private List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
