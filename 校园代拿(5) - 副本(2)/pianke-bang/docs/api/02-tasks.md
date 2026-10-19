# 02 · 任务大厅

- **服务**：`campus-task-service`（8103）
- **前缀**：`/api/tasks`
- **鉴权**：除 `hall-stats` 外，默认需 JWT

---

## 列表与统计

### GET `/api/tasks`

分页查询任务大厅。

| Query | 类型 | 默认 | 说明 |
|-------|------|------|------|
| status | string (TaskStatus) | — | 状态筛选 |
| taskType | string | — | 类型 |
| keyword | string | — | 关键词 |
| page | int | 1 | 页码 |
| size | int | 10 | 每页条数 |

**响应**：`TaskPageResponse`

---

### GET `/api/tasks/mine`

当前用户相关任务列表。

**响应**：`DeliveryTask[]`

---

### GET `/api/tasks/urgent-quota`

当前用户加急额度。

**响应**：`Map`

---

### GET `/api/tasks/hall-stats`

大厅侧栏完成率等公开概览。

| Query | 默认 | 说明 |
|-------|------|------|
| days | 30 | 统计天数 |

**鉴权**：公开

---

## 发单 / 履约

### POST `/api/tasks`

创建任务。HTTP `201`。

**Body**（`TaskCreateRequest`）

| 字段 | 类型 | 说明 |
|------|------|------|
| taskType | string | 任务类型 |
| title | string | 标题 |
| pickupLocation | string | 取件地点 |
| pickupLat / pickupLng | number | 取件坐标 |
| dropoffLocation | string | 送达地点 |
| dropoffLat / dropoffLng | number | 送达坐标 |
| reward | number | 赏金（元） |
| publisherName | string | 发布人称呼 |
| contact | string | 联系方式 |
| description | string | 描述 |
| deliveryStartAt / deliveryEndAt / latestDeliveryAt | datetime | 时段 |
| imageUrls | string[] | 图片 |
| paymentMethod | string | `ALIPAY` / `WECHAT` / `WALLET` |
| userCouponId | long | 优惠券（可选） |
| urgentEnabled | boolean | 是否加急 |
| urgentFee | number | 加急费（开启时） |

**响应**：`DeliveryTask`

---

### POST `/api/tasks/{id}/claim`

接单。

### POST `/api/tasks/{id}/complete`

完成任务。

**Body**（可选，`TaskCompleteRequest`）：`note`, `proofImages`

### POST `/api/tasks/{id}/cancel`

取消任务。

### POST `/api/tasks/{id}/confirm`

发布方确认完成。

### POST `/api/tasks/{id}/rate`

评价接单方。

**Body**（`TaskRateRequest`）：`rating`（Double）, `comment`

### POST `/api/tasks/{id}/approve`

审核通过（待审任务）。

### POST `/api/tasks/{id}/reject`

审核驳回。

### PUT `/api/tasks/{id}/resubmit`

驳回后重新提交。Body 同 `TaskCreateRequest`。

### DELETE `/api/tasks/{id}`

删除任务。HTTP `204`。

---

## 申诉 / 联系

### POST `/api/tasks/{id}/appeal/audit`

申诉审核（管理/运营侧）。

**Body**（`TaskAppealAuditRequest`）

| 字段 | 类型 | 说明 |
|------|------|------|
| approve | boolean | 是否通过 |
| serviceCompleted | boolean | 通过时：是否认定服务已完成（默认 true） |
| remark | string | 备注 |

---

### GET `/api/tasks/{id}/claimer-phone`

获取接单人手机号（权限受业务约束）。

**响应**：`{ ... }` 字符串 Map

---

## 管理端相关

任务统计与 AI 自动审核见 [08-admin.md](./08-admin.md)（`/api/admin/tasks`、`/api/admin/ai`）。
