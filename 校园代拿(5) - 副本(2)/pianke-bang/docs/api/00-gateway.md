# 00 · 网关与通用约定

## 基本信息

| 项 | 值 |
|----|-----|
| 网关 | `campus-gateway` |
| 端口 | `8080` |
| 协议 | HTTP / WebSocket |
| 前端开发代理 | Vite 将 `/api`、`/ws` 代理到 `8080` |
| 环境变量示例 | `VITE_API_ORIGIN=http://localhost:8080` |

## 微服务端口（直连仅联调，生产勿暴露）

| 服务 | 端口 | 职责 |
|------|------|------|
| campus-gateway | 8080 | 路由、JWT、CORS |
| campus-auth-service | 8101 | 认证 |
| campus-user-service | 8102 | 管理端用户 / 客服分配 |
| campus-task-service | 8103 | 任务 |
| campus-audit-service | 8104 | 公告、客服 AI、运营 |
| campus-chat-service | 8105 | 聊天 / WS |
| campus-wallet-service | 8106 | 钱包 / 券 / 活动 |

## 鉴权

### Header

```
Authorization: Bearer <jwt>
```

登录成功后响应中的 token 即 JWT。多数业务接口需携带；网关校验后向前端下游透传用户头（如 `X-Username`、`X-User-Id`）。

### 公开前缀（无需 JWT）

| 前缀 | 说明 |
|------|------|
| `/api/auth/login` | 密码登录 |
| `/api/auth/register` | 注册（含短信注册路径见 01） |
| `/api/auth/sms/` | 发短信 |
| `/api/auth/forgot-password/` | 找回密码 |
| `/api/auth/login-by-sms` | 短信登录 |
| `/api/auth/wechat/` | 微信相关 |
| `/api/auth/avatar-scan-temp/` | 头像临时资源 |
| `/api/notices` | 已发布公告 |
| `/api/coupons/activities` | 优惠活动列表 |
| `/api/hall-activities` | 大厅活动 |
| `/api/tasks/hall-stats` | 大厅统计 |
| `/api/wallet/alipay/notify` | 支付宝异步通知 |
| `/api/wallet/alipay/return` | 支付宝同步回跳 |

### Auth 自校验前缀

以下路径网关不二次 `validate-session`，由 auth-service 自行校验会话：

- `/api/auth/me`
- `/api/auth/offline`
- `/api/auth/profile`
- `/api/auth/password`
- `/api/auth/rider/`
- `/api/auth/users/`

### 内部接口

- 前缀：`/internal/**`
- 服务间调用，需内部 token（`CAMPUS_INTERNAL_TOKEN`）
- **经网关访问会被拦截（403）**

## 网关路由（摘要）

| 路径 | 目标服务 |
|------|----------|
| `/api/auth/**` | auth |
| `/api/tasks/**` | task |
| `/api/admin/tasks/**` | task |
| `/api/admin/ai/**`（除 config/test） | task |
| `/api/admin/ai/config`、`/api/admin/ai/test` | audit |
| `/api/admin/users/**`、`/api/admin/customer-service/**` | user |
| `/api/admin/users/*/wallet/**` | wallet |
| `/api/admin/coupons/**`、`/api/admin/hall-activities/**` | wallet |
| `/api/admin/**`（其余） | audit |
| `/api/support/assign-agent`、`/api/support/agent-availability` | user |
| `/api/support/**`（其余） | audit |
| `/api/chat/**`、`/ws/**` | chat |
| `/api/wallet/**` | wallet |
| `/api/coupons/**`、`/api/hall-activities/**` | wallet |
| `/api/notices/**`、`/api/system-messages/**`、`/api/feedback/**` | audit |

路由顺序敏感：更具体的 `/api/admin/...` 规则优先于 catch-all `/api/admin/**`。

## 限流

Sentinel 网关流控触发时返回 HTTP `429`：

```json
{"success":false,"message":"请求过于频繁，请稍后再试"}
```

## 通用约定

1. **Content-Type**：JSON 接口使用 `application/json`；支付宝 notify 为 `application/x-www-form-urlencoded`。
2. **错误**：业务异常多为 HTTP 4xx + 文案；未登录常见 `401`。
3. **分页**：常见 `page`（从 1）、`size`。
4. **金额**：`BigDecimal`，单位元。
5. **时间**：ISO-8601 本地时间（如 `2026-10-16T12:00:00`）。

## 客户端路径习惯

| 客户端 | Base |
|--------|------|
| frontend / rider / cs-console | `${VITE_API_ORIGIN}/api/...` |
| 小程序 | `${apiOrigin}/api` + 相对路径如 `/auth/login` |
| WebSocket | `ws://localhost:8080/ws`（或同源 `/ws`） |
