package com.campus.service;

import com.campus.dto.SystemMessageCreateRequest;
import com.campus.dto.SystemMessageListResponse;
import com.campus.model.UserSystemMessage;
import com.campus.repository.UserSystemMessageRepository;
import com.campus.support.RemoteRealtimePushSupport;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class UserSystemMessageService {

    private final UserSystemMessageRepository repository;
    private final ObjectProvider<RemoteRealtimePushSupport> realtimePush;

    public UserSystemMessageService(
            UserSystemMessageRepository repository,
            ObjectProvider<RemoteRealtimePushSupport> realtimePush
    ) {
        this.repository = repository;
        this.realtimePush = realtimePush;
    }

    public SystemMessageListResponse listForUser(String username) {
        String user = normalizeUsername(username);
        List<UserSystemMessage> messages = repository.findTop100ByUsernameIgnoreCaseOrderByCreatedAtDescIdDesc(user);
        long unread = repository.countByUsernameIgnoreCaseAndReadFlagFalse(user);
        return new SystemMessageListResponse(messages, unread);
    }

    @Transactional
    public void markAllRead(String username) {
        String user = normalizeUsername(username);
        repository.markAllRead(user);
    }

    @Transactional
    public UserSystemMessage create(SystemMessageCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String user = normalizeUsername(request.getUsername());
        String title = request.getTitle() == null ? "" : request.getTitle().trim();
        String content = request.getContent() == null ? "" : request.getContent().trim();
        String category = request.getCategory() == null ? "" : request.getCategory().trim();
        String eventType = request.getEventType() == null ? "" : request.getEventType().trim();
        if (title.isBlank() || content.isBlank() || category.isBlank() || eventType.isBlank()) {
            throw new IllegalArgumentException("系统消息字段不完整");
        }
        if (request.getRefId() != null
                && repository.existsByUsernameIgnoreCaseAndEventTypeAndRefId(user, eventType, request.getRefId())) {
            return null;
        }

        UserSystemMessage message = new UserSystemMessage();
        message.setUsername(user);
        message.setTitle(title.length() > 80 ? title.substring(0, 80) : title);
        message.setContent(content);
        message.setCategory(category);
        message.setEventType(eventType);
        message.setRefId(request.getRefId());
        message.setReadFlag(false);
        UserSystemMessage saved = repository.save(message);

        RemoteRealtimePushSupport push = realtimePush.getIfAvailable();
        if (push != null) {
            push.sendToUser(user, "SYSTEM_MESSAGE", Map.of(
                    "id", saved.getId(),
                    "title", saved.getTitle(),
                    "eventType", saved.getEventType()
            ));
        }
        return saved;
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        return username.trim();
    }
}
