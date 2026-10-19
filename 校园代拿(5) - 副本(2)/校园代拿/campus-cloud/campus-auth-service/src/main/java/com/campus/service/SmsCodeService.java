package com.campus.service;

import com.aliyun.sdk.service.dypnsapi20170525.AsyncClient;
import com.aliyun.sdk.service.dypnsapi20170525.models.CheckSmsVerifyCodeRequest;
import com.aliyun.sdk.service.dypnsapi20170525.models.CheckSmsVerifyCodeResponse;
import com.aliyun.sdk.service.dypnsapi20170525.models.CheckSmsVerifyCodeResponseBody;
import com.aliyun.sdk.service.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.sdk.service.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.sdk.service.dypnsapi20170525.models.SendSmsVerifyCodeResponseBody;
import com.campus.config.AliyunDypnsProperties;
import com.campus.model.SmsVerificationCode;
import com.campus.repository.SmsVerificationCodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class SmsCodeService {

    /**
     * 阿里云返回「签名/模板」类错误时使用，便于带扩展码重试逻辑识别（用户文案中不再包含「签名」等字样）。
     */
    private static final class AliyunSignOrTemplateLikeException extends IllegalArgumentException {
        AliyunSignOrTemplateLikeException(String userMessage) {
            super(userMessage);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(SmsCodeService.class);

    private static final int EXPIRE_MINUTES = 5;
    private static final int RESEND_SECONDS = 60;
    /** 阿里云 SDK 异步调用在 Tomcat 线程上等待的上限，避免注册/发码一直挂死 */
    private static final long ALIYUN_API_TIMEOUT_SECONDS = 25;

    private final SmsVerificationCodeRepository repository;
    private final Random random = new Random();
    private final AliyunDypnsProperties aliyunDypnsProperties;
    private final Optional<AsyncClient> dypnsClient;

    public SmsCodeService(
            SmsVerificationCodeRepository repository,
            AliyunDypnsProperties aliyunDypnsProperties,
            Optional<AsyncClient> dypnsClient
    ) {
        this.repository = repository;
        this.aliyunDypnsProperties = aliyunDypnsProperties;
        this.dypnsClient = dypnsClient;
    }

    /** 是否走阿里云真实短信（否则仅写库，不发短信） */
    public boolean usesAliyunSms() {
        return useAliyun();
    }

    /** 发送成功后的提示文案（注册页展示） */
    public String sendCodeSuccessMessage() {
        if (useAliyun()) {
            return "验证码已发送，请注意查收短信（约 5 分钟内有效）。";
        }
        return "验证码已生成（本地调试未发短信）。可在数据库表 sms_verification_code 查看 code 字段。";
    }

    /**
     * 与注册/校验共用：去空格，支持 +86 / 86 前缀，便于与库内 11 位号一致统计。
     */
    public static String normalizeChinaMobile(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().replaceAll("\\s+", "").replace("-", "");
        if (s.startsWith("+86")) {
            s = s.substring(3).trim();
        } else if (s.length() == 13 && s.startsWith("86") && s.charAt(2) == '1') {
            s = s.substring(2);
        }
        return s;
    }

    private static String requireChinaMobile(String phone) {
        String p = normalizeChinaMobile(phone);
        if (!p.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        return p;
    }

    @Transactional
    public void sendCode(String phone) {
        String p = requireChinaMobile(phone);
        if (useAliyun()) {
            sendCodeAliyun(p);
            return;
        }
        sendCodeLocalDb(p);
    }

    @Transactional
    public void verifyCode(String phone, String code) {
        String p = requireChinaMobile(phone);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("验证码不能为空");
        }
        if (useAliyun()) {
            verifyCodeAliyun(p, code.trim());
            return;
        }
        verifyCodeLocalDb(p, code.trim());
    }

    private boolean useAliyun() {
        return aliyunDypnsProperties.isEnabled() && dypnsClient.isPresent();
    }

    private void sendCodeAliyun(String phone) {
        AsyncClient client = dypnsClient.orElseThrow(() -> new IllegalArgumentException("阿里云短信客户端未初始化"));
        String extendRaw = aliyunDypnsProperties.getSmsUpExtendCode() == null ? ""
                : aliyunDypnsProperties.getSmsUpExtendCode().trim();
        boolean willAttachExtend = aliyunDypnsProperties.isSendSmsUpExtendCode() && !extendRaw.isEmpty();

        try {
            invokeSendSmsVerifyCode(client, phone, willAttachExtend);
        } catch (IllegalArgumentException e) {
            if (willAttachExtend && (e instanceof AliyunSignOrTemplateLikeException
                    || isLikelySignOrTemplateError(e.getMessage()))) {
                log.warn("带扩展码发送失败，将去掉 SmsUpExtendCode 再试一次。原因: {}", e.getMessage());
                invokeSendSmsVerifyCode(client, phone, false);
            } else {
                throw e;
            }
        }
    }

    private void invokeSendSmsVerifyCode(AsyncClient client, String phone, boolean useConfiguredSmsUpExtend) {
        String schemePre = trimToEmpty(aliyunDypnsProperties.getSchemeName());
        String signPre = trimToEmpty(aliyunDypnsProperties.getSignName());
        String tplCodePre = trimToEmpty(aliyunDypnsProperties.getTemplateCode());
        if (schemePre.isEmpty() || signPre.isEmpty() || tplCodePre.isEmpty()) {
            throw new IllegalArgumentException("请在 application 中配置 app.aliyun.dypns.scheme-name、sign-name、template-code");
        }

        String outId = aliyunDypnsProperties.getOutId() == null || aliyunDypnsProperties.getOutId().isBlank()
                ? "reg-" + UUID.randomUUID()
                : aliyunDypnsProperties.getOutId().trim();

        String cc = aliyunDypnsProperties.getCountryCode() == null ? "86"
                : aliyunDypnsProperties.getCountryCode().trim();
        if (cc.isEmpty()) {
            cc = "86";
        }

        String tplParam = aliyunDypnsProperties.getTemplateParam() == null ? ""
                : aliyunDypnsProperties.getTemplateParam().trim();
        if (tplParam.isEmpty()) {
            tplParam = "{\"code\":\"##code##\",\"min\":\"5\"}";
        }

        SendSmsVerifyCodeRequest.Builder b = SendSmsVerifyCodeRequest.builder()
                .schemeName(schemePre)
                .countryCode(cc)
                .phoneNumber(phone)
                .signName(signPre)
                .templateCode(tplCodePre)
                .templateParam(tplParam)
                .outId(outId)
                .codeLength(aliyunDypnsProperties.getCodeLength())
                .validTime(aliyunDypnsProperties.getValidTime())
                .duplicatePolicy(aliyunDypnsProperties.getDuplicatePolicy())
                .interval(aliyunDypnsProperties.getInterval())
                .codeType(aliyunDypnsProperties.getCodeType())
                .returnVerifyCode(aliyunDypnsProperties.isReturnVerifyCode());

        String extend = aliyunDypnsProperties.getSmsUpExtendCode() == null ? ""
                : aliyunDypnsProperties.getSmsUpExtendCode().trim();
        if (useConfiguredSmsUpExtend && !extend.isEmpty()) {
            b.smsUpExtendCode(extend);
        }

        String extendLog;
        if (useConfiguredSmsUpExtend && !extend.isEmpty()) {
            extendLog = extend;
        } else if (!extend.isEmpty() && !aliyunDypnsProperties.isSendSmsUpExtendCode()) {
            extendLog = "(已配置但被 send-sms-up-extend-code=false 忽略)";
        } else {
            extendLog = "(未传)";
        }

        log.info(
                "调用 SendSmsVerifyCode: region(见配置) schemeName={} signName={} templateCode={} countryCode={} templateParam={} outId={} smsUpExtendCode={}",
                schemePre,
                signPre,
                tplCodePre,
                cc,
                tplParam,
                outId,
                extendLog
        );

        try {
            SendSmsVerifyCodeResponse resp = client.sendSmsVerifyCode(b.build())
                    .get(ALIYUN_API_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertSendOk(resp);
        } catch (TimeoutException e) {
            throw new IllegalArgumentException("短信发送超时：请检查服务器出网、安全组是否放行访问阿里云，或稍后重试", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("短信发送被中断", e);
        } catch (ExecutionException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            log.warn("阿里云 SendSmsVerifyCode SDK/网络异常", c);
            throw new IllegalArgumentException(friendlyUserMessageForDypnsThrowable(c, false));
        }
    }

    private static boolean isLikelySignOrTemplateError(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("签名") || message.contains("模板") || message.contains("模版");
    }

    private static String collectThrowableMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        while (t != null) {
            if (t.getMessage() != null) {
                sb.append(t.getMessage()).append(' ');
            }
            t = t.getCause();
        }
        return sb.toString();
    }

    /**
     * 面向用户的短文案；完整错误已打日志，勿再把 RequestId/SDK 细节返回给前端。
     */
    private static String friendlyUserMessageForDypnsThrowable(Throwable cause, boolean verifyingCode) {
        return mapDypnsRawToUser(collectThrowableMessages(cause), verifyingCode);
    }

    private static String friendlyUserMessageForDypnsApi(String apiCode, String apiMessage, boolean verifyingCode) {
        return mapDypnsRawToUser(
                (apiCode == null ? "" : apiCode) + " " + (apiMessage == null ? "" : apiMessage),
                verifyingCode
        );
    }

    private static String mapDypnsRawToUser(String raw, boolean verifyingCode) {
        if (raw == null || raw.isBlank()) {
            return verifyingCode
                    ? "验证码校验未通过，请稍后重试或重新获取验证码。"
                    : "短信发送失败，请稍后重试。";
        }
        String s = raw;
        String lower = s.toLowerCase(Locale.ROOT);
        if (s.contains("isv.ValidateFail")
                || lower.contains("validatefail")
                || (s.contains("验证失败") && (lower.contains("code:") || s.contains("400")))) {
            return verifyingCode
                    ? "验证码不正确或已过期，请重新获取短信验证码后再试。"
                    : "短信验证未通过，请稍后重试。";
        }
        if (lower.contains("throttling") || s.contains("流控") || s.contains("QPS") || s.contains("频率")) {
            return "操作过于频繁，请稍后再试。";
        }
        if (s.contains("InvalidAccessKeyId")
                || s.contains("SignatureDoesNotMatch")
                || s.contains("not authorized")
                || lower.contains("accessdenied")) {
            return "短信服务暂时不可用，请稍后再试。若多次失败请联系管理员。";
        }
        if (s.contains("签名") || s.contains("模板") || s.contains("模版")) {
            return verifyingCode
                    ? "短信通道暂无法完成验证，请稍后重试或联系管理员。"
                    : "短信通道暂无法发送验证码，请稍后重试或联系管理员。";
        }
        if (lower.contains("timeout") || s.contains("连接超时") || s.contains("Connection reset")) {
            return verifyingCode ? "校验验证码时网络异常，请稍后重试。" : "发送短信时网络异常，请稍后重试。";
        }
        return verifyingCode
                ? "验证码校验未通过，请确认输入或重新获取验证码。"
                : "短信发送失败，请稍后重试。若多次失败请联系管理员。";
    }

    private static void assertSendOk(SendSmsVerifyCodeResponse resp) {
        if (resp == null) {
            throw new IllegalArgumentException("短信发送失败，请稍后重试。");
        }
        SendSmsVerifyCodeResponseBody body = resp.getBody();
        if (body == null) {
            throw new IllegalArgumentException("短信发送失败，请稍后重试。");
        }
        if (!"OK".equalsIgnoreCase(body.getCode())) {
            String reqId = "";
            if (body.getModel() != null && body.getModel().getRequestId() != null) {
                reqId = body.getModel().getRequestId().trim();
            }
            log.warn(
                    "阿里云 SendSmsVerifyCode 失败 code={} message={} requestId={} accessDeniedDetail={}",
                    body.getCode(),
                    body.getMessage(),
                    reqId.isEmpty() ? "-" : reqId,
                    body.getAccessDeniedDetail()
            );
            String apiMsg = body.getMessage() != null ? body.getMessage() : "";
            String userMsg = friendlyUserMessageForDypnsApi(body.getCode(), body.getMessage(), false);
            if (isLikelySignOrTemplateError(apiMsg)) {
                throw new AliyunSignOrTemplateLikeException(userMsg);
            }
            throw new IllegalArgumentException(userMsg);
        }
    }

    private void verifyCodeAliyun(String phone, String code) {
        AsyncClient client = dypnsClient.orElseThrow(() -> new IllegalArgumentException("阿里云短信客户端未初始化"));

        String cc = aliyunDypnsProperties.getCountryCode() == null ? "86"
                : aliyunDypnsProperties.getCountryCode().trim();
        if (cc.isEmpty()) {
            cc = "86";
        }

        CheckSmsVerifyCodeRequest req = CheckSmsVerifyCodeRequest.builder()
                .schemeName(trimToEmpty(aliyunDypnsProperties.getSchemeName()))
                .countryCode(cc)
                .phoneNumber(phone)
                .verifyCode(code)
                .build();

        try {
            CheckSmsVerifyCodeResponse resp = client.checkSmsVerifyCode(req)
                    .get(ALIYUN_API_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertVerifyOk(resp);
        } catch (TimeoutException e) {
            throw new IllegalArgumentException("验证码校验超时：请检查服务器出网或稍后重试（注册请求不宜长时间无响应）", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("验证码校验被中断", e);
        } catch (ExecutionException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            log.warn("阿里云 CheckSmsVerifyCode SDK/网络异常", c);
            throw new IllegalArgumentException(friendlyUserMessageForDypnsThrowable(c, true));
        }
    }

    private static void assertVerifyOk(CheckSmsVerifyCodeResponse resp) {
        if (resp == null) {
            throw new IllegalArgumentException("验证码不正确或已过期，请重新获取短信验证码后再试。");
        }
        CheckSmsVerifyCodeResponseBody body = resp.getBody();
        if (body == null) {
            throw new IllegalArgumentException("验证码不正确或已过期，请重新获取短信验证码后再试。");
        }
        if (!"OK".equalsIgnoreCase(body.getCode())) {
            log.warn(
                    "阿里云 CheckSmsVerifyCode 业务未成功 code={} message={}",
                    body.getCode(),
                    body.getMessage()
            );
            throw new IllegalArgumentException(friendlyUserMessageForDypnsApi(body.getCode(), body.getMessage(), true));
        }
        var model = body.getModel();
        if (model == null || model.getVerifyResult() == null
                || !"PASS".equalsIgnoreCase(model.getVerifyResult().trim())) {
            log.warn(
                    "阿里云 CheckSmsVerifyCode 校验未通过 verifyResult={}",
                    model == null || model.getVerifyResult() == null ? "(null)" : model.getVerifyResult()
            );
            throw new IllegalArgumentException("验证码不正确或已过期，请重新获取短信验证码后再试。");
        }
    }

    private void sendCodeLocalDb(String phone) {
        LocalDateTime now = LocalDateTime.now();

        repository.findTopByPhoneOrderByIdDesc(phone).ifPresent(latest -> {
            if (latest.getCreatedAt().plusSeconds(RESEND_SECONDS).isAfter(now)) {
                long wait = java.time.Duration.between(now, latest.getCreatedAt().plusSeconds(RESEND_SECONDS)).getSeconds();
                throw new IllegalStateException("发送过于频繁，请" + Math.max(wait, 1) + "秒后再试");
            }
        });

        String dbCode = String.format("%06d", random.nextInt(1_000_000));
        SmsVerificationCode row = new SmsVerificationCode();
        row.setPhone(phone);
        row.setCode(dbCode);
        row.setExpireAt(now.plusMinutes(EXPIRE_MINUTES));
        row.setConsumed(false);
        repository.save(row);
        log.warn(
                "【本地短信模式】未向运营商发送短信。手机号 {} 的验证码已写入表 sms_verification_code，code={}（5分钟内有效，调试用）",
                phone,
                dbCode
        );
    }

    private void verifyCodeLocalDb(String phone, String code) {
        SmsVerificationCode row = repository
                .findFirstValid(phone, code, LocalDateTime.now())
                .orElseThrow(() -> new IllegalArgumentException("验证码错误或已过期，请重新发送"));

        row.setConsumed(true);
        repository.save(row);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
