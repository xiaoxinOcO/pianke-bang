package com.campus.service;

import com.campus.dto.ChangePasswordRequest;
import com.campus.dto.ForgotPasswordResetRequest;
import com.campus.dto.LoginBySmsRequest;
import com.campus.dto.LoginRequest;
import com.campus.dto.RegisterBySmsRequest;
import com.campus.dto.RegisterRequest;
import com.campus.dto.RiderApplyRequest;
import com.campus.dto.UpdateProfileRequest;
import com.campus.dto.UserPublicProfileDTO;
import com.campus.model.DeliveryTask;
import com.campus.model.UserAccount;
import com.campus.repository.DeliveryTaskRepository;
import com.campus.repository.UserAccountRepository;
import com.campus.security.AdminPolicy;
import com.campus.security.JwtService;
import com.campus.security.LoginChannel;
import com.campus.security.PasswordHasher;
import com.campus.support.OssImageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 同一手机号在系统中允许绑定的注册账号数量上限 */
    private static final int MAX_ACCOUNTS_PER_PHONE = 2;

    private final UserAccountRepository userAccountRepository;
    private final DeliveryTaskRepository deliveryTaskRepository;
    private final JwtService jwtService;
    private final SmsCodeService smsCodeService;
    private final AdminPolicy adminPolicy;
    private final ImageAuditService imageAuditService;
    private final PasswordHasher passwordHasher;
    private final OssImageStore ossImageStore;

    public AuthService(
            UserAccountRepository userAccountRepository,
            DeliveryTaskRepository deliveryTaskRepository,
            JwtService jwtService,
            SmsCodeService smsCodeService,
            AdminPolicy adminPolicy,
            ImageAuditService imageAuditService,
            PasswordHasher passwordHasher,
            OssImageStore ossImageStore
    ) {
        this.userAccountRepository = userAccountRepository;
        this.deliveryTaskRepository = deliveryTaskRepository;
        this.jwtService = jwtService;
        this.smsCodeService = smsCodeService;
        this.adminPolicy = adminPolicy;
        this.imageAuditService = imageAuditService;
        this.passwordHasher = passwordHasher;
        this.ossImageStore = ossImageStore;
    }

    private void assertPhoneRegistrationSlotAvailable(String normalizedMobile) {
        if (normalizedMobile == null || normalizedMobile.isBlank()) {
            return;
        }
        long n = userAccountRepository.countByMobileBound(normalizedMobile);
        log.info("手机号注册名额校验: mobile={} 已占用账号数={} 上限={}", normalizedMobile, n, MAX_ACCOUNTS_PER_PHONE);
        if (n >= MAX_ACCOUNTS_PER_PHONE) {
            log.warn("已拒绝（发码或注册）: 手机号 {} 已达注册上限", normalizedMobile);
            throw new IllegalStateException("该手机号注册账号数已达上限（最多" + MAX_ACCOUNTS_PER_PHONE + "个）");
        }
    }

    public Map<String, Object> register(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }

        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String password = request.getPassword() == null ? "" : request.getPassword().trim();

        if (username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (username.length() > 12) {
            throw new IllegalArgumentException("用户名最多50位");
        }
        if (password.length() < 6 || password.length() > 32) {
            throw new IllegalArgumentException("密码长度需在6-32位");
        }
        if (userAccountRepository.existsByUsername(username)) {
            throw new IllegalStateException("用户名已存在");
        }

        String regPhone = SmsCodeService.normalizeChinaMobile(request.getPhone() == null ? "" : request.getPhone());
        if (!regPhone.isBlank()) {
            if (!regPhone.matches("^1\\d{10}$")) {
                throw new IllegalArgumentException("手机号格式不正确");
            }
            assertPhoneRegistrationSlotAvailable(regPhone);
        }

        String realName = request.getRealName() == null ? "" : request.getRealName().trim();
        String studentId = request.getStudentId() == null ? "" : request.getStudentId().trim();
        String gender = request.getGender() == null ? "" : request.getGender().trim();

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPassword(passwordHasher.hash(password));
        user.setEnabled(true);
        if (!regPhone.isBlank()) {
            user.setPhone(regPhone);
        }
        if (!realName.isBlank()) {
            user.setRealName(realName);
        }
        if (!studentId.isBlank()) {
            user.setStudentId(studentId);
        }
        if (!gender.isBlank()) {
            user.setGender(gender);
        }
        userAccountRepository.save(user);

        return Map.of(
                "message", "注册成功",
                "username", username,
                "userId", user.getId()
        );
    }

    public Map<String, Object> login(LoginRequest request) {
        return issueTokenAfterPasswordCheck(request);
    }

    public Map<String, Object> sendRegisterSmsCode(String phone) {
        String p = SmsCodeService.normalizeChinaMobile(phone);
        if (!p.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        assertPhoneRegistrationSlotAvailable(p);
        smsCodeService.sendCode(p);
        String channel = smsCodeService.usesAliyunSms() ? "aliyun" : "local";
        return Map.of(
                "message", smsCodeService.sendCodeSuccessMessage(),
                "smsChannel", channel
        );
    }

    /**
     * 登录短信：不校验「注册名额」，但要求该手机号已在系统中注册；发码逻辑与注册相同（{@code app.aliyun.dypns.enabled=false} 时仅写库）。
     */
    public Map<String, Object> sendLoginSmsCode(String phone) {
        String p = SmsCodeService.normalizeChinaMobile(phone);
        if (!p.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        List<UserAccount> existing = userAccountRepository.findByPhone(p);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("该手机号未注册，请先注册");
        }
        smsCodeService.sendCode(p);
        String channel = smsCodeService.usesAliyunSms() ? "aliyun" : "local";
        return Map.of(
                "message", smsCodeService.sendCodeSuccessMessage(),
                "smsChannel", channel
        );
    }

    /** 忘记密码发码：与登录发码相同，手机号须已注册。 */
    public Map<String, Object> sendForgotPasswordSms(String phone) {
        return sendLoginSmsCode(phone);
    }

    /**
     * 忘记密码：校验短信验证码后返回可选账号（同号多账号）或唯一账号信息。
     */
    public Map<String, Object> verifyForgotPasswordSms(String phone, String code) {
        String p = SmsCodeService.normalizeChinaMobile(phone == null ? "" : phone);
        String c = code == null ? "" : code.trim();
        if (p.isBlank() || c.isBlank()) {
            throw new IllegalArgumentException("手机号和验证码不能为空");
        }
        smsCodeService.verifyCode(p, c);

        List<UserAccount> list = userAccountRepository.findByPhone(p);
        if (list.isEmpty()) {
            throw new IllegalArgumentException("该手机号未注册");
        }

        List<Long> ids = list.stream().map(UserAccount::getId).toList();
        String pickToken = jwtService.generateSmsPickToken(p, ids);

        if (list.size() > 1) {
            List<Map<String, Object>> accounts = new ArrayList<>();
            for (UserAccount u : list) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("userId", u.getId());
                row.put("username", u.getUsername());
                row.put("avatarUrl", u.resolveDisplayAvatarUrl());
                accounts.add(row);
            }
            return Map.of(
                    "needChooseAccount", true,
                    "accounts", accounts,
                    "pickToken", pickToken,
                    "message", "请选择要重置密码的账号"
            );
        }

        UserAccount user = list.get(0);
        return Map.of(
                "needChooseAccount", false,
                "userId", user.getId(),
                "username", user.getUsername(),
                "pickToken", pickToken,
                "message", "验证成功"
        );
    }

    public Map<String, Object> resetPasswordBySms(ForgotPasswordResetRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String phone = SmsCodeService.normalizeChinaMobile(request.getPhone() == null ? "" : request.getPhone());
        String code = request.getCode() == null ? "" : request.getCode().trim();
        String accountHint = request.getAccount() == null ? "" : request.getAccount().trim();
        String newPwd = request.getNewPassword() == null ? "" : request.getNewPassword().trim();

        if (newPwd.isBlank()) {
            throw new IllegalArgumentException("新密码不能为空");
        }
        if (newPwd.length() < 6 || newPwd.length() > 32) {
            throw new IllegalArgumentException("新密码长度需在6-32位");
        }

        String pickToken = request.getPickToken() == null ? "" : request.getPickToken().trim();
        Long userId = request.getUserId();
        if (!pickToken.isBlank() && userId != null) {
            JwtService.SmsPickPayload payload = jwtService.parseSmsPickToken(pickToken);
            if (!payload.allowedUserIds().contains(userId)) {
                throw new IllegalArgumentException("所选账号不在本次验证范围内");
            }
            UserAccount user = userAccountRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            String userPhone = SmsCodeService.normalizeChinaMobile(user.getPhone() == null ? "" : user.getPhone());
            if (!userPhone.equals(payload.normalizedPhone())) {
                throw new IllegalArgumentException("账号信息已变更，请重新验证");
            }
            if (!user.isEnabled()) {
                throw new IllegalStateException("账号已被管理员禁用，请联系管理员");
            }
            user.setPassword(passwordHasher.hash(newPwd));
            invalidateAllSessions(user);
            userAccountRepository.save(user);
            return Map.of("message", "密码已重置，请使用新密码登录");
        }

        if (phone.isBlank() || code.isBlank()) {
            throw new IllegalArgumentException("手机号、验证码和新密码不能为空");
        }

        smsCodeService.verifyCode(phone, code);

        List<UserAccount> list = userAccountRepository.findByPhone(phone);
        if (list.isEmpty()) {
            throw new IllegalArgumentException("该手机号未注册");
        }
        UserAccount user = resolveUserForForgotPasswordReset(list, accountHint);
        if (!user.isEnabled()) {
            throw new IllegalStateException("账号已被管理员禁用，请联系管理员");
        }

        user.setPassword(passwordHasher.hash(newPwd));
        invalidateAllSessions(user);
        userAccountRepository.save(user);

        return Map.of("message", "密码已重置，请使用新密码登录");
    }

    private UserAccount resolveUserForForgotPasswordReset(List<UserAccount> list, String accountHint) {
        if (list.size() == 1) {
            return list.get(0);
        }
        if (accountHint.isBlank()) {
            throw new IllegalArgumentException("该手机号绑定多个账号，请填写昵称或账号ID");
        }
        String hint = accountHint.trim();
        for (UserAccount u : list) {
            if (u.getUsername().equalsIgnoreCase(hint)) {
                return u;
            }
        }
        if (hint.matches("^\\d+$")) {
            long id = Long.parseLong(hint);
            for (UserAccount u : list) {
                if (u.getId() != null && u.getId() == id) {
                    return u;
                }
            }
        }
        throw new IllegalArgumentException("未找到与填写信息匹配的账号");
    }

    public Map<String, Object> loginBySms(LoginBySmsRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String phone = SmsCodeService.normalizeChinaMobile(request.getPhone() == null ? "" : request.getPhone());
        String code = request.getCode() == null ? "" : request.getCode().trim();
        if (phone.isBlank() || code.isBlank()) {
            throw new IllegalArgumentException("手机号和验证码不能为空");
        }
        smsCodeService.verifyCode(phone, code);

        return resolveLoginByPhone(phone, request.getClientChannel());
    }

    /**
     * 多账号短信登录第二步：凭短时 pickToken 选定 userId。
     */
    public Map<String, Object> confirmLoginBySmsPick(String pickToken, long userId) {
        return confirmLoginBySmsPick(pickToken, userId, LoginChannel.WEB);
    }

    public Map<String, Object> confirmLoginBySmsPick(String pickToken, long userId, String clientChannel) {
        JwtService.SmsPickPayload payload = jwtService.parseSmsPickToken(pickToken);
        if (!payload.allowedUserIds().contains(userId)) {
            throw new IllegalArgumentException("所选账号不在本次验证范围内");
        }
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        String userPhone = SmsCodeService.normalizeChinaMobile(user.getPhone() == null ? "" : user.getPhone());
        if (!userPhone.equals(payload.normalizedPhone())) {
            throw new IllegalArgumentException("账号信息已变更，请重新验证登录");
        }
        return finalizeSmsLogin(user, clientChannel);
    }

    /**
     * 按手机号登录：单账号直接发 token；多账号返回 needChooseAccount + pickToken（与短信登录一致）。
     */
    public Map<String, Object> resolveLoginByPhone(String normalizedPhone) {
        return resolveLoginByPhone(normalizedPhone, LoginChannel.WEB);
    }

    public Map<String, Object> resolveLoginByPhone(String normalizedPhone, String clientChannel) {
        List<UserAccount> list = userAccountRepository.findByPhone(normalizedPhone);
        if (list.isEmpty()) {
            throw new IllegalArgumentException("该手机号未注册，请先注册");
        }
        if (list.size() > 1) {
            List<Map<String, Object>> accounts = new ArrayList<>();
            for (UserAccount u : list) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("userId", u.getId());
                row.put("username", u.getUsername());
                row.put("avatarUrl", u.resolveDisplayAvatarUrl());
                accounts.add(row);
            }
            List<Long> ids = list.stream().map(UserAccount::getId).toList();
            String pickToken = jwtService.generateSmsPickToken(normalizedPhone, ids);
            return Map.of(
                    "needChooseAccount", true,
                    "accounts", accounts,
                    "pickToken", pickToken,
                    "message", "请选择要登录的账号"
            );
        }
        return finalizeSmsLogin(list.get(0), clientChannel);
    }

    public Map<String, Object> finalizeSmsLogin(UserAccount user) {
        return finalizeSmsLogin(user, LoginChannel.WEB);
    }

    public Map<String, Object> finalizeSmsLogin(UserAccount user, String clientChannel) {
        if (!user.isEnabled()) {
            throw new IllegalStateException("账号已被管理员禁用，请联系管理员");
        }
        if (user.isCustomerServiceStaff()) {
            throw new IllegalArgumentException("该账号为人工客服专用，请使用客服工作台登录");
        }
        return issueLoginToken(user, clientChannel);
    }

    /** 为指定端签发新会话；仅踢掉同端旧登录，其它端不受影响。 */
    public Map<String, Object> issueLoginToken(UserAccount user, String clientChannel) {
        String channel = LoginChannel.normalize(clientChannel);
        String newSessionId = UUID.randomUUID().toString().replace("-", "");
        if (LoginChannel.isMp(channel)) {
            user.setActiveMpSessionId(newSessionId);
        } else if (LoginChannel.isRider(channel)) {
            user.setActiveRiderSessionId(newSessionId);
        } else {
            user.setActiveSessionId(newSessionId);
        }
        user.setLastSeenAt(LocalDateTime.now());
        userAccountRepository.save(user);
        String token = jwtService.generateToken(user.getUsername(), newSessionId, user.getId(), channel);
        return Map.of(
                "message", "登录成功",
                "username", user.getUsername(),
                "token", token
        );
    }

    private void invalidateAllSessions(UserAccount user) {
        user.setActiveSessionId(UUID.randomUUID().toString().replace("-", ""));
        user.setActiveMpSessionId(UUID.randomUUID().toString().replace("-", ""));
        user.setActiveRiderSessionId(UUID.randomUUID().toString().replace("-", ""));
    }

    private static String activeSessionForChannel(UserAccount user, String channel) {
        if (LoginChannel.isMp(channel)) {
            return user.getActiveMpSessionId() == null ? "" : user.getActiveMpSessionId().trim();
        }
        if (LoginChannel.isRider(channel)) {
            return user.getActiveRiderSessionId() == null ? "" : user.getActiveRiderSessionId().trim();
        }
        return user.getActiveSessionId() == null ? "" : user.getActiveSessionId().trim();
    }

    private static boolean sessionIdEquals(String active, String sessionId) {
        return active != null && !active.isBlank() && active.trim().equals(sessionId);
    }

    /** 绑定微信标识；同一 openid/unionid 只保留在目标账号上。 */
    public void bindWeChatIdentity(UserAccount user, String webOpenId, String mpOpenId, String unionId) {
        if (user == null) {
            return;
        }
        if (webOpenId != null && !webOpenId.isBlank()) {
            userAccountRepository.findByWxWebOpenId(webOpenId).ifPresent(other -> {
                if (!other.getId().equals(user.getId())) {
                    other.setWxWebOpenId(null);
                    userAccountRepository.save(other);
                }
            });
            user.setWxWebOpenId(webOpenId.trim());
        }
        if (mpOpenId != null && !mpOpenId.isBlank()) {
            userAccountRepository.findByWxMpOpenId(mpOpenId).ifPresent(other -> {
                if (!other.getId().equals(user.getId())) {
                    other.setWxMpOpenId(null);
                    userAccountRepository.save(other);
                }
            });
            user.setWxMpOpenId(mpOpenId.trim());
        }
        if (unionId != null && !unionId.isBlank()) {
            userAccountRepository.findByWxUnionId(unionId).ifPresent(other -> {
                if (!other.getId().equals(user.getId())) {
                    other.setWxUnionId(null);
                    userAccountRepository.save(other);
                }
            });
            user.setWxUnionId(unionId.trim());
        }
        userAccountRepository.save(user);
    }

    public UserAccount requireUserById(long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    public JwtService.SmsPickPayload parseSmsPickToken(String pickToken) {
        return jwtService.parseSmsPickToken(pickToken);
    }

    public Map<String, Object> registerBySms(RegisterBySmsRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }

        String phone = SmsCodeService.normalizeChinaMobile(request.getPhone() == null ? "" : request.getPhone());
        String code = request.getCode() == null ? "" : request.getCode().trim();
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String password = request.getPassword() == null ? "" : request.getPassword().trim();

        if (phone.isBlank() || code.isBlank()) {
            throw new IllegalArgumentException("手机号和验证码不能为空");
        }

        smsCodeService.verifyCode(phone, code);

        RegisterRequest normalRequest = new RegisterRequest();
        normalRequest.setPhone(phone);
        normalRequest.setUsername(username);
        normalRequest.setPassword(password);
        String realName = request.getRealName() == null ? "" : request.getRealName().trim();
        String studentId = request.getStudentId() == null ? "" : request.getStudentId().trim();
        String gender = request.getGender() == null ? "" : request.getGender().trim();
        normalRequest.setRealName(realName);
        normalRequest.setStudentId(studentId);
        normalRequest.setGender(gender);

        Map<String, Object> body = new LinkedHashMap<>(register(normalRequest));
        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("注册失败：请稍后重试"));
        Map<String, Object> login = issueLoginToken(user, request.getClientChannel());
        body.put("token", login.get("token"));
        return body;
    }

    public Map<String, Object> validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或凭证缺失");
        }
        String pureToken = token.startsWith("Bearer ") ? token.substring(7) : token;
        if (!jwtService.isTokenValid(pureToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录态无效或已过期");
        }

        String username = jwtService.extractUsername(pureToken);
        String sessionId = jwtService.extractSessionId(pureToken);
        String channel = jwtService.extractChannel(pureToken);
        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号已被禁用，请联系管理员");
        }
        if (user.isCustomerServiceStaff()) {
            user.setAvatarUrl(UserAccount.CUSTOMER_SERVICE_AVATAR_URL);
            user.setAvatarPending(false);
            user.setPendingAvatarUrl(null);
        }
        String activeSessionId = activeSessionForChannel(user, channel);
        boolean sessionOk = !activeSessionId.isBlank() && activeSessionId.equals(sessionId);
        // 兼容：token 端标识与落库字段短暂不一致时，只要 sid 仍是该账号任一有效会话即可
        if (!sessionOk) {
            sessionOk = sessionIdEquals(user.getActiveSessionId(), sessionId)
                    || sessionIdEquals(user.getActiveMpSessionId(), sessionId)
                    || sessionIdEquals(user.getActiveRiderSessionId(), sessionId);
        }
        if (!sessionOk) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号已在其他设备登录，请重新登录");
        }

        user.setLastSeenAt(LocalDateTime.now());
        userAccountRepository.save(user);

        String studentId = user.getStudentId() == null ? "" : user.getStudentId();
        String regPhone = user.getPhone() == null ? "" : user.getPhone();
        String gender = user.getGender() == null ? "" : user.getGender();
        String realName = user.getRealName() == null ? "" : user.getRealName();
        String pendingAvatar = user.getPendingAvatarUrl() == null ? "" : user.getPendingAvatarUrl().trim();
        int credit = user.getCreditScore() == null ? 80 : user.getCreditScore();
        if (credit <= 0 && user.getBlockedUntil() == null) {
            credit = 80;
        }
        return Map.ofEntries(
                Map.entry("message", "登录态有效"),
                Map.entry("username", user.getUsername()),
                Map.entry("userId", user.getId()),
                Map.entry("studentId", studentId),
                Map.entry("phone", regPhone),
                Map.entry("gender", gender),
                Map.entry("realName", realName),
                Map.entry("avatarUrl", user.resolveDisplayAvatarUrl()),
                Map.entry("pendingAvatarUrl", user.isAvatarPending() ? pendingAvatar : ""),
                Map.entry("avatarPending", user.isAvatarPending()),
                Map.entry("riderApproved", user.isRiderApproved()),
                Map.entry("riderApplicationPending", user.isRiderApplicationPending()),
                Map.entry("creditScore", credit),
                Map.entry("blockedUntil", user.getBlockedUntil() == null ? "" : String.valueOf(user.getBlockedUntil())),
                Map.entry("pendingPenalty", user.getPendingPenalty() == null ? java.math.BigDecimal.ZERO : user.getPendingPenalty()),
                Map.entry("customerServiceStaff", user.isCustomerServiceStaff()),
                Map.entry("platformAdmin", user.isPlatformAdmin()),
                Map.entry("accountRole", user.resolveAccountRole().name())
        );
    }

    public Map<String, Object> markOffline(String token) {
        if (token == null || token.isBlank()) {
            return Map.of("message", "已忽略空凭证");
        }
        String pureToken = token.startsWith("Bearer ") ? token.substring(7) : token;
        if (!jwtService.isTokenValid(pureToken)) {
            return Map.of("message", "凭证无效，已忽略");
        }
        String username = jwtService.extractUsername(pureToken);
        String sessionId = jwtService.extractSessionId(pureToken);
        String channel = jwtService.extractChannel(pureToken);
        UserAccount user = userAccountRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return Map.of("message", "用户不存在，已忽略");
        }
        String activeSessionId = activeSessionForChannel(user, channel);
        if (!activeSessionId.equals(sessionId)) {
            return Map.of("message", "会话不匹配，已忽略");
        }
        user.setLastSeenAt(null);
        userAccountRepository.save(user);
        return Map.of("message", "已标记离线");
    }

    public Map<String, Object> updateProfile(String currentUsername, UpdateProfileRequest request) {
        if (currentUsername == null || currentUsername.isBlank()) {
            throw new IllegalArgumentException("未登录");
        }
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }

        UserAccount user = userAccountRepository.findByUsername(currentUsername.trim())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        String newUsername = request.getUsername() == null ? "" : request.getUsername().trim();
        String avatarUrl = request.getAvatarUrl() == null ? "" : request.getAvatarUrl().trim();

        if (newUsername.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (newUsername.length() > 50) {
            throw new IllegalArgumentException("用户名最多50位");
        }

        if (!newUsername.equalsIgnoreCase(user.getUsername()) && userAccountRepository.existsByUsername(newUsername)) {
            throw new IllegalArgumentException("用户名已存在");
        }

        if (request.getRealName() != null) {
            String realName = request.getRealName().trim();
            if (realName.isBlank()) {
                throw new IllegalArgumentException("真实姓名不能为空");
            }
            if (realName.length() < 2) {
                throw new IllegalArgumentException("真实姓名至少2个字");
            }
            user.setRealName(realName);
        }

        if (request.getStudentId() != null) {
            user.setStudentId(request.getStudentId().trim());
        }

        if (request.getGender() != null) {
            String gender = request.getGender().trim();
            if (!gender.isBlank() && !"male".equals(gender) && !"female".equals(gender)) {
                throw new IllegalArgumentException("性别格式不正确");
            }
            if (!gender.isBlank()) {
                user.setGender(gender);
            }
        }

        user.setUsername(newUsername);
        boolean avatarAutoApproved = false;
        if (!user.isCustomerServiceStaff()) {
            if (!avatarUrl.isBlank() && !avatarUrl.equals(user.getAvatarUrl())) {
                // block 拒绝；pass 自动生效；review / 降级 → 人工待审
                ImageAuditService.AuditOutcome audit = imageAuditService.requireAvatarAudit(avatarUrl);
                String storedAvatar = ossImageStore.storeDataUrl(avatarUrl, "avatar");
                if (audit.verdict() == ImageAuditService.Verdict.PASS) {
                    user.setAvatarUrl(storedAvatar);
                    user.setAvatarPending(false);
                    user.setPendingAvatarUrl(null);
                    avatarAutoApproved = true;
                } else {
                    user.setPendingAvatarUrl(storedAvatar);
                    user.setAvatarPending(true);
                }
            }
        } else {
            user.setAvatarUrl(UserAccount.CUSTOMER_SERVICE_AVATAR_URL);
            user.setAvatarPending(false);
            user.setPendingAvatarUrl(null);
        }
        userAccountRepository.save(user);

        // 改昵称后强制两端重新登录态对齐：仅刷新当前端会话，另一端保持
        // 这里没有请求端信息时，默认刷网页端（主站改资料）；小程序改资料走带 channel 的重载
        String newSessionId = UUID.randomUUID().toString().replace("-", "");
        user.setActiveSessionId(newSessionId);
        user.setLastSeenAt(LocalDateTime.now());
        userAccountRepository.save(user);
        String newToken = jwtService.generateToken(newUsername, newSessionId, user.getId(), LoginChannel.WEB);
        String pendingOut = user.getPendingAvatarUrl() == null ? "" : user.getPendingAvatarUrl().trim();
        String realNameOut = user.getRealName() == null ? "" : user.getRealName();
        String studentIdOut = user.getStudentId() == null ? "" : user.getStudentId();
        String genderOut = user.getGender() == null ? "" : user.getGender();
        String message;
        if (user.isAvatarPending()) {
            message = "资料已更新，头像已提交审核";
        } else if (avatarAutoApproved) {
            message = "个人资料更新成功，头像已通过机审并生效";
        } else {
            message = "个人资料更新成功";
        }
        return Map.ofEntries(
                Map.entry("message", message),
                Map.entry("username", newUsername),
                Map.entry("realName", realNameOut),
                Map.entry("studentId", studentIdOut),
                Map.entry("gender", genderOut),
                Map.entry("avatarUrl", user.resolveDisplayAvatarUrl()),
                Map.entry("pendingAvatarUrl", user.isAvatarPending() ? pendingOut : ""),
                Map.entry("avatarPending", user.isAvatarPending()),
                Map.entry("riderApproved", user.isRiderApproved()),
                Map.entry("riderApplicationPending", user.isRiderApplicationPending()),
                Map.entry("token", newToken)
        );
    }

    public Map<String, Object> applyRider(String currentUsername, RiderApplyRequest request) {
        if (currentUsername == null || currentUsername.isBlank()) {
            throw new IllegalArgumentException("未登录");
        }
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }

        UserAccount user = userAccountRepository.findByUsername(currentUsername.trim())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (user.isRiderApproved()) {
            throw new IllegalStateException("你已经是接单员，无需重复申请");
        }
        if (user.isRiderApplicationPending()) {
            throw new IllegalStateException("接单员申请已提交，请等待管理员审核");
        }

        String realName = request.getRealName() == null ? "" : request.getRealName().trim();
        String studentId = request.getStudentId() == null ? "" : request.getStudentId().trim();
        String phone = SmsCodeService.normalizeChinaMobile(request.getPhone() == null ? "" : request.getPhone());
        String reason = request.getReason() == null ? "" : request.getReason().trim();

        if (realName.isBlank() || studentId.isBlank() || phone.isBlank() || reason.isBlank()) {
            throw new IllegalArgumentException("请完整填写申请信息");
        }
        if (!phone.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        if (reason.length() > 300) {
            throw new IllegalArgumentException("申请理由不能超过300字");
        }

        user.setRiderRealName(realName);
        user.setRiderStudentId(studentId);
        user.setRiderPhone(phone);
        user.setRiderApplyReason(reason);
        user.setRiderApplicationPending(true);
        user.setRiderAppliedAt(java.time.LocalDateTime.now());
        userAccountRepository.save(user);

        return Map.of(
                "message", "接单员申请已提交，等待管理员审核",
                "riderApproved", user.isRiderApproved(),
                "riderApplicationPending", user.isRiderApplicationPending()
        );
    }

    public UserPublicProfileDTO getUserPublicProfile(String username) {
        String normalized = username == null ? "" : username.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }

        UserAccount user = userAccountRepository.findByUsername(normalized)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        Double publisherAvg = null;
        var pubAvgRaw = deliveryTaskRepository.calcPublisherAverageRating(normalized);
        if (pubAvgRaw != null) {
            publisherAvg = pubAvgRaw.setScale(1, java.math.RoundingMode.HALF_UP).doubleValue();
        }

        Double claimerAvg = null;
        var claimerAvgRaw = deliveryTaskRepository.calcClaimerAverageRating(normalized);
        if (claimerAvgRaw != null) {
            claimerAvg = claimerAvgRaw.setScale(1, java.math.RoundingMode.HALF_UP).doubleValue();
        }

        long publisherCount = deliveryTaskRepository.countPublisherRatings(normalized);
        long claimerCount = deliveryTaskRepository.countClaimerRatings(normalized);
        long claimedTaskCount = deliveryTaskRepository.countClaimedTasks(normalized);

        String maskedPhone = "";
        if (user.getRiderPhone() != null && user.getRiderPhone().length() >= 7) {
            String p = user.getRiderPhone();
            maskedPhone = p.substring(0, 3) + "****" + p.substring(p.length() - 4);
        }

        return new UserPublicProfileDTO(
                user.getUsername(),
                user.resolveDisplayAvatarUrl(),
                publisherAvg,
                publisherCount,
                claimerAvg,
                claimerCount,
                claimedTaskCount,
                user.isRiderApproved(),
                "未知",
                maskedPhone,
                user.isCustomerServiceStaff()
        );
    }

    /** 公开：接单员收到的评价列表（最多 50 条） */
    public List<Map<String, Object>> listClaimerRatings(String username) {
        String normalized = username == null ? "" : username.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        userAccountRepository.findByUsername(normalized)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        List<DeliveryTask> rows = deliveryTaskRepository.findClaimerRatings(normalized);
        List<Map<String, Object>> out = new ArrayList<>();
        int n = 0;
        for (DeliveryTask t : rows) {
            if (n >= 50) break;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskId", t.getId());
            item.put("title", t.getTitle() == null ? "任务" : t.getTitle());
            item.put("rating", t.getClaimerRating());
            String comment = t.getClaimerRatingComment();
            item.put("comment", comment == null || comment.isBlank() ? null : comment.trim());
            item.put("publisherName", maskUsername(t.getPublisherName()));
            LocalDateTime at = t.getUpdatedAt() != null ? t.getUpdatedAt() : t.getCreatedAt();
            item.put("ratedAt", at == null ? null : at.toString());
            out.add(item);
            n++;
        }
        return out;
    }

    private static String maskUsername(String name) {
        if (name == null || name.isBlank()) return "***";
        String n = name.trim();
        if (n.length() <= 1) return "*";
        if (n.length() == 2) return n.charAt(0) + "*";
        return n.charAt(0) + "***" + n.charAt(n.length() - 1);
    }

    public Map<String, Object> changePassword(String currentUsername, ChangePasswordRequest request) {
        if (currentUsername == null || currentUsername.isBlank()) {
            throw new IllegalArgumentException("未登录");
        }
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }

        UserAccount user = userAccountRepository.findByUsername(currentUsername.trim())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        String oldPwd = request.getOldPassword() == null ? "" : request.getOldPassword().trim();
        String newPwd = request.getNewPassword() == null ? "" : request.getNewPassword().trim();

        if (oldPwd.isBlank() || newPwd.isBlank()) {
            throw new IllegalArgumentException("旧密码和新密码不能为空");
        }
        if (!passwordHasher.matches(oldPwd, user.getPassword())) {
            throw new IllegalArgumentException("旧密码不正确");
        }
        if (newPwd.length() < 6 || newPwd.length() > 32) {
            throw new IllegalArgumentException("新密码长度需在6-32位");
        }

        user.setPassword(passwordHasher.hash(newPwd));
        userAccountRepository.save(user);

        return Map.of("message", "密码修改成功");
    }

    private Map<String, Object> issueTokenAfterPasswordCheck(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }

        String raw = request.getAccount() == null ? "" : request.getAccount().trim();
        String password = request.getPassword() == null ? "" : request.getPassword().trim();

        if (raw.isBlank() || password.isBlank()) {
            throw new IllegalArgumentException("账号和密码不能为空");
        }

        UserAccount user = resolveUserForPasswordLogin(raw, password);

        if (!user.isEnabled()) {
            throw new IllegalStateException("账号已被管理员禁用，请联系管理员");
        }
        if (user.isCustomerServiceStaff() && !request.isCsConsole()) {
            throw new IllegalArgumentException("该账号为人工客服专用，请使用客服工作台登录");
        }
        // 接单员台：未认证不得签发会话，避免误改主站/其它端登录态
        if (LoginChannel.isRider(request.getClientChannel())) {
            if (!user.isRiderApproved()) {
                if (user.isRiderApplicationPending()) {
                    throw new IllegalArgumentException("接单员申请审核中，通过后再登录工作台");
                }
                throw new IllegalArgumentException("该账号尚未通过接单员认证，请先在用户端提交申请");
            }
        }

        return issueLoginToken(user, request.getClientChannel());
    }

    /**
     * 11 位注册手机号优先；纯数字为账号主键 ID；配置中的管理员登录名（如 admin）仍可用「用户名+密码」。
     */
    private UserAccount resolveUserForPasswordLogin(String rawAccount, String password) {
        String mobile = SmsCodeService.normalizeChinaMobile(rawAccount);
        if (mobile.matches("^1[3-9]\\d{9}$")) {
            List<UserAccount> list = userAccountRepository.findByPhone(mobile);
            List<UserAccount> matches = list.stream()
                    .filter(u -> passwordHasher.matches(password, u.getPassword()))
                    .toList();
            if (matches.isEmpty()) {
                throw new IllegalArgumentException("手机号或密码错误");
            }
            if (matches.size() > 1) {
                throw new IllegalArgumentException("该手机号下存在多个账号，请使用账号ID登录");
            }
            return upgradePasswordIfLegacy(matches.get(0), password);
        }

        String digits = rawAccount.trim();
        if (digits.matches("^\\d+$")) {
            long id = Long.parseLong(digits);
            UserAccount user = userAccountRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("账号ID或密码错误"));
            if (!passwordHasher.matches(password, user.getPassword())) {
                throw new IllegalArgumentException("账号ID或密码错误");
            }
            return upgradePasswordIfLegacy(user, password);
        }

        if (adminPolicy.isUsernamePasswordLoginAllowed(rawAccount)) {
            UserAccount user = userAccountRepository.findByUsernameIgnoreCase(rawAccount.trim())
                    .orElseThrow(() -> new IllegalArgumentException("账号或密码错误"));
            if (!passwordHasher.matches(password, user.getPassword())) {
                String tip = user.isCustomerServiceStaff() ? "客服账号或密码错误" : "管理员账号或密码错误";
                throw new IllegalArgumentException(tip);
            }
            return upgradePasswordIfLegacy(user, password);
        }

        throw new IllegalArgumentException("请使用账号ID、注册手机号或管理员/客服用户名登录");
    }

    /** 历史明文密码在登录成功后升级为 BCrypt，下次起库中不再可见明文。 */
    private UserAccount upgradePasswordIfLegacy(UserAccount user, String rawPassword) {
        if (!passwordHasher.needsRehash(user.getPassword())) {
            return user;
        }
        user.setPassword(passwordHasher.hash(rawPassword));
        return userAccountRepository.save(user);
    }
}
