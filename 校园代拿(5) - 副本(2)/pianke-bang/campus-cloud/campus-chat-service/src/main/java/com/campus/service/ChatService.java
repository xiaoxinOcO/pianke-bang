package com.campus.service;

import com.campus.dto.ChatContactDTO;
import com.campus.dto.ChatReadMarkRequest;
import com.campus.dto.ChatSendRequest;
import com.campus.model.ChatMessage;
import com.campus.model.ChatReadCursor;
import com.campus.model.DeliveryTask;
import com.campus.model.TaskStatus;
import com.campus.model.UserAccount;
import com.campus.repository.ChatMessageRepository;
import com.campus.repository.ChatReadCursorRepository;
import com.campus.repository.DeliveryTaskRepository;
import com.campus.repository.UserAccountRepository;
import com.campus.support.OssImageStore;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatReadCursorRepository chatReadCursorRepository;
    private final DeliveryTaskRepository deliveryTaskRepository;
    private final UserAccountRepository userAccountRepository;
    private final RealtimePushService realtimePushService;
    private final OssImageStore ossImageStore;

    public ChatService(
            ChatMessageRepository chatMessageRepository,
            ChatReadCursorRepository chatReadCursorRepository,
            DeliveryTaskRepository deliveryTaskRepository,
            UserAccountRepository userAccountRepository,
            RealtimePushService realtimePushService,
            OssImageStore ossImageStore
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatReadCursorRepository = chatReadCursorRepository;
        this.deliveryTaskRepository = deliveryTaskRepository;
        this.userAccountRepository = userAccountRepository;
        this.realtimePushService = realtimePushService;
        this.ossImageStore = ossImageStore;
    }

    @Transactional
    public ChatMessage sendMessage(String currentUser, Long currentUserId, ChatSendRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }

        UserAccount meUser = resolveCurrentUser(currentUser, currentUserId);
        String me = meUser.getUsername();
        String to = request.getToUsername() == null ? "" : request.getToUsername().trim();
        String content = request.getContent() == null ? "" : request.getContent().trim();

        if (to.isBlank()) {
            throw new IllegalArgumentException("接收方不能为空");
        }
        if (to.equalsIgnoreCase(me)) {
            throw new IllegalArgumentException("不能给自己发消息");
        }
        if (content.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }

        boolean linkedByTask = canTemporaryChat(me, to);
        boolean hasHistory = hasChatHistory(me, to);

        if (!linkedByTask && !hasHistory && !canInitiatePrivateChat(meUser, to)) {
            throw new IllegalStateException("仅已认证接单员可主动发起私聊");
        }

        ChatMessage msg = new ChatMessage();
        msg.setFromUsername(me);
        msg.setToUsername(to);
        msg.setContent(ossImageStore.rewriteChatContent(content));
        ChatMessage saved = chatMessageRepository.save(msg);

        // 实时推送给接收方（以及自己用于多端同步）
        realtimePushService.sendToUser(to, "CHAT_MESSAGE", saved);
        realtimePushService.sendToUser(me, "CHAT_MESSAGE", saved);

        return saved;
    }

    public List<ChatMessage> history(String currentUser, Long currentUserId, String targetUser) {
        String me = resolveCurrentUser(currentUser, currentUserId).getUsername();
        String target = targetUser == null ? "" : targetUser.trim();
        if (target.isBlank()) {
            throw new IllegalArgumentException("请选择聊天对象");
        }

        return chatMessageRepository
                .findByFromUsernameAndToUsernameOrFromUsernameAndToUsernameOrderByCreatedAtAsc(
                        me, target,
                        target, me
                );
    }

    public List<ChatContactDTO> contacts(String currentUser, Long currentUserId) {
        String me = resolveCurrentUser(currentUser, currentUserId).getUsername();
        List<ChatMessage> recent = chatMessageRepository.findByFromUsernameOrToUsernameOrderByCreatedAtDesc(me, me);

        LinkedHashSet<String> contactNames = new LinkedHashSet<>();
        for (ChatMessage msg : recent) {
            if (me.equalsIgnoreCase(msg.getFromUsername()) && msg.getToUsername() != null) {
                contactNames.add(msg.getToUsername());
            } else if (msg.getFromUsername() != null) {
                contactNames.add(msg.getFromUsername());
            }
        }

        contactNames.removeIf(name -> name == null || name.isBlank() || me.equalsIgnoreCase(name));

        if (contactNames.isEmpty()) {
            return List.of();
        }

        var users = userAccountRepository.findByUsernameIn(contactNames.stream().toList());
        java.util.Map<String, String> avatarMap = new java.util.HashMap<>();
        java.util.Map<String, Boolean> riderMap = new java.util.HashMap<>();
        java.util.Map<String, Boolean> csMap = new java.util.HashMap<>();
        for (UserAccount u : users) {
            if (u.getUsername() == null || u.getUsername().isBlank()) {
                continue;
            }
            avatarMap.putIfAbsent(u.getUsername(), u.resolveDisplayAvatarUrl());
            riderMap.putIfAbsent(u.getUsername(), u.isRiderApproved());
            csMap.putIfAbsent(u.getUsername(), u.isCustomerServiceStaff());
        }

        return contactNames.stream()
                .map(name -> new ChatContactDTO(
                        name,
                        avatarMap.getOrDefault(name, ""),
                        riderMap.getOrDefault(name, false),
                        csMap.getOrDefault(name, false)
                ))
                .toList();
    }

    public List<String> temporaryContacts(String currentUser, Long currentUserId) {
        String me = resolveCurrentUser(currentUser, currentUserId).getUsername();
        List<DeliveryTask> links = deliveryTaskRepository.findTaskLinksForUser(me);
        LinkedHashSet<String> result = new LinkedHashSet<>();

        for (DeliveryTask t : links) {
            if (t.getStatus() != TaskStatus.CLAIMED && t.getStatus() != TaskStatus.PENDING_CONFIRMATION) {
                continue;
            }
            if (me.equalsIgnoreCase(t.getPublisherName()) && t.getClaimerName() != null && !t.getClaimerName().isBlank()) {
                result.add(t.getClaimerName());
            } else if (me.equalsIgnoreCase(t.getClaimerName()) && t.getPublisherName() != null && !t.getPublisherName().isBlank()) {
                result.add(t.getPublisherName());
            }
        }

        result.removeIf(name -> name == null || name.isBlank() || me.equalsIgnoreCase(name));
        return result.stream().toList();
    }

    /**
     * 标记当前用户已读某会话（游标取会话内最大消息 id，单调递增）。
     */
    @Transactional
    public Map<String, Object> markRead(String currentUser, Long currentUserId, ChatReadMarkRequest request) {
        String me = resolveCurrentUser(currentUser, currentUserId).getUsername();
        String target = request == null || request.getTargetUser() == null
                ? ""
                : request.getTargetUser().trim();
        if (target.isBlank()) {
            throw new IllegalArgumentException("请选择聊天对象");
        }
        if (target.equalsIgnoreCase(me)) {
            throw new IllegalArgumentException("不能标记自己");
        }

        Long conversationMax = chatMessageRepository.findMaxIdBetween(me, target);
        long maxId = conversationMax == null ? 0L : conversationMax;
        Long requested = request.getLastReadMessageId();
        if (requested != null && requested > 0) {
            maxId = Math.min(maxId, requested);
        }

        ChatReadCursor cursor = chatReadCursorRepository
                .findByReaderUsernameIgnoreCaseAndPeerUsernameIgnoreCase(me, target)
                .orElseGet(ChatReadCursor::new);
        long previous = cursor.getLastReadMessageId() == null ? 0L : cursor.getLastReadMessageId();
        long next = Math.max(previous, maxId);

        if (cursor.getId() == null) {
            cursor.setReaderUsername(me);
            cursor.setPeerUsername(target);
        }
        cursor.setLastReadMessageId(next);
        chatReadCursorRepository.save(cursor);

        if (next > previous) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("peerUsername", me);
            payload.put("lastReadMessageId", next);
            realtimePushService.sendToUser(target, "CHAT_READ", payload);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetUser", target);
        result.put("lastReadMessageId", next);
        return result;
    }

    /** 查询对方对我方消息的已读游标 */
    public Map<String, Object> peerReadState(String currentUser, Long currentUserId, String targetUser) {
        String me = resolveCurrentUser(currentUser, currentUserId).getUsername();
        String target = targetUser == null ? "" : targetUser.trim();
        if (target.isBlank()) {
            throw new IllegalArgumentException("请选择聊天对象");
        }

        Long lastRead = chatReadCursorRepository
                .findByReaderUsernameIgnoreCaseAndPeerUsernameIgnoreCase(target, me)
                .map(ChatReadCursor::getLastReadMessageId)
                .orElse(0L);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetUser", target);
        result.put("peerLastReadMessageId", lastRead == null ? 0L : lastRead);
        return result;
    }

    private boolean canTemporaryChat(String userA, String userB) {
        List<DeliveryTask> links = deliveryTaskRepository.findTaskLinksBetweenUsers(userA, userB);
        return links.stream().anyMatch(t -> t.getStatus() == TaskStatus.CLAIMED || t.getStatus() == TaskStatus.PENDING_CONFIRMATION);
    }

    private boolean hasChatHistory(String userA, String userB) {
        return !chatMessageRepository
                .findByFromUsernameAndToUsernameOrFromUsernameAndToUsernameOrderByCreatedAtAsc(userA, userB, userB, userA)
                .isEmpty();
    }

    /** 接单员、人工客服，或向人工客服咨询的普通用户，均可发起会话 */
    private boolean canInitiatePrivateChat(UserAccount meUser, String to) {
        if (meUser.isRiderApproved() || meUser.isCustomerServiceStaff()) {
            return true;
        }
        return userAccountRepository.findByUsername(to)
                .map(UserAccount::isCustomerServiceStaff)
                .orElse(false);
    }

    private UserAccount resolveCurrentUser(String currentUser, Long currentUserId) {
        if (currentUserId != null) {
            var byId = userAccountRepository.findById(currentUserId);
            if (byId.isPresent()) {
                return byId.get();
            }
        }
        if (currentUser == null || currentUser.isBlank()) {
            throw new IllegalArgumentException("未登录");
        }
        String principal = currentUser.trim();
        return userAccountRepository.findByUsername(principal)
                .or(() -> userAccountRepository.findByUsernameIgnoreCase(principal))
                .orElseThrow(() -> new IllegalArgumentException("当前用户不存在"));
    }
}
