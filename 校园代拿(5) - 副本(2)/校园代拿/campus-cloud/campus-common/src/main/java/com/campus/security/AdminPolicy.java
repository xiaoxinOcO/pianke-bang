package com.campus.security;

import com.campus.model.UserAccount;
import com.campus.repository.UserAccountRepository;

/**
 * 平台管理员 / 客服权限：以用户表角色标志为准（不再依赖运行时 CSV 名单判断）。
 */
public class AdminPolicy {

    private final UserAccountRepository userAccountRepository;

    public AdminPolicy(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    public boolean isAdmin(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return userAccountRepository.findByUsernameIgnoreCase(username.trim())
                .map(UserAccount::isPlatformAdmin)
                .orElse(false);
    }

    public boolean isCustomerServiceStaff(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return userAccountRepository.findByUsernameIgnoreCase(username.trim())
                .map(UserAccount::isCustomerServiceStaff)
                .orElse(false);
    }

    /**
     * 是否允许用「用户名 + 密码」登录（管理员或客服账号；普通用户仍走账号ID/手机号）。
     */
    public boolean isUsernamePasswordLoginAllowed(String rawLogin) {
        if (rawLogin == null || rawLogin.isBlank()) {
            return false;
        }
        return userAccountRepository.findByUsernameIgnoreCase(rawLogin.trim())
                .map(u -> u.isPlatformAdmin() || u.isCustomerServiceStaff())
                .orElse(false);
    }

    /** @deprecated 使用 {@link #isUsernamePasswordLoginAllowed(String)} */
    @Deprecated
    public boolean isConfiguredAdminLoginName(String rawLogin) {
        return isUsernamePasswordLoginAllowed(rawLogin);
    }
}
