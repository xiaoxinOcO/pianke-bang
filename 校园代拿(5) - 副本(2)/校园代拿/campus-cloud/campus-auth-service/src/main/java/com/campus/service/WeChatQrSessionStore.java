package com.campus.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WeChatQrSessionStore {

    public enum Status {
        PENDING,
        NEED_BIND,
        NEED_CHOOSE,
        SUCCESS,
        EXPIRED,
        FAILED
    }

    public static final class Session {
        private final String id;
        private final Instant expiresAt;
        private final String mode;
        private volatile Status status = Status.PENDING;
        private volatile String message = "";
        private volatile String wxWebOpenId = "";
        private volatile String wxUnionId = "";
        private volatile String phone = "";
        private volatile String pickToken = "";
        private volatile List<Map<String, Object>> accounts = List.of();
        private volatile String loginToken = "";
        private volatile String username = "";

        private Session(String id, Instant expiresAt, String mode) {
            this.id = id;
            this.expiresAt = expiresAt;
            this.mode = mode;
        }

        public String getId() {
            return id;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }

        public String getMode() {
            return mode;
        }

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message == null ? "" : message;
        }

        public String getWxWebOpenId() {
            return wxWebOpenId;
        }

        public void setWxWebOpenId(String wxWebOpenId) {
            this.wxWebOpenId = wxWebOpenId == null ? "" : wxWebOpenId;
        }

        public String getWxUnionId() {
            return wxUnionId;
        }

        public void setWxUnionId(String wxUnionId) {
            this.wxUnionId = wxUnionId == null ? "" : wxUnionId;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone == null ? "" : phone;
        }

        public String getPickToken() {
            return pickToken;
        }

        public void setPickToken(String pickToken) {
            this.pickToken = pickToken == null ? "" : pickToken;
        }

        public List<Map<String, Object>> getAccounts() {
            return accounts;
        }

        public void setAccounts(List<Map<String, Object>> accounts) {
            this.accounts = accounts == null ? List.of() : List.copyOf(accounts);
        }

        public String getLoginToken() {
            return loginToken;
        }

        public void setLoginToken(String loginToken) {
            this.loginToken = loginToken == null ? "" : loginToken;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username == null ? "" : username;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public Session create(String mode, long ttlSeconds) {
        purgeExpired();
        String id = UUID.randomUUID().toString().replace("-", "");
        Session session = new Session(id, Instant.now().plusSeconds(Math.max(60, ttlSeconds)), mode);
        sessions.put(id, session);
        return session;
    }

    public Optional<Session> get(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        Session session = sessions.get(id.trim());
        if (session == null) {
            return Optional.empty();
        }
        if (session.isExpired() && session.getStatus() != Status.SUCCESS) {
            session.setStatus(Status.EXPIRED);
            session.setMessage("二维码已过期，请刷新后重试");
        }
        return Optional.of(session);
    }

    public void remove(String id) {
        if (id != null) {
            sessions.remove(id);
        }
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(e -> {
            Session s = e.getValue();
            return s.isExpired() && s.getStatus() != Status.SUCCESS
                    || (s.getStatus() == Status.SUCCESS && s.getExpiresAt().plusSeconds(60).isBefore(now));
        });
    }
}
