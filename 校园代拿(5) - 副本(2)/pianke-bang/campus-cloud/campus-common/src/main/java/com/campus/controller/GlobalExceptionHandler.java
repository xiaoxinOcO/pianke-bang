package com.campus.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import feign.FeignException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 对外只返回稳定错误码与安全文案；数据库 / Seata / 堆栈细节仅写日志。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public static final String CODE_BAD_REQUEST = "BAD_REQUEST";
    public static final String CODE_CONFLICT = "CONFLICT";
    public static final String CODE_NOT_FOUND = "NOT_FOUND";
    public static final String CODE_REMOTE_ERROR = "REMOTE_ERROR";
    public static final String CODE_DB_ERROR = "DB_ERROR";
    public static final String CODE_TX_ERROR = "TX_ERROR";
    public static final String CODE_INTERNAL = "INTERNAL_ERROR";

    @ExceptionHandler(FeignException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleFeign(FeignException ex) {
        log.warn("Feign 调用失败 status={} message={}", ex.status(), safeLogDetail(ex.contentUTF8()), ex);
        String clientMessage = "远程服务暂时不可用，请稍后重试";
        if (ex.status() >= 400 && ex.status() < 500) {
            String extracted = extractJsonMessage(ex.contentUTF8());
            if (isSafeClientMessage(extracted)) {
                clientMessage = extracted;
            }
        }
        return errorBody(HttpStatus.CONFLICT.value(), CODE_REMOTE_ERROR, clientMessage);
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleDataAccess(DataAccessException ex) {
        log.error("数据库访问失败", ex);
        return errorBody(HttpStatus.INTERNAL_SERVER_ERROR.value(), CODE_DB_ERROR, "数据服务繁忙，请稍后重试");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleBadRequest(IllegalArgumentException ex) {
        String message = isSafeClientMessage(ex.getMessage()) ? ex.getMessage().trim() : "请求参数无效";
        if (!isSafeClientMessage(ex.getMessage())) {
            log.warn("非法参数异常已脱敏: {}", safeLogDetail(ex.getMessage()), ex);
        }
        return errorBody(HttpStatus.BAD_REQUEST.value(), CODE_BAD_REQUEST, message);
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleConflict(IllegalStateException ex) {
        String message = isSafeClientMessage(ex.getMessage()) ? ex.getMessage().trim() : "当前状态不允许此操作";
        if (!isSafeClientMessage(ex.getMessage())) {
            log.warn("状态冲突异常已脱敏: {}", safeLogDetail(ex.getMessage()), ex);
        }
        return errorBody(HttpStatus.CONFLICT.value(), CODE_CONFLICT, message);
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleNotFound(NoSuchElementException ex) {
        String message = isSafeClientMessage(ex.getMessage()) ? ex.getMessage().trim() : "资源不存在";
        return errorBody(HttpStatus.NOT_FOUND.value(), CODE_NOT_FOUND, message);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleUnexpected(Exception ex) {
        log.error("未处理异常: {}", ex.getClass().getSimpleName(), ex);
        if (isSeataOrInfraFailure(ex)) {
            return errorBody(HttpStatus.INTERNAL_SERVER_ERROR.value(), CODE_TX_ERROR, "事务服务繁忙，请稍后重试");
        }
        return errorBody(HttpStatus.INTERNAL_SERVER_ERROR.value(), CODE_INTERNAL, "服务器内部错误，请稍后重试");
    }

    private static Map<String, Object> errorBody(int httpCode, String errorCode, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("code", httpCode);
        body.put("errorCode", errorCode);
        body.put("message", message);
        return body;
    }

    private static boolean isSeataOrInfraFailure(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String blob = (ex.getClass().getName() + " " + String.valueOf(ex.getMessage())
                + " " + root.getClass().getName() + " " + String.valueOf(root.getMessage())).toLowerCase();
        return blob.contains("seata")
                || blob.contains("tmtransaction")
                || blob.contains("transactionexception")
                || blob.contains("could not initiate rm")
                || blob.contains("no available service")
                || blob.contains("rpc timeout")
                || blob.contains("begin global request failed")
                || blob.contains("communications link failure")
                || blob.contains("getconnectiontimeoutexception");
    }

    /** 业务中文提示可回传；SQL / 堆栈 / 英文基础设施细节不可回传。 */
    static boolean isSafeClientMessage(String message) {
        if (message == null) {
            return false;
        }
        String text = message.trim();
        if (text.isEmpty() || text.length() > 200) {
            return false;
        }
        String lower = text.toLowerCase();
        if (lower.contains("sql")
                || lower.contains("seata")
                || lower.contains("jdbc")
                || lower.contains("hibernate")
                || lower.contains("undo_log")
                || lower.contains("exception")
                || lower.contains("stack")
                || lower.contains("at com.")
                || lower.contains("at org.")
                || lower.contains("communications link")
                || lower.contains("connection timed out")
                || lower.contains("access denied")
                || text.contains("###")
                || text.contains("Caused by")) {
            return false;
        }
        // 业务文案多为中文或简短中英提示
        long cjk = text.chars().filter(ch -> Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN).count();
        return cjk > 0 || text.length() <= 80;
    }

    private static String extractJsonMessage(String raw) {
        if (raw == null || !raw.contains("\"message\"")) {
            return null;
        }
        try {
            int start = raw.indexOf("\"message\"");
            int colon = raw.indexOf(':', start);
            int quoteStart = raw.indexOf('"', colon + 1);
            int quoteEnd = raw.indexOf('"', quoteStart + 1);
            if (quoteStart >= 0 && quoteEnd > quoteStart) {
                return raw.substring(quoteStart + 1, quoteEnd);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    private static String safeLogDetail(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.replaceAll("\\s+", " ").trim();
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }
}
