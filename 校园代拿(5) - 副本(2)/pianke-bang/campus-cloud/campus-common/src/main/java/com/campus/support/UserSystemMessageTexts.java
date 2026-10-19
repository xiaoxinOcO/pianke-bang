package com.campus.support;

import com.campus.model.DeliveryTask;
import com.campus.mq.TaskStatusMessage;

import java.math.BigDecimal;
import java.util.Optional;

public final class UserSystemMessageTexts {

    private UserSystemMessageTexts() {
    }

    public record MessageDraft(String title, String content, String category, String eventType, Long refId) {
    }

    public static Optional<MessageDraft> forTaskEvent(String targetUsername, TaskStatusMessage message) {
        if (targetUsername == null || targetUsername.isBlank() || message == null) {
            return Optional.empty();
        }
        String user = targetUsername.trim();
        String publisher = normalize(message.getPublisherName());
        String claimer = normalize(message.getClaimerName());
        String event = normalizeUpper(message.getEvent());
        String status = normalizeUpper(message.getStatus());
        String bookTitle = bookTitle(message.getTitle());
        Long taskId = message.getTaskId();

        boolean isPublisher = !publisher.isBlank() && publisher.equalsIgnoreCase(user);
        boolean isClaimer = !claimer.isBlank() && claimer.equalsIgnoreCase(user);

        return switch (event) {
            case "TASK_SUBMITTED" -> isPublisher
                    ? draft("任务提交成功", "你的任务" + bookTitle + "已提交，请尽快完成赏金支付并等待审核", "TASK", event, taskId)
                    : Optional.empty();
            case "TASK_RESUBMITTED" -> isPublisher
                    ? draft("任务已重新提交", "你的任务" + bookTitle + "已重新提交，等待管理员审核", "TASK", event, taskId)
                    : Optional.empty();
            case "TASK_APPROVED" -> isPublisher
                    ? draft("任务审核通过", "你的任务" + bookTitle + "已审核通过，现已出现在任务大厅", "TASK", event, taskId)
                    : Optional.empty();
            case "TASK_REJECTED" -> isPublisher
                    ? draft("任务审核未通过", "你的任务" + bookTitle + "未通过审核，请查看驳回原因后修改", "TASK", event, taskId)
                    : Optional.empty();
            case "TASK_CLAIMED" -> {
                if (isPublisher) {
                    yield draft("任务已被接单", "你的任务" + bookTitle + "已被接单", "TASK", event, taskId);
                }
                if (isClaimer) {
                    yield draft("接单成功", "你已成功接单" + bookTitle, "TASK", event, taskId);
                }
                yield Optional.empty();
            }
            case "TASK_PENDING_CONFIRMATION" -> {
                if (isPublisher) {
                    yield draft("待确认完成", "任务" + bookTitle + "待你确认完成", "TASK", event, taskId);
                }
                if (isClaimer) {
                    yield draft("等待确认完成", "任务" + bookTitle + "已送达，等待发布者确认完成", "TASK", event, taskId);
                }
                yield Optional.empty();
            }
            case "TASK_COMPLETED", "TASK_AUTO_CONFIRMED" -> {
                if (isPublisher) {
                    yield draft("任务已完成", "任务" + bookTitle + "已完成", "TASK", event, taskId);
                }
                if (isClaimer) {
                    yield draft("任务已完成", "任务" + bookTitle + "已完成，酬劳将入账至你的钱包", "TASK", event, taskId);
                }
                yield Optional.empty();
            }
            case "TASK_CANCELLED" -> {
                if (isPublisher) {
                    yield draft("任务已取消", "你的任务" + bookTitle + "已取消", "TASK", event, taskId);
                }
                if (isClaimer) {
                    yield draft("任务已取消", "你参与的任务" + bookTitle + "已取消", "TASK", event, taskId);
                }
                yield Optional.empty();
            }
            case "APPEAL_APPROVED", "APPEAL_APPROVED_NO_PAY" -> isPublisher
                    ? draft("申诉已通过", "你对任务" + bookTitle + "的申诉已通过", "TASK", event, taskId)
                    : Optional.empty();
            case "APPEAL_REJECTED" -> isPublisher
                    ? draft("申诉未通过", "你对任务" + bookTitle + "的申诉未通过", "TASK", event, taskId)
                    : Optional.empty();
            case "TASK_REOPENED" -> isClaimer
                    ? draft("任务已重新开放", "任务" + bookTitle + "已重新开放接单", "TASK", event, taskId)
                    : Optional.empty();
            default -> {
                if (!isPublisher && !isClaimer) {
                    yield Optional.empty();
                }
                String cnStatus = taskStatusLabel(status);
                yield draft("任务状态更新", "任务" + bookTitle + "状态更新为：" + cnStatus, "TASK",
                        event.isBlank() ? status : event, taskId);
            }
        };
    }

    public static Optional<MessageDraft> forTaskSubmitted(DeliveryTask task) {
        if (task == null || task.getPublisherName() == null || task.getPublisherName().isBlank()) {
            return Optional.empty();
        }
        String bookTitle = bookTitle(task.getTitle());
        boolean prepaid = Boolean.TRUE.equals(task.getBountyPrepaid());
        String content = prepaid
                ? "你的任务" + bookTitle + "已提交，等待管理员审核"
                : "你的任务" + bookTitle + "已提交，请尽快完成赏金支付";
        return optionalDraft("任务提交成功", content, "TASK", "TASK_SUBMITTED", task.getId());
    }

    public static MessageDraft forAlipayRecharge(String username, BigDecimal amount, String outTradeNo) {
        return requiredDraft("充值成功", "支付宝充值 ¥" + money(amount) + " 已到账（订单 " + safe(outTradeNo) + "）",
                "WALLET", "ALIPAY_RECHARGE", null);
    }

    public static MessageDraft forAdminRecharge(String username, BigDecimal amount, String remark) {
        String extra = remark == null || remark.isBlank() ? "" : "，备注：" + remark.trim();
        return requiredDraft("充值成功", "管理员为你充值 ¥" + money(amount) + extra, "WALLET", "ADMIN_RECHARGE", null);
    }

    public static MessageDraft forWithdrawApply(String username, BigDecimal amount, Long requestId) {
        return requiredDraft("提现申请已提交", "你申请了 ¥" + money(amount) + " 提现，等待管理员审核", "WALLET", "WITHDRAW_APPLY", requestId);
    }

    public static MessageDraft forWithdrawApproved(String username, BigDecimal amount, Long requestId, boolean alipaySandbox) {
        String channel = alipaySandbox ? "支付宝沙箱" : "平台";
        return requiredDraft("提现审核通过", "你的 ¥" + money(amount) + " 提现申请已通过，将通过" + channel + "到账",
                "WALLET", "WITHDRAW_APPROVE", requestId);
    }

    public static MessageDraft forWithdrawRejected(String username, BigDecimal amount, Long requestId, String remark) {
        String extra = remark == null || remark.isBlank() ? "" : "，原因：" + remark.trim();
        return requiredDraft("提现审核驳回", "你的 ¥" + money(amount) + " 提现申请未通过，金额已退回钱包" + extra,
                "WALLET", "WITHDRAW_REJECT", requestId);
    }

    public static MessageDraft forTaskIncome(String username, BigDecimal amount, Long taskId, String title) {
        return requiredDraft("任务酬劳入账", "任务《" + safe(title) + "》完成，¥" + money(amount) + " 已入账至你的钱包",
                "WALLET", "TASK_INCOME", taskId);
    }

    public static MessageDraft forBountyLock(String username, BigDecimal amount, Long taskId, String title) {
        return requiredDraft("赏金支付成功", "任务《" + safe(title) + "》赏金 ¥" + money(amount) + " 已支付成功",
                "WALLET", "TASK_BOUNTY_PAID", taskId);
    }

    private static Optional<MessageDraft> optionalDraft(String title, String content, String category, String eventType, Long refId) {
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new MessageDraft(title.trim(), content.trim(), category, eventType, refId));
    }

    private static MessageDraft requiredDraft(String title, String content, String category, String eventType, Long refId) {
        return optionalDraft(title, content, category, eventType, refId)
                .orElseThrow(() -> new IllegalArgumentException("系统消息字段不完整"));
    }

    private static Optional<MessageDraft> draft(String title, String content, String category, String eventType, Long refId) {
        return optionalDraft(title, content, category, eventType, refId);
    }

    private static String bookTitle(String title) {
        String t = safe(title);
        return t.isBlank() ? "" : "《" + t + "》";
    }

    private static String money(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String taskStatusLabel(String status) {
        return switch (status) {
            case "PENDING_REVIEW" -> "待审核";
            case "REJECTED" -> "已驳回";
            case "OPEN" -> "待接单";
            case "CLAIMED" -> "已被接单";
            case "PENDING_CONFIRMATION" -> "待发布者确认";
            case "COMPLETED" -> "已完成";
            case "CANCELLED" -> "已取消";
            case "OVERDUE" -> "超时";
            default -> status == null || status.isBlank() ? "状态已更新" : status;
        };
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static String normalizeUpper(String raw) {
        return normalize(raw).toUpperCase();
    }

    private static String safe(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
