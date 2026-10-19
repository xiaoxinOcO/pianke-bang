package com.campus.service;

import com.campus.dto.AdminAccountRoleRequest;
import com.campus.dto.AdminCreditScoreRequest;
import com.campus.dto.AdminUserDTO;
import com.campus.dto.CreateCustomerServiceRequest;
import com.campus.model.AccountRole;
import com.campus.model.UserAccount;
import com.campus.repository.UserAccountRepository;
import com.campus.rules.CreditRules;
import com.campus.security.AdminPolicy;
import com.campus.security.PasswordHasher;
import com.campus.support.RemoteAuditLogSupport;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminUserService {

    private final UserAccountRepository userAccountRepository;
    private final RemoteAuditLogSupport remoteAuditLog;
    private final AdminPolicy adminPolicy;
    private final PasswordHasher passwordHasher;

    public AdminUserService(
            UserAccountRepository userAccountRepository,
            RemoteAuditLogSupport remoteAuditLog,
            AdminPolicy adminPolicy,
            PasswordHasher passwordHasher
    ) {
        this.userAccountRepository = userAccountRepository;
        this.remoteAuditLog = remoteAuditLog;
        this.adminPolicy = adminPolicy;
        this.passwordHasher = passwordHasher;
    }

    public List<AdminUserDTO> listUsers(String operator) {
        requireAdmin(operator);
        return userAccountRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toDTO)
                .toList();
    }

    public AdminUserDTO setUserEnabled(Long id, boolean enabled, String operator) {
        requireAdmin(operator);

        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (user.isPlatformAdmin() && !enabled) {
            throw new IllegalStateException("不能禁用管理员账号");
        }

        user.setEnabled(enabled);
        userAccountRepository.save(user);

        remoteAuditLog.log(
                operator,
                enabled ? "USER_ENABLED" : "USER_DISABLED",
                null,
                (enabled ? "启用用户：" : "禁用用户：") + user.getUsername()
        );

        return toDTO(user);
    }

    public AdminUserDTO approveAvatar(Long id, String operator) {
        requireAdmin(operator);

        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (!user.isAvatarPending() || user.getPendingAvatarUrl() == null || user.getPendingAvatarUrl().isBlank()) {
            throw new IllegalStateException("该用户暂无待审核头像");
        }

        user.setAvatarUrl(user.getPendingAvatarUrl());
        user.setPendingAvatarUrl(null);
        user.setAvatarPending(false);
        userAccountRepository.save(user);

        remoteAuditLog.log(operator, "AVATAR_APPROVED", null, "通过头像审核：" + user.getUsername());

        return toDTO(user);
    }

    public AdminUserDTO rejectAvatar(Long id, String operator) {
        requireAdmin(operator);

        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (!user.isAvatarPending()) {
            throw new IllegalStateException("该用户暂无待审核头像");
        }

        user.setPendingAvatarUrl(null);
        user.setAvatarPending(false);
        userAccountRepository.save(user);

        remoteAuditLog.log(operator, "AVATAR_REJECTED", null, "驳回头像审核：" + user.getUsername());

        return toDTO(user);
    }

    public AdminUserDTO approveRider(Long id, String operator) {
        requireAdmin(operator);

        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (!user.isRiderApplicationPending()) {
            throw new IllegalStateException("该用户暂无待审核接单员申请");
        }

        user.setRiderApproved(true);
        user.setRiderApplicationPending(false);
        userAccountRepository.save(user);

        remoteAuditLog.log(operator, "RIDER_APPROVED", null, "通过接单员申请：" + user.getUsername());

        return toDTO(user);
    }

    public AdminUserDTO updateUserCreditScore(Long id, AdminCreditScoreRequest request, String operator) {
        requireAdmin(operator);

        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        Integer score = request == null ? null : request.getCreditScore();
        if (score == null) {
            throw new IllegalArgumentException("信誉分不能为空");
        }
        if (score < 0 || score > CreditRules.MAX) {
            throw new IllegalArgumentException("信誉分必须在0-" + CreditRules.MAX + "之间");
        }

        user.setCreditScore(score);
        if (score >= CreditRules.MIN_TO_CLAIM) {
            user.setBlockedUntil(null);
        }
        userAccountRepository.save(user);

        remoteAuditLog.log(operator, "CREDIT_SCORE_UPDATE", null, "修改信誉分：" + user.getUsername() + " -> " + score);
        return toDTO(user);
    }

    public AdminUserDTO rejectRider(Long id, String operator) {
        requireAdmin(operator);

        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (!user.isRiderApplicationPending()) {
            throw new IllegalStateException("该用户暂无待审核接单员申请");
        }

        user.setRiderApproved(false);
        user.setRiderApplicationPending(false);
        user.setRiderRealName(null);
        user.setRiderStudentId(null);
        user.setRiderPhone(null);
        user.setRiderApplyReason(null);
        user.setRiderAppliedAt(null);
        userAccountRepository.save(user);

        remoteAuditLog.log(operator, "RIDER_REJECTED", null, "驳回接单员申请：" + user.getUsername());

        return toDTO(user);
    }

    public AdminUserDTO revokeRiderCertification(Long id, String operator) {
        requireAdmin(operator);

        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (!user.isRiderApproved()) {
            throw new IllegalStateException("该用户不是已认证接单员，无需撤销");
        }

        user.setRiderApproved(false);
        user.setRiderApplicationPending(false);
        user.setRiderRealName(null);
        user.setRiderStudentId(null);
        user.setRiderPhone(null);
        user.setRiderApplyReason(null);
        user.setRiderAppliedAt(null);
        userAccountRepository.save(user);

        remoteAuditLog.log(operator, "RIDER_CERT_REVOKED", null, "撤销接单员认证：" + user.getUsername());

        return toDTO(user);
    }

    public List<AdminUserDTO> listCustomerServiceStaff(String operator) {
        requireAdmin(operator);
        return userAccountRepository.findByCustomerServiceStaffTrueOrderByCreatedAtDesc().stream()
                .map(this::toDTO)
                .toList();
    }

    public AdminUserDTO createCustomerServiceStaff(CreateCustomerServiceRequest request, String operator) {
        requireAdmin(operator);
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String password = request.getPassword() == null ? "" : request.getPassword().trim();
        if (username.isBlank()) {
            throw new IllegalArgumentException("客服账号不能为空");
        }
        if (username.length() > 50) {
            throw new IllegalArgumentException("客服账号最多50位");
        }
        if (password.length() < 6 || password.length() > 32) {
            throw new IllegalArgumentException("密码长度需在6-32位");
        }
        if (userAccountRepository.existsByUsername(username)) {
            throw new IllegalStateException("账号已存在");
        }

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPassword(passwordHasher.hash(password));
        user.setEnabled(true);
        user.applyAccountRole(AccountRole.CUSTOMER_SERVICE);
        user.setAvatarUrl(UserAccount.CUSTOMER_SERVICE_AVATAR_URL);
        userAccountRepository.save(user);

        remoteAuditLog.log(operator, "CS_STAFF_CREATED", null, "创建人工客服账号：" + username);
        return toDTO(user);
    }

    /**
     * 调整用户平台角色：USER / ADMIN / CUSTOMER_SERVICE（管理员与客服互斥）。
     */
    public AdminUserDTO setAccountRole(Long id, AdminAccountRoleRequest request, String operator) {
        requireAdmin(operator);
        if (id == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        AccountRole target = AccountRole.parse(request == null ? null : request.getRole());
        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        AccountRole current = user.resolveAccountRole();
        if (current == target) {
            return toDTO(user);
        }

        if (current == AccountRole.ADMIN && target != AccountRole.ADMIN) {
            if (userAccountRepository.countByPlatformAdminTrue() <= 1) {
                throw new IllegalStateException("不能取消最后一个管理员");
            }
            if (operator != null && operator.equalsIgnoreCase(user.getUsername())) {
                throw new IllegalStateException("不能取消自己的管理员角色");
            }
        }

        if (target == AccountRole.CUSTOMER_SERVICE) {
            user.applyAccountRole(AccountRole.CUSTOMER_SERVICE);
            user.setAvatarUrl(UserAccount.CUSTOMER_SERVICE_AVATAR_URL);
        } else {
            user.applyAccountRole(target);
        }
        userAccountRepository.save(user);

        remoteAuditLog.log(
                operator,
                "ACCOUNT_ROLE_UPDATE",
                null,
                "调整账号角色：" + user.getUsername() + " " + current + " -> " + target
        );
        return toDTO(user);
    }

    private AdminUserDTO toDTO(UserAccount user) {
        LocalDateTime now = LocalDateTime.now();
        boolean online = user.getLastSeenAt() != null && user.getLastSeenAt().isAfter(now.minusMinutes(2));
        AccountRole role = user.resolveAccountRole();
        return new AdminUserDTO(
                user.getId(),
                user.getUsername(),
                user.getPhone(),
                user.isEnabled(),
                user.isAvatarPending(),
                user.resolveDisplayAvatarUrl(),
                user.getPendingAvatarUrl(),
                user.isRiderApproved(),
                user.isRiderApplicationPending(),
                user.getCreditScore(),
                user.getBlockedUntil(),
                user.getPendingPenalty(),
                user.getRiderRealName(),
                user.getRiderStudentId(),
                user.getRiderPhone(),
                user.getRiderApplyReason(),
                user.getRiderAppliedAt(),
                online,
                user.getLastSeenAt(),
                user.getCreatedAt(),
                user.isCustomerServiceStaff(),
                user.isPlatformAdmin(),
                role.name()
        );
    }

    private void requireAdmin(String operator) {
        if (operator == null || !adminPolicy.isAdmin(operator)) {
            throw new IllegalStateException("仅管理员可执行该操作");
        }
    }
}
