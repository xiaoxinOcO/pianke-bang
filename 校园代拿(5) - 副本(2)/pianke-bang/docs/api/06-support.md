# 06 · 客服与智能支持

- **前缀**：`/api/support`
- **路由分流**：
  - `assign-agent`、`agent-availability` → `campus-user-service`
  - 其余（含 `/chat`、评价、客服上下文）→ `campus-audit-service`
- **鉴权**：需 JWT（智能客服需登录用户）

> 说明：`CustomerSupportController` 与 `SupportAssignController` 均声明了 `GET /assign-agent`；经网关时以 user-service 路由为准。

---

## 智能客服

### POST `/api/support/chat`

用户端智能客服对话（RAG + 只读工具）。

**Body**（`CustomerSupportChatRequest`）

| 字段 | 类型 | 说明 |
|------|------|------|
| message | string | 本轮用户消息 |
| history | array | 历史消息列表（`CustomerSupportChatMessage`） |

**响应**：`Map`（含回复文案、可选引用等）

---

## 人工坐席分配

### GET `/api/support/assign-agent`

分配/获取可用客服坐席。

**目标服务**：user-service（网关优先）

**响应**：`Map`（坐席用户名等）

---

### GET `/api/support/agent-availability`

坐席在线/可用状态。

**目标服务**：user-service

**响应**：`Map`

---

## 评价

### POST `/api/support/ratings`

提交客服评价。

**Body**（`CsRatingSubmitRequest`）

| 字段 | 类型 |
|------|------|
| agentUsername | string |
| score | int |
| tags | string[] |
| comment | string |

---

### GET `/api/support/ratings/mine`

我提交的评价分页。

| Query | 默认 |
|-------|------|
| page | 1 |
| size | 20 |

**响应**：`CsRatingPageResponse`

---

## 客服台

### GET `/api/support/cs/user-context`

客服查看用户上下文（订单/钱包摘要等）。

| Query | 必填 |
|-------|------|
| username | 是 |

**响应**：`Map`

**权限**：客服/管理角色（业务层校验）
