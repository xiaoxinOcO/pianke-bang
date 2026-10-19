# 09 · 内部接口（Feign）

> **不对前端 / 小程序开放。** 前缀 `/internal/**`，经网关访问返回 403。  
> 调用方：微服务之间；通常需内部 token（`CAMPUS_INTERNAL_TOKEN` / `InternalTokenSupport`）。

---

## Auth — `/internal/auth`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/validate-session` | 校验会话。Body：含 token/session 的 Map |

**Feign**：`AuthSessionClient`

---

## Wallet — `/internal/wallet`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/lock-bounty` | 锁定任务赏金 |
| POST | `/refund-bounty` | 退回赏金 |
| POST | `/income-from-task` | 任务完成入账 |
| POST | `/coupon-subsidy` | 记录优惠券补贴 |
| POST | `/compensate-timeout` | 超时补偿 |
| POST | `/credit-demo` | Demo 加款 |
| GET | `/agent-snapshot?username=` | Agent 钱包快照 |
| GET | `/agent-withdrawals` | Agent 提现记录 |
| GET | `/agent-coupons` | Agent 优惠券 |
| GET | `/agent-transactions` | Agent 流水 |

**Feign**：`WalletInternalClient`

---

## Tasks — `/internal/tasks`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/agent-summary` | Agent 任务摘要 |
| GET | `/agent-detail` | Agent 任务详情 |
| GET | `/agent-timeline` | Agent 时间线 |
| GET | `/agent-appeals` | Agent 申诉 |
| GET | `/agent-urgent-quota` | Agent 加急额度 |
| GET | `/{id}/alipay-bounty` | 支付宝赏金信息 |
| POST | `/{id}/prepare-alipay-bounty` | 准备支付宝赏金支付 |
| POST | `/{id}/confirm-alipay-bounty` | 确认支付宝赏金到账 |

**Feign**：`TaskInternalClient`

---

## Audit / AI — `/internal/audit`、`/internal/ai`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/internal/audit/log` | 写操作日志。Body：Map |
| GET | `/internal/ai/enabled` | LLM 是否启用 |
| GET | `/internal/ai/auto-audit/enabled` | 自动审核开关 |
| POST | `/internal/ai/auto-audit/enabled` | 设置开关。Body：`Map<String,Boolean>` |
| POST | `/internal/ai/audit-task` | 单任务 AI 审核 |

**Feign**：`AuditLogClient`、`AdminAiClient`

---

## System messages — `/internal/system-messages`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/create` | 创建系统消息 |

**Feign**：`SystemMessageClient`

---

## Support tools — `/internal/support`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/run-tools` | 智能客服只读工具执行（钱包/任务查询等） |

供 audit 服务 Agent 工具链调用。

---

## Realtime — `/internal/realtime`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/push` | 向在线用户推送 WS 事件。Body：Map |

**Feign**：`RealtimePushClient`

---

## Feign 调用关系（摘要）

```
task-service  → wallet (/internal/wallet/*)
task-service  → audit  (/internal/ai/*, /internal/audit/log, /internal/system-messages)
task-service  → chat   (/internal/realtime/push)
gateway       → auth   (/internal/auth/validate-session)
audit Agent   → support (/internal/support/run-tools)
              → wallet/tasks agent-* 只读接口
```
