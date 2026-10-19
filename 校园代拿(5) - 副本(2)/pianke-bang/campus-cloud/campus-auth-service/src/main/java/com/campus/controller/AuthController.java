package com.campus.controller;

import com.campus.dto.ChangePasswordRequest;
import com.campus.dto.ForgotPasswordResetRequest;
import com.campus.dto.ForgotPasswordVerifyRequest;
import com.campus.dto.LoginBySmsConfirmRequest;
import com.campus.dto.LoginBySmsRequest;
import com.campus.dto.LoginRequest;
import com.campus.dto.RegisterBySmsRequest;
import com.campus.dto.RegisterRequest;
import com.campus.dto.RiderApplyRequest;
import com.campus.dto.SmsSendRequest;
import com.campus.dto.UpdateProfileRequest;
import com.campus.dto.UserPublicProfileDTO;
import com.campus.dto.WeChatMpConfirmPickRequest;
import com.campus.dto.WeChatMpLoginRequest;
import com.campus.dto.WeChatQrConfirmPickRequest;
import com.campus.dto.WeChatQrConfirmSmsRequest;
import com.campus.service.AuthService;
import com.campus.service.AvatarScanTempStore;
import com.campus.service.WeChatAuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final WeChatAuthService weChatAuthService;
    private final AvatarScanTempStore avatarScanTempStore;

    public AuthController(
            AuthService authService,
            WeChatAuthService weChatAuthService,
            AvatarScanTempStore avatarScanTempStore
    ) {
        this.authService = authService;
        this.weChatAuthService = weChatAuthService;
        this.avatarScanTempStore = avatarScanTempStore;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/sms/send")
    public Map<String, Object> sendSms(@RequestBody SmsSendRequest request) {
        return authService.sendRegisterSmsCode(request.getPhone());
    }

    @PostMapping("/sms/send-login")
    public Map<String, Object> sendLoginSms(@RequestBody SmsSendRequest request) {
        return authService.sendLoginSmsCode(request.getPhone());
    }

    @PostMapping("/forgot-password/sms/send")
    public Map<String, Object> sendForgotPasswordSms(@RequestBody SmsSendRequest request) {
        return authService.sendForgotPasswordSms(request.getPhone());
    }

    @PostMapping("/forgot-password/verify-sms")
    public Map<String, Object> verifyForgotPasswordSms(@RequestBody ForgotPasswordVerifyRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        return authService.verifyForgotPasswordSms(request.getPhone(), request.getCode());
    }

    @PostMapping("/forgot-password/reset")
    public Map<String, Object> resetPasswordBySms(@RequestBody ForgotPasswordResetRequest request) {
        return authService.resetPasswordBySms(request);
    }

    @PostMapping("/login-by-sms")
    public Map<String, Object> loginBySms(@RequestBody LoginBySmsRequest request) {
        return authService.loginBySms(request);
    }

    @PostMapping("/login-by-sms/confirm")
    public Map<String, Object> loginBySmsConfirm(@RequestBody LoginBySmsConfirmRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("请求参数不完整");
        }
        return authService.confirmLoginBySmsPick(
                request.getPickToken() == null ? "" : request.getPickToken(),
                request.getUserId(),
                request.getClientChannel()
        );
    }

    @PostMapping("/register-by-sms")
    public Map<String, Object> registerBySms(@RequestBody RegisterBySmsRequest request) {
        return authService.registerBySms(request);
    }

    @GetMapping("/wechat/status")
    public Map<String, Object> wechatStatus() {
        return weChatAuthService.status();
    }

    /** 电脑端：创建扫码登录会话（返回二维码内容） */
    @PostMapping("/wechat/web/qr-session")
    public Map<String, Object> createWechatQrSession(@RequestBody(required = false) Map<String, String> body) {
        String frontendBase = body == null ? null : body.get("frontendBaseUrl");
        return weChatAuthService.createWebQrSession(frontendBase);
    }

    /** 电脑端轮询扫码结果 */
    @GetMapping("/wechat/web/qr-session/{sessionId}")
    public Map<String, Object> pollWechatQrSession(@PathVariable("sessionId") String sessionId) {
        return weChatAuthService.pollWebQrSession(sessionId);
    }

    /** 微信开放平台扫码回调 */
    @GetMapping("/wechat/web/oauth-callback")
    public ResponseEntity<Void> wechatOauthCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state
    ) {
        String redirect = weChatAuthService.handleWebOauthCallback(code, state);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirect)
                .build();
    }

    /** 小程序已登录：确认电脑端扫码登录 */
    @PostMapping("/wechat/web/confirm-login")
    public Map<String, Object> wechatConfirmLogin(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody com.campus.dto.WeChatQrConfirmLoginRequest request
    ) {
        if (request == null || request.getSessionId() == null || request.getSessionId().isBlank()) {
            throw new IllegalArgumentException("缺少扫码会话");
        }
        return weChatAuthService.confirmWebQrByLoggedInUser(request.getSessionId(), authorization);
    }

    /** 手机确认页：短信验证并确认电脑端扫码会话（已弃用，保留兼容） */
    @PostMapping("/wechat/web/confirm-sms")
    public Map<String, Object> wechatConfirmSms(@RequestBody WeChatQrConfirmSmsRequest request) {
        return weChatAuthService.confirmWebQrBySms(request);
    }

    /** 小程序扫码：授权微信手机号后确认电脑端登录（账号无手机号能力时不可用） */
    @PostMapping("/wechat/web/confirm-mp")
    public Map<String, Object> wechatConfirmMp(@RequestBody com.campus.dto.WeChatQrConfirmMpRequest request) {
        return weChatAuthService.confirmWebQrByMp(request);
    }

    /** 手机确认页：多账号择一 */
    @PostMapping("/wechat/web/confirm-pick")
    public Map<String, Object> wechatConfirmPick(@RequestBody WeChatQrConfirmPickRequest request) {
        return weChatAuthService.confirmWebQrPick(request);
    }

    /** 小程序：微信登录 + 授权手机号 */
    @PostMapping("/wechat/mp/login")
    public Map<String, Object> wechatMpLogin(@RequestBody WeChatMpLoginRequest request) {
        return weChatAuthService.loginByMiniProgram(request);
    }

    /** 小程序：多账号择一并绑定微信 */
    @PostMapping("/wechat/mp/confirm-pick")
    public Map<String, Object> wechatMpConfirmPick(@RequestBody WeChatMpConfirmPickRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("请求参数不完整");
        }
        return weChatAuthService.confirmMiniProgramPick(
                request.getPickToken() == null ? "" : request.getPickToken(),
                request.getUserId(),
                request.getWxMpOpenId(),
                request.getWxUnionId()
        );
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return authService.validateToken(authorization);
    }

    @PostMapping("/offline")
    public Map<String, Object> markOffline(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return authService.markOffline(authorization);
    }

    @GetMapping("/users/{username}/public-profile")
    public UserPublicProfileDTO userPublicProfile(@PathVariable("username") String username) {
        return authService.getUserPublicProfile(username);
    }

    /** 公开：接单员评价列表 */
    @GetMapping("/users/{username}/claimer-ratings")
    public List<Map<String, Object>> claimerRatings(@PathVariable("username") String username) {
        return authService.listClaimerRatings(username);
    }

    /**
     * 公开短时接口：供阿里云 ScanImage 拉取 data:image 头像。
     * token 约 2 分钟失效，网关已放行。
     */
    @GetMapping("/avatar-scan-temp/{token}")
    public ResponseEntity<byte[]> avatarScanTemp(@PathVariable("token") String token) {
        AvatarScanTempStore.TempImage image = avatarScanTempStore.get(token);
        if (image == null) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(image.contentType());
        } catch (Exception ex) {
            mediaType = MediaType.IMAGE_JPEG;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(image.bytes());
    }

    @PutMapping("/profile")
    public Map<String, Object> updateProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody UpdateProfileRequest request
    ) {
        String username = String.valueOf(authService.validateToken(authorization).get("username"));
        return authService.updateProfile(username, request);
    }

    @PostMapping("/rider/apply")
    public Map<String, Object> applyRider(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody RiderApplyRequest request
    ) {
        String username = String.valueOf(authService.validateToken(authorization).get("username"));
        return authService.applyRider(username, request);
    }

    @PutMapping("/password")
    public Map<String, Object> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ChangePasswordRequest request
    ) {
        String username = String.valueOf(authService.validateToken(authorization).get("username"));
        return authService.changePassword(username, request);
    }
}
