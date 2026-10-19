package com.campus.support;

import com.campus.client.SystemMessageClient;
import com.campus.dto.SystemMessageCreateRequest;
import com.campus.model.DeliveryTask;
import com.campus.mq.TaskStatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "campus.remote.system-message", havingValue = "true")
public class RemoteSystemMessageSupport {

    private static final Logger log = LoggerFactory.getLogger(RemoteSystemMessageSupport.class);

    private final SystemMessageClient systemMessageClient;

    public RemoteSystemMessageSupport(SystemMessageClient systemMessageClient) {
        this.systemMessageClient = systemMessageClient;
    }

    public void notifyTaskStatusChanged(TaskStatusMessage message) {
        if (message == null) {
            return;
        }
        notifyUser(message.getPublisherName(), UserSystemMessageTexts.forTaskEvent(message.getPublisherName(), message));
        notifyUser(message.getClaimerName(), UserSystemMessageTexts.forTaskEvent(message.getClaimerName(), message));
    }

    public void notifyTaskSubmitted(DeliveryTask task) {
        if (task == null || task.getPublisherName() == null) {
            return;
        }
        notifyUser(task.getPublisherName(), UserSystemMessageTexts.forTaskSubmitted(task));
    }

    public void notifyAlipayRecharge(String username, BigDecimal amount, String outTradeNo) {
        notifyUser(username, Optional.of(UserSystemMessageTexts.forAlipayRecharge(username, amount, outTradeNo)));
    }

    public void notifyAdminRecharge(String username, BigDecimal amount, String remark) {
        notifyUser(username, Optional.of(UserSystemMessageTexts.forAdminRecharge(username, amount, remark)));
    }

    public void notifyWithdrawApply(String username, BigDecimal amount, Long requestId) {
        notifyUser(username, Optional.of(UserSystemMessageTexts.forWithdrawApply(username, amount, requestId)));
    }

    public void notifyWithdrawApproved(String username, BigDecimal amount, Long requestId, boolean alipaySandbox) {
        notifyUser(username, Optional.of(UserSystemMessageTexts.forWithdrawApproved(username, amount, requestId, alipaySandbox)));
    }

    public void notifyWithdrawRejected(String username, BigDecimal amount, Long requestId, String remark) {
        notifyUser(username, Optional.of(UserSystemMessageTexts.forWithdrawRejected(username, amount, requestId, remark)));
    }

    public void notifyTaskIncome(String username, BigDecimal amount, Long taskId, String title) {
        notifyUser(username, Optional.of(UserSystemMessageTexts.forTaskIncome(username, amount, taskId, title)));
    }

    public void notifyBountyPaid(String username, BigDecimal amount, Long taskId, String title) {
        notifyUser(username, Optional.of(UserSystemMessageTexts.forBountyLock(username, amount, taskId, title)));
    }

    private void notifyUser(String username, Optional<UserSystemMessageTexts.MessageDraft> draftOpt) {
        if (username == null || username.isBlank() || draftOpt == null || draftOpt.isEmpty()) {
            return;
        }
        UserSystemMessageTexts.MessageDraft draft = draftOpt.get();
        try {
            SystemMessageCreateRequest request = new SystemMessageCreateRequest();
            request.setUsername(username.trim());
            request.setTitle(draft.title());
            request.setContent(draft.content());
            request.setCategory(draft.category());
            request.setEventType(draft.eventType());
            request.setRefId(draft.refId());
            systemMessageClient.create(request);
        } catch (Exception ex) {
            log.warn("系统消息写入失败: username={}, eventType={}, reason={}",
                    username, draft.eventType(), ex.getMessage());
        }
    }
}
