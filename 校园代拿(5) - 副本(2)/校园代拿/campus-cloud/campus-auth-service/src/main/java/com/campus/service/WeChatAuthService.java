package com.campus.service;

import com.campus.config.WeChatProperties;
import com.campus.dto.WeChatMpLoginRequest;
import com.campus.dto.WeChatQrConfirmPickRequest;
import com.campus.dto.WeChatQrConfirmSmsRequest;
import com.campus.model.UserAccount;
import com.campus.repository.UserAccountRepository;
import com.campus.security.JwtService;
import com.campus.security.LoginChannel;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class WeChatAuthService {

    private final WeChatProperties weChatProperties;
    private final WeChatApiClient weChatApiClient;
    private final WeChatQrSessionStore qrSessionStore;
    private final AuthService authService;
    private final UserAccountRepository userAccountRepository;
    private final SmsCodeService smsCodeService;

    public WeChatAuthService(
            WeChatProperties weChatProperties,
            WeChatApiClient weChatApiClient,
            WeChatQrSessionStore qrSessionStore,
            AuthService authService,
            UserAccountRepository userAccountRepository,
            SmsCodeService smsCodeService
    ) {
        this.weChatProperties = weChatProperties;
        this.weChatApiClient = weChatApiClient;
        this.qrSessionStore = qrSessionStore;
        this.authService = authService;
        this.userAccountRepository = userAccountRepository;
        this.smsCodeService = smsCodeService;
    }

    public Map<String, Object> status() {
        WeChatProperties.Mp mp = weChatProperties.getMp();
        WeChatProperties.Web web = weChatProperties.getWeb();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mpConfigured", mp.isConfigured());
        out.put("webOauthConfigured", web.isOauthConfigured());
        out.put("webScanAvailable", web.isEnabled() && mp.isConfigured());
        out.put("webScanMode", mp.isConfigured() ? "mp_phone" : (web.isOauthConfigured() ? "wechat_oauth" : "unavailable"));
        out.put("mpAppId", mp.getAppId());
        out.put("mpEnvVersion", mp.getEnvVersion());
        return out;
    }

    public Map<String, Object> createWebQrSession(String frontendBaseOverride) {
        WeChatProperties.Web web = weChatProperties.getWeb();
        WeChatProperties.Mp mp = weChatProperties.getMp();
        if (!web.isEnabled()) {
            throw new IllegalStateException("网页扫码登录未启用");
        }
        if (!mp.isConfigured()) {
            throw new IllegalStateException(
                    "扫码登录需要配置小程序 AppSecret（环境变量 WECHAT_MP_APP_SECRET）。"
                            + "扫码后在小程序内确认登录电脑端。"
            );
        }

        WeChatQrSessionStore.Session session = qrSessionStore.create("mp_phone", web.getSessionTtlSeconds());
        WeChatApiClient.WxacodeImage image = weChatApiClient.createUnlimitedWxacode(
                "pages/scan-login/scan-login",
                session.getId()
        );
        String base64 = java.util.Base64.getEncoder().encodeToString(image.bytes());
        String mime = image.mimeType() == null || image.mimeType().isBlank() ? "image/jpeg" : image.mimeType();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", session.getId());
        out.put("mode", "mp_phone");
        out.put("expiresIn", web.getSessionTtlSeconds());
        out.put("qrImageBase64", "data:" + mime + ";base64," + base64);
        out.put("hint", "请用微信扫一扫，在小程序内确认登录电脑端");
        return out;
    }

    public Map<String, Object> pollWebQrSession(String sessionId) {
        WeChatQrSessionStore.Session session = requireSession(sessionId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", session.getId());
        out.put("status", session.getStatus().name());
        out.put("mode", session.getMode());
        out.put("message", session.getMessage());
        if (session.getStatus() == WeChatQrSessionStore.Status.NEED_CHOOSE) {
            out.put("needChooseAccount", true);
            out.put("accounts", session.getAccounts());
            out.put("pickToken", session.getPickToken());
        }
        if (session.getStatus() == WeChatQrSessionStore.Status.NEED_BIND) {
            out.put("needBindPhone", true);
        }
        if (session.getStatus() == WeChatQrSessionStore.Status.SUCCESS) {
            out.put("token", session.getLoginToken());
            out.put("username", session.getUsername());
            out.put("message", session.getMessage().isBlank() ? "登录成功" : session.getMessage());
        }
        return out;
    }

    /**
     * 微信开放平台扫码回调：用 code 换 openid，已绑定则直接成功；否则进入待绑定手机号。
     */
    public String handleWebOauthCallback(String code, String state) {
        String frontendBase = weChatProperties.getWeb().getFrontendBaseUrl();
        if (frontendBase.isBlank()) {
            frontendBase = "https://xiaoxin1.xyz";
        }
        try {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("微信未返回授权码");
            }
            WeChatQrSessionStore.Session session = requireSession(state);
            WeChatApiClient.WebOauthToken oauth = weChatApiClient.exchangeWebOauthCode(code.trim());
            session.setWxWebOpenId(oauth.openId());
            session.setWxUnionId(oauth.unionId());

            Optional<UserAccount> bound = findBoundUser(oauth.openId(), oauth.unionId());
            if (bound.isPresent()) {
                applySuccess(session, authService.finalizeSmsLogin(bound.get()));
                return frontendBase + "/#/scan-login?ticket=" + session.getId() + "&done=1";
            }
            session.setStatus(WeChatQrSessionStore.Status.NEED_BIND);
            session.setMessage("首次微信登录，请绑定已注册手机号");
            return frontendBase + "/#/scan-login?ticket=" + session.getId() + "&bind=1";
        } catch (Exception ex) {
            String msg = URLEncoder.encode(
                    ex.getMessage() == null ? "微信登录失败" : ex.getMessage(),
                    StandardCharsets.UTF_8
            );
            String ticket = state == null ? "" : state.trim();
            if (!ticket.isBlank()) {
                qrSessionStore.get(ticket).ifPresent(s -> {
                    s.setStatus(WeChatQrSessionStore.Status.FAILED);
                    s.setMessage(ex.getMessage() == null ? "微信登录失败" : ex.getMessage());
                });
                return frontendBase + "/#/scan-login?ticket=" + ticket + "&error=" + msg;
            }
            return frontendBase + "/#/scan-login?error=" + msg;
        }
    }

    /**
     * 小程序已登录用户确认电脑端扫码登录（不依赖微信手机号能力）。
     * 电脑端签发网页会话；同时给小程序换发 mp 会话，避免旧「网页通道」token 被挤掉。
     */
    public Map<String, Object> confirmWebQrByLoggedInUser(String sessionId, String authorization) {
        Map<String, Object> me = authService.validateToken(authorization);
        String username = String.valueOf(me.get("username"));
        if (username.isBlank() || "null".equals(username)) {
            throw new IllegalArgumentException("登录态无效");
        }
        WeChatQrSessionStore.Session session = requireSession(sessionId);
        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (session.getStatus() != WeChatQrSessionStore.Status.SUCCESS) {
            // 给电脑端签发独立网页会话（只踢其他网页）
            Map<String, Object> webLogin = authService.issueLoginToken(user, LoginChannel.WEB);
            applySuccess(session, webLogin);
        }

        Map<String, Object> out = new LinkedHashMap<>(pollWebQrSession(session.getId()));
        // 给小程序换发/刷新 mp 会话并回传，客户端必须立刻保存，否则会掉线
        Map<String, Object> mpLogin = authService.issueLoginToken(user, LoginChannel.MP);
        out.put("mpToken", mpLogin.get("token"));
        return out;
    }

    public Map<String, Object> confirmWebQrBySms(WeChatQrConfirmSmsRequest request) {
        throw new IllegalStateException("网页扫码已改为小程序确认登录，不再使用短信确认");
    }

    /**
     * 小程序扫码确认：用户授权微信手机号后，登录对应账号并完成电脑端会话。
     */
    public Map<String, Object> confirmWebQrByMp(com.campus.dto.WeChatQrConfirmMpRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        WeChatQrSessionStore.Session session = requireSession(request.getSessionId());
        if (session.getStatus() == WeChatQrSessionStore.Status.SUCCESS) {
            return pollWebQrSession(session.getId());
        }

        WeChatMpLoginRequest mpReq = new WeChatMpLoginRequest();
        mpReq.setLoginCode(request.getLoginCode());
        mpReq.setPhoneCode(request.getPhoneCode());
        Map<String, Object> loginResult = loginByMiniProgram(mpReq, LoginChannel.WEB);

        if (Boolean.TRUE.equals(loginResult.get("needChooseAccount"))) {
            session.setStatus(WeChatQrSessionStore.Status.NEED_CHOOSE);
            session.setPickToken(String.valueOf(loginResult.getOrDefault("pickToken", "")));
            @SuppressWarnings("unchecked")
            var accounts = (java.util.List<Map<String, Object>>) loginResult.get("accounts");
            session.setAccounts(accounts);
            session.setWxUnionId(String.valueOf(loginResult.getOrDefault("wxUnionId", "")));
            // mp_phone 模式下复用 wxWebOpenId 字段暂存 mpOpenId，供多账号选择后绑定
            session.setWxWebOpenId(String.valueOf(loginResult.getOrDefault("wxMpOpenId", "")));
            session.setMessage(String.valueOf(loginResult.getOrDefault("message", "请选择要登录的账号")));
            Map<String, Object> out = pollWebQrSession(session.getId());
            out.put("wxMpOpenId", loginResult.get("wxMpOpenId"));
            out.put("wxUnionId", loginResult.get("wxUnionId"));
            return out;
        }

        applySuccess(session, loginResult);
        return pollWebQrSession(session.getId());
    }

    public Map<String, Object> confirmWebQrPick(WeChatQrConfirmPickRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("请求参数不完整");
        }
        WeChatQrSessionStore.Session session = requireSession(request.getSessionId());
        JwtService.SmsPickPayload payload = authService.parseSmsPickToken(
                request.getPickToken() == null ? "" : request.getPickToken()
        );
        if (!payload.allowedUserIds().contains(request.getUserId())) {
            throw new IllegalArgumentException("所选账号不在本次验证范围内");
        }
        UserAccount user = authService.requireUserById(request.getUserId());
        String userPhone = SmsCodeService.normalizeChinaMobile(user.getPhone() == null ? "" : user.getPhone());
        if (!userPhone.equals(payload.normalizedPhone())) {
            throw new IllegalArgumentException("账号信息已变更，请重新验证登录");
        }
        // mp_phone 会话里 wxWebOpenId 字段暂存的是 mpOpenId
        String mpOpenId = session.getWxWebOpenId();
        String unionId = session.getWxUnionId();
        authService.bindWeChatIdentity(
                user,
                null,
                mpOpenId.isBlank() ? null : mpOpenId,
                unionId.isBlank() ? null : unionId
        );
        Map<String, Object> ok = authService.finalizeSmsLogin(user);
        applySuccess(session, ok);
        return pollWebQrSession(session.getId());
    }

    public Map<String, Object> loginByMiniProgram(WeChatMpLoginRequest request) {
        return loginByMiniProgram(request, LoginChannel.MP);
    }

    public Map<String, Object> loginByMiniProgram(WeChatMpLoginRequest request, String clientChannel) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String loginCode = request.getLoginCode() == null ? "" : request.getLoginCode().trim();
        String phoneCode = request.getPhoneCode() == null ? "" : request.getPhoneCode().trim();
        if (loginCode.isBlank() || phoneCode.isBlank()) {
            throw new IllegalArgumentException("缺少微信登录凭证或手机号授权");
        }
        WeChatApiClient.JsCodeSession js = weChatApiClient.code2Session(loginCode);
        String phoneRaw = weChatApiClient.getPhoneNumberByCode(phoneCode);
        String phone = SmsCodeService.normalizeChinaMobile(phoneRaw);
        if (phone.isBlank() || phone.length() != 11) {
            throw new IllegalArgumentException("微信手机号格式不正确");
        }

        Optional<UserAccount> byWx = Optional.empty();
        if (!js.unionId().isBlank()) {
            byWx = userAccountRepository.findByWxUnionId(js.unionId());
        }
        if (byWx.isEmpty()) {
            byWx = userAccountRepository.findByWxMpOpenId(js.openId());
        }
        if (byWx.isPresent()) {
            UserAccount user = byWx.get();
            String boundPhone = SmsCodeService.normalizeChinaMobile(user.getPhone() == null ? "" : user.getPhone());
            if (!boundPhone.isBlank() && !boundPhone.equals(phone)) {
                // 微信身份已绑定其他手机号账号：以当前授权手机号为准重新匹配
                byWx = Optional.empty();
            } else {
                authService.bindWeChatIdentity(user, null, js.openId(), js.unionId());
                return authService.finalizeSmsLogin(user, clientChannel);
            }
        }

        Map<String, Object> result = authService.resolveLoginByPhone(phone, clientChannel);
        if (Boolean.TRUE.equals(result.get("needChooseAccount"))) {
            // 多账号：先返回选择列表；前端选定后再调 confirm，此处把 openid 放进 pick 流程较复杂，
            // 简化：返回 needChoose + 额外微信上下文由 confirm 接口绑定（见 mpConfirmPick）
            Map<String, Object> out = new LinkedHashMap<>(result);
            out.put("wxMpOpenId", js.openId());
            out.put("wxUnionId", js.unionId());
            out.put("phone", phone);
            return out;
        }
        // 单账号：绑定微信后返回 token
        String username = String.valueOf(result.get("username"));
        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        authService.bindWeChatIdentity(user, null, js.openId(), js.unionId());
        return result;
    }

    public Map<String, Object> confirmMiniProgramPick(String pickToken, long userId, String wxMpOpenId, String wxUnionId) {
        JwtService.SmsPickPayload payload = authService.parseSmsPickToken(pickToken);
        if (!payload.allowedUserIds().contains(userId)) {
            throw new IllegalArgumentException("所选账号不在本次验证范围内");
        }
        UserAccount user = authService.requireUserById(userId);
        String userPhone = SmsCodeService.normalizeChinaMobile(user.getPhone() == null ? "" : user.getPhone());
        if (!userPhone.equals(payload.normalizedPhone())) {
            throw new IllegalArgumentException("账号信息已变更，请重新授权登录");
        }
        authService.bindWeChatIdentity(user, null, wxMpOpenId, wxUnionId);
        return authService.finalizeSmsLogin(user, LoginChannel.MP);
    }

    private Map<String, Object> applyPhoneLoginResultToSession(
            WeChatQrSessionStore.Session session,
            Map<String, Object> loginResult
    ) {
        if (Boolean.TRUE.equals(loginResult.get("needChooseAccount"))) {
            session.setStatus(WeChatQrSessionStore.Status.NEED_CHOOSE);
            session.setPickToken(String.valueOf(loginResult.getOrDefault("pickToken", "")));
            @SuppressWarnings("unchecked")
            var accounts = (java.util.List<Map<String, Object>>) loginResult.get("accounts");
            session.setAccounts(accounts);
            session.setMessage(String.valueOf(loginResult.getOrDefault("message", "请选择要登录的账号")));
            return pollWebQrSession(session.getId());
        }
        String username = String.valueOf(loginResult.get("username"));
        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        bindSessionWeChatToUser(session, user);
        applySuccess(session, loginResult);
        return pollWebQrSession(session.getId());
    }

    private void bindSessionWeChatToUser(WeChatQrSessionStore.Session session, UserAccount user) {
        authService.bindWeChatIdentity(
                user,
                session.getWxWebOpenId().isBlank() ? null : session.getWxWebOpenId(),
                null,
                session.getWxUnionId().isBlank() ? null : session.getWxUnionId()
        );
    }

    private void applySuccess(WeChatQrSessionStore.Session session, Map<String, Object> loginResult) {
        session.setStatus(WeChatQrSessionStore.Status.SUCCESS);
        session.setLoginToken(String.valueOf(loginResult.getOrDefault("token", "")));
        session.setUsername(String.valueOf(loginResult.getOrDefault("username", "")));
        session.setMessage(String.valueOf(loginResult.getOrDefault("message", "登录成功")));
        session.setPickToken("");
        session.setAccounts(java.util.List.of());
    }

    private Optional<UserAccount> findBoundUser(String webOpenId, String unionId) {
        if (unionId != null && !unionId.isBlank()) {
            Optional<UserAccount> byUnion = userAccountRepository.findByWxUnionId(unionId.trim());
            if (byUnion.isPresent()) {
                return byUnion;
            }
        }
        if (webOpenId != null && !webOpenId.isBlank()) {
            return userAccountRepository.findByWxWebOpenId(webOpenId.trim());
        }
        return Optional.empty();
    }

    private WeChatQrSessionStore.Session requireSession(String sessionId) {
        WeChatQrSessionStore.Session session = qrSessionStore.get(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("扫码会话不存在或已失效，请刷新二维码"));
        if (session.getStatus() == WeChatQrSessionStore.Status.SUCCESS) {
            return session;
        }
        if (session.getStatus() == WeChatQrSessionStore.Status.EXPIRED || session.isExpired()) {
            session.setStatus(WeChatQrSessionStore.Status.EXPIRED);
            throw new IllegalArgumentException("二维码已过期，请刷新后重试");
        }
        return session;
    }

    private String resolveFrontendBase(String override) {
        if (override != null && !override.isBlank()) {
            String o = override.trim().replaceAll("/$", "");
            if (o.startsWith("http://") || o.startsWith("https://")) {
                return o;
            }
        }
        String configured = weChatProperties.getWeb().getFrontendBaseUrl();
        if (!configured.isBlank()) {
            return configured;
        }
        return "https://xiaoxin1.xyz";
    }
}
