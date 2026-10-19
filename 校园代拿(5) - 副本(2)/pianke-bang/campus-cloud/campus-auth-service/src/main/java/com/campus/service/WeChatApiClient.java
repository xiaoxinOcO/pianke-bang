package com.campus.service;

import com.campus.config.WeChatProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class WeChatApiClient {

    private static final Logger log = LoggerFactory.getLogger(WeChatApiClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WeChatProperties properties;
    private final HttpClient httpClient;
    private final AtomicReference<CachedToken> mpAccessToken = new AtomicReference<>();

    public WeChatApiClient(WeChatProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public record JsCodeSession(String openId, String unionId, String sessionKey) {
    }

    public record WebOauthToken(String openId, String unionId, String accessToken) {
    }

    public record WxacodeImage(byte[] bytes, String mimeType) {
    }

    public JsCodeSession code2Session(String jsCode) {
        WeChatProperties.Mp mp = properties.getMp();
        if (!mp.isConfigured()) {
            throw new IllegalStateException("小程序微信登录未配置 AppSecret，请设置环境变量 WECHAT_MP_APP_SECRET");
        }
        String url = UriComponentsBuilder
                .fromHttpUrl("https://api.weixin.qq.com/sns/jscode2session")
                .queryParam("appid", mp.getAppId())
                .queryParam("secret", mp.getAppSecret())
                .queryParam("js_code", jsCode)
                .queryParam("grant_type", "authorization_code")
                .toUriString();
        JsonNode node = getJson(url);
        assertNoErr(node, "小程序登录凭证无效");
        String openId = text(node, "openid");
        if (openId.isBlank()) {
            throw new IllegalArgumentException("微信未返回 openid");
        }
        return new JsCodeSession(openId, text(node, "unionid"), text(node, "session_key"));
    }

    public String getPhoneNumberByCode(String phoneCode) {
        if (phoneCode == null || phoneCode.isBlank()) {
            throw new IllegalArgumentException("缺少手机号授权码");
        }
        String token = getMpAccessToken();
        String url = "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=" + token;
        JsonNode node = postJson(url, Map.of("code", phoneCode.trim()));
        assertNoErr(node, "获取微信手机号失败");
        JsonNode info = node.path("phone_info");
        String phone = text(info, "purePhoneNumber");
        if (phone.isBlank()) {
            phone = text(info, "phoneNumber");
        }
        if (phone.isBlank()) {
            throw new IllegalArgumentException("微信未返回手机号，请重新授权");
        }
        return phone.replaceAll("\\D", "");
    }

    /**
     * 生成小程序码（scene 最长 32 字符），用于网页扫码登录。
     */
    public WxacodeImage createUnlimitedWxacode(String page, String scene) {
        WeChatProperties.Mp mp = properties.getMp();
        if (!mp.isConfigured()) {
            throw new IllegalStateException("小程序未配置 AppSecret");
        }
        String sceneVal = scene == null ? "" : scene.trim();
        if (sceneVal.isBlank() || sceneVal.length() > 32) {
            throw new IllegalArgumentException("小程序码 scene 无效");
        }
        String token = getMpAccessToken();
        String url = "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=" + token;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scene", sceneVal);
        body.put("page", page == null || page.isBlank() ? "pages/scan-login/scan-login" : page);
        body.put("check_path", false);
        body.put("env_version", mp.getEnvVersion());
        body.put("width", 280);
        byte[] bytes = postBytes(url, body);
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("微信未返回小程序码");
        }
        if (bytes.length > 2 && bytes[0] == '{') {
            JsonNode node = parse(new String(bytes, StandardCharsets.UTF_8));
            assertNoErr(node, "生成小程序码失败");
            throw new IllegalStateException("生成小程序码失败");
        }
        String mime = detectImageMime(bytes);
        return new WxacodeImage(bytes, mime);
    }

    public WebOauthToken exchangeWebOauthCode(String code) {
        WeChatProperties.Web web = properties.getWeb();
        if (!web.isOauthConfigured()) {
            throw new IllegalStateException("网站微信扫码未配置，请设置 WECHAT_WEB_APP_ID / WECHAT_WEB_APP_SECRET");
        }
        String url = UriComponentsBuilder
                .fromHttpUrl("https://api.weixin.qq.com/sns/oauth2/access_token")
                .queryParam("appid", web.getAppId())
                .queryParam("secret", web.getAppSecret())
                .queryParam("code", code)
                .queryParam("grant_type", "authorization_code")
                .toUriString();
        JsonNode node = getJson(url);
        assertNoErr(node, "微信扫码授权失败");
        String openId = text(node, "openid");
        if (openId.isBlank()) {
            throw new IllegalArgumentException("微信未返回网站 openid");
        }
        return new WebOauthToken(openId, text(node, "unionid"), text(node, "access_token"));
    }

    public String buildWebQrConnectUrl(String state) {
        WeChatProperties.Web web = properties.getWeb();
        String redirect = URLEncoder.encode(web.getRedirectUri(), StandardCharsets.UTF_8);
        String st = URLEncoder.encode(state == null ? "" : state, StandardCharsets.UTF_8);
        return "https://open.weixin.qq.com/connect/qrconnect"
                + "?appid=" + web.getAppId()
                + "&redirect_uri=" + redirect
                + "&response_type=code"
                + "&scope=snsapi_login"
                + "&state=" + st
                + "#wechat_redirect";
    }

    private String getMpAccessToken() {
        CachedToken cached = mpAccessToken.get();
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtMs() > now + 60_000) {
            return cached.token();
        }
        WeChatProperties.Mp mp = properties.getMp();
        String url = UriComponentsBuilder
                .fromHttpUrl("https://api.weixin.qq.com/cgi-bin/token")
                .queryParam("grant_type", "client_credential")
                .queryParam("appid", mp.getAppId())
                .queryParam("secret", mp.getAppSecret())
                .toUriString();
        JsonNode node = getJson(url);
        assertNoErr(node, "获取小程序 access_token 失败");
        String token = text(node, "access_token");
        if (token.isBlank()) {
            throw new IllegalStateException("微信未返回 access_token");
        }
        int expiresIn = node.path("expires_in").asInt(7200);
        mpAccessToken.set(new CachedToken(token, now + expiresIn * 1000L));
        return token;
    }

    private JsonNode getJson(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parse(resp.body());
        } catch (Exception ex) {
            log.warn("wechat GET failed: {}", ex.toString());
            throw new IllegalStateException("调用微信接口失败：" + ex.getMessage());
        }
    }

    private JsonNode postJson(String url, Object body) {
        try {
            String json = JSON.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parse(resp.body());
        } catch (Exception ex) {
            log.warn("wechat POST json failed: {}", ex.toString());
            throw new IllegalStateException("调用微信接口失败：" + ex.getMessage());
        }
    }

    private byte[] postBytes(String url, Object body) {
        try {
            String json = JSON.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() >= 400) {
                throw new IllegalStateException("微信接口 HTTP " + resp.statusCode());
            }
            return resp.body();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("wechat POST bytes failed: {}", ex.toString());
            throw new IllegalStateException("调用微信接口失败：" + ex.getMessage());
        }
    }

    private static String detectImageMime(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47) {
            return "image/png";
        }
        return "image/jpeg";
    }

    private JsonNode parse(String body) {
        try {
            return JSON.readTree(body == null ? "{}" : body);
        } catch (Exception ex) {
            log.warn("parse wechat json failed: {}", ex.toString());
            throw new IllegalStateException("微信接口响应解析失败");
        }
    }

    private void assertNoErr(JsonNode node, String fallback) {
        int err = node.path("errcode").asInt(0);
        if (err != 0) {
            String msg = text(node, "errmsg");
            log.warn("wechat api error errcode={} errmsg={}", err, msg);
            throw new IllegalArgumentException(msg.isBlank() ? fallback : (fallback + "：" + msg));
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText("").trim();
    }

    private record CachedToken(String token, long expiresAtMs) {
    }
}
