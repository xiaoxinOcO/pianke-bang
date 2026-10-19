# 08 · 管理后台

前缀：`/api/admin/**`。均需 **管理员 JWT**（部分客服相关接口需客服角色）。

因网关按更细路径拆到不同服务，下表按归属分组。

---

## A. 用户与客服账号（user-service）

### GET `/api/admin/users`

用户列表。响应：`AdminUserDTO[]`

### GET `/api/admin/users/summary`

用户汇总计数。响应：`Map<string, long>`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/users/{id}/enable` | 启用 |
| POST | `/api/admin/users/{id}/disable` | 禁用 |
| POST | `/api/admin/users/{id}/avatar/approve` | 头像通过 |
| POST | `/api/admin/users/{id}/avatar/reject` | 头像驳回 |
| POST | `/api/admin/users/{id}/rider/approve` | 骑手认证通过 |
| POST | `/api/admin/users/{id}/rider/reject` | 骑手驳回 |
| POST | `/api/admin/users/{id}/rider/revoke` | 撤销骑手认证 |

### POST `/api/admin/users/{id}/credit-score`

**Body**（`AdminCreditScoreRequest`）：`creditScore`（int）

### POST `/api/admin/users/{id}/account-role`

**Body**（`AdminAccountRoleRequest`）：`role`

### GET `/api/admin/customer-service`

客服员工列表。

### POST `/api/admin/customer-service`

创建客服账号。

**Body**（`CreateCustomerServiceRequest`）：`username`, `password`

---

## B. 钱包充值（wallet-service）

### POST `/api/admin/users/{id}/wallet/credit`

给用户钱包加款。

**Body**（`AdminWalletCreditRequest`）：`amount`, `remark`

---

## C. 优惠券管理（wallet-service）

前缀：`/api/admin/coupons`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/templates` | 模板列表 |
| POST | `/templates` | 创建模板 |
| PATCH | `/templates/{id}/status` | 改状态 |
| PATCH | `/templates/{id}/banner` | 改 Banner |
| DELETE | `/templates/{id}` | 删除模板 |
| POST | `/issue` | 发券：`templateId`,`username`,`count`,`reason` |
| POST | `/redeem-codes` | 批量/创建兑换码：`templateId`,`code`,`count`,`maxUses`,`expireAt`,`remark` |
| GET | `/redeem-codes` | 兑换码列表（可带 query） |

---

## D. 大厅活动管理（wallet-service）

前缀：`/api/admin/hall-activities`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 列表 |
| POST | `/` | 创建 |
| PUT | `/{id}` | 更新 |
| PATCH | `/{id}/status` | Body：`{ "status": "..." }` |
| DELETE | `/{id}` | 删除 |

---

## E. 任务统计与 AI 审核（task-service）

### GET `/api/admin/tasks/stats`

任务统计。

### POST `/api/admin/ai/tasks/auto-audit`

触发任务自动审核批次。

### GET `/api/admin/ai/auto-audit/enabled`

查询自动审核开关。

### POST `/api/admin/ai/auto-audit/enabled`

设置自动审核开关（body 以实现为准）。

---

## F. AI 配置与运营（audit-service）

### GET `/api/admin/ai/config`

读取 LLM 配置（不含敏感明文时以实际返回为准）。

### POST `/api/admin/ai/config`

**Body**（`AdminAiConfigRequest`）

| 字段 | 类型 |
|------|------|
| enabled | boolean |
| baseUrl | string |
| apiKey | string |
| model | string |
| supportModel | string |
| timeoutSeconds | int |

### POST `/api/admin/ai/test`

连通性测试。Body 可选：`{ "prompt": "..." }`

---

### GET `/api/admin/logs`

操作日志分页：`page`（默认 1）、`size`（默认 20）

---

### 公告管理

| 方法 | 路径 | Body / Query |
|------|------|----------------|
| GET | `/api/admin/notices` | — |
| POST | `/api/admin/notices` | `title`,`content`,`published`,`pinned` |
| PUT | `/api/admin/notices/{id}` | 同上 |
| POST | `/api/admin/notices/{id}/publish?value=` | boolean |
| POST | `/api/admin/notices/{id}/pin?value=` | boolean |
| DELETE | `/api/admin/notices/{id}` | — |

---

### 反馈管理

### GET `/api/admin/feedback`

| Query | 说明 |
|-------|------|
| page / size | 分页 |
| type | 类型 |
| read | 是否已读 |

### POST `/api/admin/feedback/{id}/read?value=true`

标记已读。

---

### GET `/api/admin/cs-ratings`

客服评价分页：`page`,`size`,`agent`,`scoreMin`,`scoreMax`

---

### POST `/api/admin/system-messages`

创建系统消息。Body 同 `SystemMessageCreateRequest`（见 [07-comms.md](./07-comms.md)）。

---

## 提现审核补充

提现待审/审核接口挂在钱包前缀下，见 [03-wallet-payments.md](./03-wallet-payments.md)：

- `GET /api/wallet/admin/withdraw/pending`
- `POST /api/wallet/admin/withdraw/{id}/audit`
