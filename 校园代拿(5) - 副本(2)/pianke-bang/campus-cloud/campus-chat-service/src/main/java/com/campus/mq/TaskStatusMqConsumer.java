package com.campus.mq;

import com.campus.service.RealtimePushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "campus.mq.enabled", havingValue = "true")
public class TaskStatusMqConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskStatusMqConsumer.class);

    private final RealtimePushService realtimePushService;

    public TaskStatusMqConsumer(RealtimePushService realtimePushService) {
        this.realtimePushService = realtimePushService;
    }

    @RabbitListener(queues = CampusMqConstants.QUEUE_CHAT_PUSH)
    public void onTaskStatus(TaskStatusMessage message) {
        if (message == null) {
            return;
        }
        log.info("[MQ 消费] 任务状态推送 -> queue={}, taskId={}, event={}, status={}, publisher={}, claimer={}",
                CampusMqConstants.QUEUE_CHAT_PUSH,
                message.getTaskId(), message.getEvent(), message.getStatus(),
                message.getPublisherName(), message.getClaimerName());
        Map<String, Object> payload = Map.of(
                "taskId", message.getTaskId() == null ? 0L : message.getTaskId(),
                "status", message.getStatus() == null ? "" : message.getStatus(),
                "title", message.getTitle() == null ? "" : message.getTitle(),
                "event", message.getEvent() == null ? "" : message.getEvent()
        );
        realtimePushService.sendToUser(message.getPublisherName(), "TASK_STATUS", payload);
        realtimePushService.sendToUser(message.getClaimerName(), "TASK_STATUS", payload);
    }
}
