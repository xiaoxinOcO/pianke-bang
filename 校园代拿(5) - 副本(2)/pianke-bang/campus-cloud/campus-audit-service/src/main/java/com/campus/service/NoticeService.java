package com.campus.service;

import com.campus.dto.NoticeUpsertRequest;
import com.campus.model.Notice;
import com.campus.repository.NoticeRepository;
import com.campus.security.AdminPolicy;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final AdminLogService adminLogService;
    private final AdminPolicy adminPolicy;

    public NoticeService(NoticeRepository noticeRepository, AdminLogService adminLogService, AdminPolicy adminPolicy) {
        this.noticeRepository = noticeRepository;
        this.adminLogService = adminLogService;
        this.adminPolicy = adminPolicy;
    }

    private void requireAdmin(String operator) {
        if (operator == null || !adminPolicy.isAdmin(operator)) {
            throw new IllegalStateException("仅管理员可执行该操作");
        }
    }

    public List<Notice> publicNotices() {
        return noticeRepository.findByPublishedTrueOrderByPinnedDescUpdatedAtDescIdDesc();
    }

    public List<Notice> adminList(String operator) {
        requireAdmin(operator);
        return noticeRepository.findAll().stream()
                .sorted(
                        Comparator.comparing(Notice::isPinned).reversed()
                                .thenComparing(Notice::isPublished).reversed()
                                .thenComparing(Notice::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                                .thenComparing(Notice::getId, Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                )
                .toList();
    }

    @Transactional
    public Notice create(String operator, NoticeUpsertRequest request) {
        requireAdmin(operator);
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String title = request.getTitle() == null ? "" : request.getTitle().trim();
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (title.isBlank()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (title.length() > 80) {
            throw new IllegalArgumentException("标题不能超过80字");
        }
        if (content.isBlank()) {
            throw new IllegalArgumentException("内容不能为空");
        }
        if (content.length() > 5000) {
            throw new IllegalArgumentException("内容过长（最多5000字）");
        }

        Notice n = new Notice();
        n.setTitle(title);
        n.setContent(content);
        n.setPublished(request.isPublished());
        n.setPinned(request.isPinned());
        applyLink(n, request);
        n.setOperator(operator);
        Notice saved = noticeRepository.save(n);
        adminLogService.log(operator, "NOTICE_CREATE", null, "创建公告#" + saved.getId() + "：" + title);
        return saved;
    }

    @Transactional
    public Notice update(String operator, Long id, NoticeUpsertRequest request) {
        requireAdmin(operator);
        if (id == null) {
            throw new IllegalArgumentException("公告ID不能为空");
        }
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        Notice n = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("公告不存在"));

        String title = request.getTitle() == null ? "" : request.getTitle().trim();
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (title.isBlank()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (title.length() > 80) {
            throw new IllegalArgumentException("标题不能超过80字");
        }
        if (content.isBlank()) {
            throw new IllegalArgumentException("内容不能为空");
        }
        if (content.length() > 5000) {
            throw new IllegalArgumentException("内容过长（最多5000字）");
        }

        n.setTitle(title);
        n.setContent(content);
        n.setPublished(request.isPublished());
        n.setPinned(request.isPinned());
        applyLink(n, request);
        n.setOperator(operator);
        Notice saved = noticeRepository.save(n);
        adminLogService.log(operator, "NOTICE_UPDATE", null, "更新公告#" + saved.getId() + "：" + title);
        return saved;
    }

    private void applyLink(Notice n, NoticeUpsertRequest request) {
        boolean enabled = request.isLinkEnabled();
        if (!enabled) {
            n.setLinkEnabled(false);
            n.setLinkPath(null);
            return;
        }
        String path = request.getLinkPath() == null ? "" : request.getLinkPath().trim();
        if (path.isEmpty() || !path.startsWith("/")) {
            throw new IllegalArgumentException("启用链接时请选择跳转页面（路径需以 / 开头）");
        }
        if (path.length() > 200) {
            throw new IllegalArgumentException("跳转路径过长");
        }
        n.setLinkEnabled(true);
        n.setLinkPath(path);
    }

    @Transactional
    public Notice setPublish(String operator, Long id, boolean published) {
        requireAdmin(operator);
        Notice n = noticeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("公告不存在"));
        n.setPublished(published);
        n.setOperator(operator);
        Notice saved = noticeRepository.save(n);
        adminLogService.log(operator, published ? "NOTICE_PUBLISH" : "NOTICE_UNPUBLISH", null, (published ? "上架" : "下架") + "公告#" + id);
        return saved;
    }

    @Transactional
    public Notice setPinned(String operator, Long id, boolean pinned) {
        requireAdmin(operator);
        Notice n = noticeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("公告不存在"));
        n.setPinned(pinned);
        n.setOperator(operator);
        Notice saved = noticeRepository.save(n);
        adminLogService.log(operator, pinned ? "NOTICE_PIN" : "NOTICE_UNPIN", null, (pinned ? "置顶" : "取消置顶") + "公告#" + id);
        return saved;
    }

    @Transactional
    public void delete(String operator, Long id) {
        requireAdmin(operator);
        Notice n = noticeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("公告不存在"));
        noticeRepository.deleteById(id);
        adminLogService.log(operator, "NOTICE_DELETE", null, "删除公告#" + id + "：" + (n.getTitle() == null ? "" : n.getTitle()));
    }
}

