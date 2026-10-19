package com.campus.service;

import com.campus.client.TaskInternalClient;
import com.campus.client.WalletInternalClient;
import com.campus.model.UserAccount;
import com.campus.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人工客服工作台：只读查询咨询用户的钱包与任务摘要。
 */
@Service
public class CsDeskContextService {

    private static final Logger log = LoggerFactory.getLogger(CsDeskContextService.class);

    private final UserAccountRepository userAccountRepository;
    private final WalletInternalClient walletInternalClient;
    private final TaskInternalClient taskInternalClient;

    public CsDeskContextService(
            UserAccountRepository userAccountRepository,
            WalletInternalClient walletInternalClient,
            TaskInternalClient taskInternalClient
    ) {
        this.userAccountRepository = userAccountRepository;
        this.walletInternalClient = walletInternalClient;
        this.taskInternalClient = taskInternalClient;
    }

    public Map<String, Object> userContext(String operator, String targetUsername) {
        assertCsStaff(operator);
        String target = targetUsername == null ? "" : targetUsername.trim();
        if (target.isBlank()) {
            throw new IllegalArgumentException("请指定用户名");
        }

        UserAccount user = userAccountRepository.findByUsernameIgnoreCase(target)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("username", user.getUsername());
        profile.put("riderApproved", user.isRiderApproved());
        profile.put("customerServiceStaff", user.isCustomerServiceStaff());
        profile.put("creditScore", user.getCreditScore());
        profile.put("phoneMasked", maskPhone(user.getPhone()));

        Map<String, Object> wallet = loadWallet(user.getUsername());
        List<Map<String, Object>> tasks = loadTasks(user.getUsername(), user.getId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "ok");
        data.put("profile", profile);
        data.put("wallet", wallet);
        data.put("tasks", tasks);
        return data;
    }

    private void assertCsStaff(String operator) {
        if (operator == null || operator.isBlank()) {
            throw new IllegalStateException("请先登录");
        }
        UserAccount me = userAccountRepository.findByUsernameIgnoreCase(operator.trim())
                .orElseThrow(() -> new IllegalStateException("账号不存在"));
        if (!me.isCustomerServiceStaff()) {
            throw new IllegalStateException("仅人工客服可查看用户上下文");
        }
    }

    private Map<String, Object> loadWallet(String username) {
        Map<String, Object> wallet = new LinkedHashMap<>();
        try {
            Map<String, Object> snap = walletInternalClient.agentSnapshot(username);
            if (snap == null || snap.get("error") != null) {
                wallet.put("available", false);
                wallet.put("error", snap != null ? String.valueOf(snap.get("error")) : "暂时查不到钱包");
                return wallet;
            }
            wallet.put("available", true);
            wallet.put("balance", snap.get("balance"));
            wallet.put("totalIncome", snap.get("totalIncome"));
            wallet.put("totalWithdrawn", snap.get("totalWithdrawn"));
            wallet.put("withdrawingAmount", snap.get("withdrawingAmount"));
            return wallet;
        } catch (Exception ex) {
            log.warn("cs desk wallet failed: {}", ex.getMessage());
            wallet.put("available", false);
            wallet.put("error", "钱包查询暂时失败");
            return wallet;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadTasks(String username, Long userId) {
        try {
            Map<String, Object> summary = taskInternalClient.agentSummary(username, userId, "all", 8);
            if (summary == null || summary.get("error") != null) {
                return List.of();
            }
            Object raw = summary.get("tasks");
            if (!(raw instanceof List<?> list)) {
                return List.of();
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", m.get("id"));
                row.put("title", m.get("title"));
                row.put("status", m.get("status"));
                row.put("statusLabel", m.get("statusLabel"));
                row.put("reward", m.get("reward"));
                row.put("role", m.get("role"));
                row.put("roleLabel", m.get("roleLabel"));
                out.add(row);
            }
            return out;
        } catch (Exception ex) {
            log.warn("cs desk tasks failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private static String maskPhone(String phone) {
        if (phone == null) {
            return "";
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 7) {
            return digits;
        }
        return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
    }
}
