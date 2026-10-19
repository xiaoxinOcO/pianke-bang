-- 为指定用户批量发布任务（直接进任务大厅，状态 OPEN）
-- 用法：mysql -u root -p campus_delivery < seed_tasks_for_users.sql
-- 或在 Navicat / DBeaver 中选中 campus_delivery 库后整段执行
--
-- 目标用户 id：9, 10, 11, 12, 13, 14, 15, 1000000
-- publisher_name 自动取 user_account.username，无需手填昵称

USE campus_delivery;

-- 1) 执行前确认用户存在
SELECT id, username, phone, real_name, created_at
FROM user_account
WHERE id IN (9, 10, 11, 12, 13, 14, 15, 1000000)
ORDER BY id;

-- 2) 为每个用户插入 1 条可接单任务（status = OPEN）
INSERT INTO delivery_task (
  title, task_type, pickup_location, dropoff_location, reward,
  publisher_name, contact, description, status,
  latest_delivery_at, overdue_rejected, bounty_prepaid,
  created_at, updated_at
)
SELECT
  '菜鸟驿站代取小件', 'EXPRESS', '菜鸟驿站（南区）', '竹园3号楼', 5.00,
  u.username, COALESCE(NULLIF(TRIM(u.phone), ''), '13800000001'),
  '3 个小件，取件码尾号 881，放楼下货架即可。', 'OPEN',
  DATE_ADD(NOW(6), INTERVAL 2 DAY), 0, 1, NOW(6), NOW(6)
FROM user_account u WHERE u.id = 9

UNION ALL SELECT
  '一食堂外卖代取', 'FOOD', '一食堂外卖取餐架', '信工楼C座302', 4.00,
  u.username, COALESCE(NULLIF(TRIM(u.phone), ''), '13800000002'),
  '已下单，取餐号 0921，请尽快送达。', 'OPEN',
  DATE_ADD(NOW(6), INTERVAL 1 DAY), 0, 1, NOW(6), NOW(6)
FROM user_account u WHERE u.id = 10

UNION ALL SELECT
  '打印店装订资料代取', 'PRINT', '图书馆文印中心', '杏园4号楼前台', 6.50,
  u.username, COALESCE(NULLIF(TRIM(u.phone), ''), '13800000003'),
  '已付款，取货口令「校园代拿」，勿拆钉。', 'OPEN',
  DATE_ADD(NOW(6), INTERVAL 3 DAY), 0, 1, NOW(6), NOW(6)
FROM user_account u WHERE u.id = 11

UNION ALL SELECT
  '中通快递北门代取', 'EXPRESS', '北门快递柜区 B12', '桂园1号楼侧门', 7.00,
  u.username, COALESCE(NULLIF(TRIM(u.phone), ''), '13800000004'),
  '大件鞋盒，请轻放；取件码 3391。', 'OPEN',
  DATE_ADD(NOW(6), INTERVAL 2 DAY), 0, 1, NOW(6), NOW(6)
FROM user_account u WHERE u.id = 12

UNION ALL SELECT
  '超市代购日用品', 'OTHER', '校园超市', '梅园6号楼', 8.00,
  u.username, COALESCE(NULLIF(TRIM(u.phone), ''), '13800000005'),
  '清单：牛奶1、面包1、纸巾1包。', 'OPEN',
  DATE_ADD(NOW(6), INTERVAL 1 DAY), 0, 1, NOW(6), NOW(6)
FROM user_account u WHERE u.id = 13

UNION ALL SELECT
  '顺丰同城文件加急', 'EXPRESS', '顺丰网点（东门）', '图书馆三楼自习区', 12.00,
  u.username, COALESCE(NULLIF(TRIM(u.phone), ''), '13800000006'),
  '密封文件袋，勿拆封，送到后电话联系。', 'OPEN',
  DATE_ADD(NOW(6), INTERVAL 1 DAY), 0, 1, NOW(6), NOW(6)
FROM user_account u WHERE u.id = 14

UNION ALL SELECT
  '美团优选今日达自提', 'FOOD', '生活区团购自提点', '松园2号楼', 3.50,
  u.username, COALESCE(NULLIF(TRIM(u.phone), ''), '13800000007'),
  '两袋水果，冷藏品请尽快送达。', 'OPEN',
  DATE_ADD(NOW(6), INTERVAL 1 DAY), 0, 1, NOW(6), NOW(6)
FROM user_account u WHERE u.id = 15

UNION ALL SELECT
  '代取京东小家电', 'EXPRESS', '京东派（生活区）', '竹园5号楼618', 15.00,
  u.username, COALESCE(NULLIF(TRIM(u.phone), ''), '13800000008'),
  '箱子约 5kg，请小心搬运。', 'OPEN',
  DATE_ADD(NOW(6), INTERVAL 2 DAY), 0, 1, NOW(6), NOW(6)
FROM user_account u WHERE u.id = 1000000;

-- 3) 执行后核对
SELECT t.id, t.title, t.status, t.reward, t.publisher_name, t.created_at
FROM delivery_task t
WHERE t.publisher_name IN (
  SELECT username FROM user_account WHERE id IN (9, 10, 11, 12, 13, 14, 15, 1000000)
)
ORDER BY t.id DESC
LIMIT 20;
