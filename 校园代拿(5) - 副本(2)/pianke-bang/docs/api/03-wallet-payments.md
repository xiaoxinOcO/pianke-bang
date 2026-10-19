# 03 · 钱包与支付

- **服务**：`campus-wallet-service`（8106）
- **前缀**：`/api/wallet`、`/api/wallet/alipay`
- **鉴权**：除支付宝 notify/return 外，默认需 JWT

---

## 钱包

### GET `/api/wallet/me`

当前用户钱包概览（余额、流水摘要等）。

**响应**：`Map`

---

### POST `/api/wallet/rebuild`

重建/校正当前用户钱包数据（运维或异常修复用途）。

---

### POST `/api/wallet/withdraw`

申请提现（平台账户提现流程）。

**Body**（`WalletWithdrawApplyRequest`）

| 字段 | 类型 | 说明 |
|------|------|------|
| amount | number | 金额 |
| accountName | string | 账户名 |
| accountNo | string | 账号 |
| remark | string | 备注 |

**响应**：`WalletWithdrawRequest`

---

### GET `/api/wallet/admin/withdraw/pending`

待审核提现列表（管理员）。

**响应**：`WalletWithdrawRequest[]`

---

### POST `/api/wallet/admin/withdraw/{id}/audit`

审核提现。

**Body**（`WalletAuditRequest`）：`approve`（boolean）, `remark`

---

## 支付宝

前缀：`/api/wallet/alipay`

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/key-setup` | 需登录 | 密钥/配置自检信息 |
| GET | `/pending` | 需登录 | 待处理支付单 |
| POST | `/recharge` | 需登录 | 创建充值：`{ "amount" }` |
| POST | `/withdraw` | 需登录 | 支付宝提现：`amount`,`alipayAccount`,`accountName` |
| POST | `/notify` | **公开** | 支付宝异步通知（form-urlencoded） |
| GET | `/return` | **公开** | 同步回跳（HTML） |
| POST | `/sync-latest` | 需登录 | 同步最近一笔 |
| POST | `/sync/{outTradeNo}` | 需登录 | 按商户订单号同步 |
| POST | `/task-pay` | 需登录 | 任务赏金支付宝支付（body Map） |
| POST | `/task-sync/{taskId}` | 需登录 | 同步指定任务支付 |
| POST | `/task-sync-latest` | 需登录 | 同步最近任务支付 |

### POST `/api/wallet/alipay/recharge`

**Body**（`AlipayRechargeRequest`）：`amount`

### POST `/api/wallet/alipay/withdraw`

**Body**（`AlipayWithdrawRequest`）

| 字段 | 类型 |
|------|------|
| amount | number |
| alipayAccount | string |
| accountName | string |

### POST `/api/wallet/alipay/task-pay`

**Body**（Map，常见含任务 id / 金额等，以联调为准）

**说明**：notify/return 由支付宝服务器或浏览器回调，客户端勿伪造。

---

## 管理端钱包充值

见 [08-admin.md](./08-admin.md)：`POST /api/admin/users/{id}/wallet/credit`。
