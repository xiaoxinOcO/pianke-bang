# 07 · 公告 / 系统消息 / 反馈

- **服务**：`campus-audit-service`（8104）
- **前缀**：`/api/notices`、`/api/system-messages`、`/api/feedback`

---

## 公告（用户端）

### GET `/api/notices`

已发布公告列表。

**鉴权**：公开

**响应**：`Notice[]`

管理端公告 CRUD 见 [08-admin.md](./08-admin.md)。

---

## 系统消息

### GET `/api/system-messages`

当前用户系统消息列表。

**鉴权**：需登录

**响应**：`SystemMessageListResponse`

---

### POST `/api/system-messages/read`

全部标记已读。

**响应**：空（void）

---

### POST `/api/system-messages/admin/send`

管理端发送系统消息（亦见 admin 下创建接口）。

**Body**（`SystemMessageCreateRequest`）

| 字段 | 类型 | 说明 |
|------|------|------|
| username | string | 目标用户 |
| title | string | 标题 |
| content | string | 内容 |
| category | string | 分类 |
| eventType | string | 事件类型 |
| refId | long | 关联业务 id |

**鉴权**：需登录 + 管理权限

---

## 用户反馈

### POST `/api/feedback`

提交反馈。

**鉴权**：需登录

**Body**（`UserFeedbackSubmitRequest`）

| 字段 | 类型 |
|------|------|
| type | string |
| page | string |
| content | string |
| images | string[] |

**响应**：`Map`

管理端查看/已读见 [08-admin.md](./08-admin.md)：`/api/admin/feedback`。
