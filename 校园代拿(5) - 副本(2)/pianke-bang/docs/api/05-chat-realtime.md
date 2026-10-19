# 05 · 聊天与实时推送

- **服务**：`campus-chat-service`（8105）
- **HTTP 前缀**：`/api/chat`
- **WebSocket**：`/ws/**`（经网关 `ws://localhost:8080/ws`）
- **鉴权**：HTTP 接口需 JWT；WS 连接鉴权方式以实现为准（通常带 token 查询参数或首包）

---

## HTTP 接口

### POST `/api/chat/send`

发送消息。

**Body**（`ChatSendRequest`）

| 字段 | 类型 | 说明 |
|------|------|------|
| toUsername | string | 接收方用户名 |
| content | string | 文本内容 |

**响应**：`ChatMessage`

---

### GET `/api/chat/history`

与指定用户的历史消息。

| Query | 必填 | 说明 |
|-------|------|------|
| targetUser | 是 | 对方用户名 |

**响应**：`ChatMessage[]`

---

### GET `/api/chat/contacts`

联系人列表。

**响应**：`ChatContactDTO[]`

---

### GET `/api/chat/temporary-contacts`

临时联系人用户名列表。

**响应**：`string[]`

---

### POST `/api/chat/read`

标记已读。

**Body**（`ChatReadMarkRequest`）

| 字段 | 类型 |
|------|------|
| targetUser | string |
| lastReadMessageId | long |

---

### GET `/api/chat/peer-read`

查询对方已读进度。

| Query | 说明 |
|-------|------|
| targetUser | 对方用户名 |

**响应**：`Map`

---

## WebSocket

| 项 | 值 |
|----|-----|
| 网关路径 | `/ws/**` |
| 开发地址 | `ws://localhost:8080/ws` |
| 用途 | 新消息、已读、系统实时事件推送 |

内部推送入口见 [09-internal-feign.md](./09-internal-feign.md)：`POST /internal/realtime/push`。
