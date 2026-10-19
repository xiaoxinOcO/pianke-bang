package com.campus.support;

import com.campus.config.CampusMqProperties;
import com.campus.model.DeliveryTask;
import com.campus.mq.CampusMqPublisher;
import com.campus.mq.TaskStatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "campus.remote.realtime", havingValue = "true")
public class TaskNotificationSupport {

    private static final Logger log = LoggerFactory.getLogger(TaskNotificationSupport.class);

    private final ObjectProvider<CampusMqPublisher> mqPublisher;
    private final RemoteRealtimePushSupport feignPush;
    private final ObjectProvider<RemoteSystemMessageSupport> systemMessageSupport;
    private final CampusMqProperties mqProperties;

    public TaskNotificationSupport(
            ObjectProvider<CampusMqPublisher> mqPublisher,
            RemoteRealtimePushSupport feignPush,
            ObjectProvider<RemoteSystemMessageSupport> systemMessageSupport,
            CampusMqProperties mqProperties
    ) {
        this.mqPublisher = mqPublisher;
        this.feignPush = feignPush;
        this.systemMessageSupport = systemMessageSupport;
        this.mqProperties = mqProperties;
    }

    public void notifyTaskStatusChanged(DeliveryTask task, String eventType) {
        if (task == null) {
            return;
        }

        TaskStatusMessage message = new TaskStatusMessage(
                task.getId(),
                task.getStatus() == null ? "" : String.valueOf(task.getStatus()),
                task.getTitle() == null ? "" : task.getTitle(),
                eventType,
                task.getPublisherName(),
                task.getClaimerName()
        );

        RemoteSystemMessageSupport systemMessages = systemMessageSupport.getIfAvailable();
        if (systemMessages != null) {
            systemMessages.notifyTaskStatusChanged(message);
        }

        CampusMqPublisher publisher = mqPublisher.getIfAvailable();
        if (publisher != null && mqProperties.isRealtime()) {
            try {
                if (publisher.publishTaskStatus(message)) {
                    return;
                }
            } catch (Exception ex) {
                log.warn("MQ 推送任务状态失败，回退 Feign: taskId={}, reason={}", task.getId(), ex.getMessage());
            }
        }

        Map<String, Object> payload = Map.of(
                "taskId", task.getId(),
                "status", task.getStatus() == null ? "" : String.valueOf(task.getStatus()),
                "title", task.getTitle() == null ? "" : task.getTitle(),
                "event", eventType
        );
        log.info("[Feign 调用] 任务状态推送 -> taskId={}, event={}", task.getId(), eventType);
        feignPush.sendToUser(task.getPublisherName(), "TASK_STATUS", payload);
        feignPush.sendToUser(task.getClaimerName(), "TASK_STATUS", payload);
    }
}
