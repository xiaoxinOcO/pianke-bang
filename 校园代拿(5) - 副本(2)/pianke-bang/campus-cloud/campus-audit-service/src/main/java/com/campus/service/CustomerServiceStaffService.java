package com.campus.service;

import com.campus.model.UserAccount;
import com.campus.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CustomerServiceStaffService {

    private static final int ONLINE_WINDOW_MINUTES = 3;

    private final UserAccountRepository userAccountRepository;
    /** 在线客服轮询计数器：按创建顺序在在线客服之间轮流分配 */
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    public CustomerServiceStaffService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    public Map<String, Object> assignAgentForUser(String currentUser) {
        if (currentUser == null || currentUser.isBlank()) {
            throw new IllegalStateException("请先登录后再转人工客服");
        }
        List<UserAccount> staffList = userAccountRepository
                .findByCustomerServiceStaffTrueAndEnabledTrueOrderByCreatedAtAsc();
        if (staffList.isEmpty()) {
            throw new IllegalStateException("暂无可用的客服账号，请联系管理员在后台创建人工客服");
        }

        LocalDateTime onlineThreshold = LocalDateTime.now().minusMinutes(ONLINE_WINDOW_MINUTES);
        List<UserAccount> onlineStaff = staffList.stream()
                .filter(u -> u.getLastSeenAt() != null && u.getLastSeenAt().isAfter(onlineThreshold))
                .toList();

        List<UserAccount> pool = onlineStaff.isEmpty() ? staffList : onlineStaff;
        boolean online = !onlineStaff.isEmpty();
        int index = Math.floorMod(roundRobinCounter.getAndIncrement(), pool.size());
        UserAccount picked = pool.get(index);

        return Map.of(
                "username", picked.getUsername(),
                "online", online,
                "message", online ? "ok" : "offline_fallback"
        );
    }
}
