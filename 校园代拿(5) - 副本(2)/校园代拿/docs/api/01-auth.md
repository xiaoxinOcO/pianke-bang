# 认证与用户

[← 返回目录](./README.md)

| | |
|:---|:---|
| **服务** | `campus-auth-service` |
| **端口** | `8101` |
| **前缀** | `/api/auth` |
| **网关** | `http://localhost:8080` |

> 公开接口无需 JWT；需登录接口请带  
> `Authorization: Bearer <token>`  
> 白名单详见 [00 · 网关约定](./00-gateway.md)  
> 密码：库内 BCrypt 哈希（不可逆）；历史明文账号在下次登录成功后自动升级为哈希。

---

## 目录

1. [注册 / 登录](#1-注册--登录)
2. [短信验证码](#2-短信验证码)
3. [找回密码](#3-找回密码)
4. [微信登录](#4-微信登录)
5. [当前用户 / 资料](#5-当前用户--资料)
6. [公开资料与头像](#6-公开资料与头像)

---

## 1. 注册 / 登录

### `POST` `/api/auth/register`

| 项 | 内容 |
|:---|:---|
| 鉴权 | 公开 |
| 说明 | 账号密码注册 |
| Body | `RegisterRequest` |

**请求字段**

| 字段 | 类型 | 说明 |
|:---|:---|:---|
| `username` | string | 用户名 |
| `password` | string | 密码 |
| `realName` | string | 真实姓名 |
| `studentId` | string | 学号 |
| `gender` | string | 性别 |
| `phone` | string | 手机号 |

**请求示例**

```json
{
  "username": "zhangsan",
  "password": "Passw0rd!",
  "realName": "张三",
  "studentId": "20210001",
  "gender": "男",
  "phone": "13800138000"
}
```

**响应**：`Map`（含 `token`、用户信息等）

---

### `POST` `/api/auth/login`

| 项 | 内容 |
|:---|:---|
| 鉴权 | 公开 |
| 说明 | 账号密码登录 |
| Body | `LoginRequest` |

**请求字段**

| 字段 | 类型 | 说明 |
|:---|:---|:---|
| `account` | string | 用户名 / 手机号等 |
| `password` | string | 密码 |
| `csConsole` | boolean | 是否客服台登录 |
| `clientChannel` | string | 客户端渠道标识 |

**请求示例**

```json
{
  "account": "zhangsan",
  "password": "Passw0rd!",
  "csConsole": false,
  "clientChannel": "web"
}
```

---

### `POST` `/api/auth/register-by-sms`

| 项 | 内容 |
|:---|:---|
| 鉴权 | 公开 |
| 说明 | 短信验证码注册 |
| Body | `RegisterBySmsRequest` |

**请求字段**

| 字段 | 类型 | 说明 |
|:---|:---|:---|
| `phone` | string | 手机号 |
| `code` | string | 短信验证码 |
| `username` | string | 用户名 |
| `password` | string | 密码 |
| `studentId` | string | 学号 |
| `gender` | string | 性别 |
| `realName` | string | 真实姓名 |
| `clientChannel` | string | 客户端渠道 |

---

### `POST` `/api/auth/login-by-sms`

| 项 | 内容 |
|:---|:---|
| 鉴权 | 公开 |
| 说明 | 短信登录（可能返回多账号待选择） |
| Body | `LoginBySmsRequest` |

| 字段 | 类型 |
|:---|:---|
| `phone` | string |
| `code` | string |
| `clientChannel` | string |

---

### `POST` `/api/auth/login-by-sms/confirm`

| 项 | 内容 |
|:---|:---|
| 鉴权 | 公开 |
| 说明 | 多账号场景下确认选中用户 |
| Body | `LoginBySmsConfirmRequest` |

| 字段 | 类型 |
|:---|:---|
| `pickToken` | string |
| `userId` | long |
| `clientChannel` | string |

---

## 2. 短信验证码

| 方法 | 路径 | 鉴权 | 说明 | Body |
|:---|:---|:---|:---|:---|
| `POST` | `/api/auth/sms/send` | 公开 | 注册验证码 | `{ "phone" }` |
| `POST` | `/api/auth/sms/send-login` | 公开 | 登录验证码 | `{ "phone" }` |

**示例**

```json
{ "phone": "13800138000" }
```

---

## 3. 找回密码

| 方法 | 路径 | 鉴权 | Body |
|:---|:---|:---|:---|
| `POST` | `/api/auth/forgot-password/sms/send` | 公开 | `{ "phone" }` |
| `POST` | `/api/auth/forgot-password/verify-sms` | 公开 | `phone`, `code` |
| `POST` | `/api/auth/forgot-password/reset` | 公开 | `phone`, `code`, `newPassword`；可选 `account` / `pickToken` / `userId` |

**重置示例**

```json
{
  "phone": "13800138000",
  "code": "123456",
  "newPassword": "NewPassw0rd!"
}
```

---

## 4. 微信登录

鉴权：全部 **公开**（前缀 `/api/auth/wechat/`）

### Web 扫码

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| `GET` | `/api/auth/wechat/status` | 微信能力是否可用 |
| `POST` | `/api/auth/wechat/web/qr-session` | 创建扫码会话（body 可选） |
| `GET` | `/api/auth/wechat/web/qr-session/{sessionId}` | 轮询扫码状态 |
| `GET` | `/api/auth/wechat/web/oauth-callback` | OAuth 回调（`code`, `state`） |
| `POST` | `/api/auth/wechat/web/confirm-login` | Web 确认登录 |
| `POST` | `/api/auth/wechat/web/confirm-sms` | Web 短信确认 |
| `POST` | `/api/auth/wechat/web/confirm-mp` | Web 小程序确认 |
| `POST` | `/api/auth/wechat/web/confirm-pick` | Web 多账号选择 |

### 小程序

| 方法 | 路径 | Body | 说明 |
|:---|:---|:---|:---|
| `POST` | `/api/auth/wechat/mp/login` | `loginCode`, `phoneCode` | 小程序登录 |
| `POST` | `/api/auth/wechat/mp/confirm-pick` | 选号确认 | 多账号选择 |

---

## 5. 当前用户 / 资料

> 以下接口需登录；网关走 **auth 自校验**（不二次 validate-session）。

### `GET` `/api/auth/me`

| 项 | 内容 |
|:---|:---|
| 鉴权 | 需登录 |
| 说明 | 当前登录用户信息 |
| Header | `Authorization: Bearer <token>` |

---

### `POST` `/api/auth/offline`

| 项 | 内容 |
|:---|:---|
| 鉴权 | 需登录 |
| 说明 | 标记离线 |

---

### `PUT` `/api/auth/profile`

| 项 | 内容 |
|:---|:---|
| 鉴权 | 需登录 |
| Body | `UpdateProfileRequest` |

| 字段 | 类型 |
|:---|:---|
| `username` | string |
| `avatarUrl` | string |
| `realName` | string |
| `studentId` | string |
| `gender` | string |

---

### `PUT` `/api/auth/password`

| 项 | 内容 |
|:---|:---|
| 鉴权 | 需登录 |
| Body | `ChangePasswordRequest` |

| 字段 | 类型 |
|:---|:---|
| `oldPassword` | string |
| `newPassword` | string |

---

### `POST` `/api/auth/rider/apply`

| 项 | 内容 |
|:---|:---|
| 鉴权 | 需登录 |
| 说明 | 提交骑手申请 |
| Body | `RiderApplyRequest` |

| 字段 | 类型 |
|:---|:---|
| `realName` | string |
| `studentId` | string |
| `phone` | string |
| `reason` | string |

---

## 6. 公开资料与头像

### `GET` `/api/auth/users/{username}/public-profile`

| 项 | 内容 |
|:---|:---|
| 鉴权 | 需登录（auth 自校验） |
| 说明 | 用户公开资料 |
| 响应 | `UserPublicProfileDTO` |

---

### `GET` `/api/auth/users/{username}/claimer-ratings`

| 项 | 内容 |
|:---|:---|
| 鉴权 | 需登录（auth 自校验） |
| 说明 | 接单评价列表 |
| 响应 | `List<Map>` |

---

### `GET` `/api/auth/avatar-scan-temp/{token}`

| 项 | 内容 |
|:---|:---|
| 鉴权 | 公开 |
| 说明 | 头像临时扫描资源 |
| 响应 | `image/*` 二进制流 |

---

[← 返回目录](./README.md) · [下一篇：任务大厅 →](./02-tasks.md)
