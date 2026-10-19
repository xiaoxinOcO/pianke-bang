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

    public Map<String, Object> getAgentAvailability() {
        List<UserAccount> staffList = listEnabledStaff();
        if (staffList.isEmpty()) {
            return Map.of(
                    "configured", false,
                    "online", false,
                    "onlineCount", 0,
                    "message", "暂未开通人工客服，请继续使用智能客服或稍后再试"
            );
        }

        List<UserAccount> onlineStaff = filterOnlineStaff(staffList);
        boolean online = !onlineStaff.isEmpty();
        return Map.of(
                "configured", true,
                "online", online,
                "onlineCount", onlineStaff.size(),
                "message", online
                        ? "人工客服在线，可为您转接"
                        : "人工客服当前不在线，请稍后再试或继续使用智能客服"
        );
    }

    public Map<String, Object> assignAgentForUser(String currentUser) {
        if (currentUser == null || currentUser.isBlank()) {
            throw new IllegalStateException("请先登录后再转人工客服");
        }
        List<UserAccount> staffList = listEnabledStaff();
        if (staffList.isEmpty()) {
            throw new IllegalStateException("暂未开通人工客服，请继续使用智能客服或稍后再试");
        }

        List<UserAccount> onlineStaff = filterOnlineStaff(staffList);
        if (onlineStaff.isEmpty()) {
            throw new IllegalStateException("人工客服当前不在线，请稍后再试或继续使用智能客服");
        }

        int index = Math.floorMod(roundRobinCounter.getAndIncrement(), onlineStaff.size());
        UserAccount picked = onlineStaff.get(index);

        return Map.of(
                "username", picked.getUsername(),
                "online", true,
                "message", "ok"
        );
    }

    private List<UserAccount> listEnabledStaff() {
        return userAccountRepository.findByCustomerServiceStaffTrueAndEnabledTrueOrderByCreatedAtAsc();
    }

    private List<UserAccount> filterOnlineStaff(List<UserAccount> staffList) {
        LocalDateTime onlineThreshold = LocalDateTime.now().minusMinutes(ONLINE_WINDOW_MINUTES);
        return staffList.stream()
                .filter(u -> u.getLastSeenAt() != null && u.getLastSeenAt().isAfter(onlineThreshold))
                .toList();
    }
}
