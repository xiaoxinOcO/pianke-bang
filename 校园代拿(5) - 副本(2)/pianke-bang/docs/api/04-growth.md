# 04 · 优惠券与大厅活动

- **服务**：`campus-wallet-service`（8106）
- **前缀**：`/api/coupons`、`/api/hall-activities`

---

## 优惠券（用户端）

### GET `/api/coupons/activities`

可领/活动中的券模板列表。

**鉴权**：公开

**响应**：`List<Map>`

---

### GET `/api/coupons/mine`

我的优惠券。

| Query | 说明 |
|-------|------|
| status | 可选，按状态筛选 |

**鉴权**：需登录

---

### POST `/api/coupons/preview`

下单前预览优惠。

**Body**（`CouponPreviewRequest`）：`reward`, `userCouponId`

---

### POST `/api/coupons/claim-newcomer`

领取新人券。

---

### POST `/api/coupons/claim/{templateId}`

按模板领取。

---

### POST `/api/coupons/redeem`

兑换码领券。

**Body**（`CouponRedeemRequest`）：`code`

---

## 大厅活动（用户端）

### GET `/api/hall-activities`

已上架活动列表。

**鉴权**：公开

**响应**：`List<Map>`

---

## 管理端

券模板、发券、兑换码、活动 CRUD 见 [08-admin.md](./08-admin.md)：

- `/api/admin/coupons/**`
- `/api/admin/hall-activities/**`
