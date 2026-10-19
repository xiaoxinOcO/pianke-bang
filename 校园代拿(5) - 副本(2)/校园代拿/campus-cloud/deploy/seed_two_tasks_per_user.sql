-- 按「用户名」插入（不依赖 id），每人 2 条：普通 + 加急，截止明天，内容不同
-- 在 campus_delivery 整段执行

USE campus_delivery;

-- 1) 先看库里真实用户名（对照截图第 4 列）
SELECT id, username, enabled FROM user_account ORDER BY id;

-- 2) 普通任务（标题/地点/描述各不相同）
INSERT INTO delivery_task (
  title, task_type, pickup_location, dropoff_location, reward,
  publisher_name, contact, description, status,
  latest_delivery_at, overdue_rejected, bounty_prepaid,
  urgent_enabled, urgent_fee, urgent_rider_bonus, urgent_push_fee, urgent_at,
  created_at, updated_at
)
SELECT
  CASE u.id % 10
    WHEN 0 THEN '南区菜鸟代取三件小件'
    WHEN 1 THEN '一食堂外卖代取'
    WHEN 2 THEN '文印中心装订资料代取'
    WHEN 3 THEN '北门中通代取鞋盒'
    WHEN 4 THEN '校园超市代购日用品'
    WHEN 5 THEN '图书馆占座到中午'
    WHEN 6 THEN '东门顺丰文件袋代取'
    WHEN 7 THEN '生活区团购水果自提'
    WHEN 8 THEN '京东派小家电代取'
    ELSE '二食堂麻辣烫代取'
  END,
  CASE u.id % 5
    WHEN 0 THEN 'EXPRESS'
    WHEN 1 THEN 'FOOD'
    WHEN 2 THEN 'PRINT'
    WHEN 3 THEN 'OTHER'
    ELSE 'QUEUE'
  END,
  CASE u.id % 10
    WHEN 0 THEN '南区菜鸟驿站'
    WHEN 1 THEN '一食堂外卖架'
    WHEN 2 THEN '图书馆文印中心'
    WHEN 3 THEN '北门快递柜B12'
    WHEN 4 THEN '校园超市'
    WHEN 5 THEN '图书馆二楼'
    WHEN 6 THEN '东门顺丰网点'
    WHEN 7 THEN '生活区团购点'
    WHEN 8 THEN '京东派（生活区）'
    ELSE '二食堂窗口3'
  END,
  CASE u.id % 10
    WHEN 0 THEN '竹园3号楼'
    WHEN 1 THEN '信工楼C302'
    WHEN 2 THEN '杏园前台'
    WHEN 3 THEN '桂园1号楼侧门'
    WHEN 4 THEN '梅园6号楼'
    WHEN 5 THEN '图书馆靠窗座位'
    WHEN 6 THEN '行政楼一楼'
    WHEN 7 THEN '松园2号楼'
    WHEN 8 THEN '竹园5-618'
    ELSE '桂园地下车库口'
  END,
  ROUND(3 + (u.id % 10) * 0.8, 2),
  u.username,
  COALESCE(NULLIF(TRIM(u.phone), ''), '13800000000'),
  CONCAT('普通测试单，发布者 ', u.username, '（id=', u.id, '），截止明天。'),
  'OPEN',
  DATE_ADD(NOW(6), INTERVAL 1 DAY),
  0, 1,
  0, NULL, NULL, NULL, NULL,
  NOW(6), NOW(6)
FROM user_account u
WHERE u.enabled = 1;

SELECT ROW_COUNT() AS normal_inserted;

-- 3) 加急任务
INSERT INTO delivery_task (
  title, task_type, pickup_location, dropoff_location, reward,
  publisher_name, contact, description, status,
  latest_delivery_at, overdue_rejected, bounty_prepaid,
  urgent_enabled, urgent_fee, urgent_rider_bonus, urgent_push_fee, urgent_at,
  created_at, updated_at
)
SELECT
  CASE u.id % 10
    WHEN 0 THEN '【加急】答辩U盘送达'
    WHEN 1 THEN '【加急】面试西装干洗取回'
    WHEN 2 THEN '【加急】实验试剂冷链代送'
    WHEN 3 THEN '【加急】会议茶歇蛋糕'
    WHEN 4 THEN '【加急】钥匙忘在教室代取'
    WHEN 5 THEN '【加急】摄影器材箱代搬'
    WHEN 6 THEN '【加急】医院检查单代送'
    WHEN 7 THEN '【加急】雨天外卖保温送达'
    WHEN 8 THEN '【加急】电脑配件同城急送'
    ELSE '【加急】比赛号码布急送'
  END,
  CASE u.id % 4
    WHEN 0 THEN 'EXPRESS'
    WHEN 1 THEN 'FOOD'
    WHEN 2 THEN 'PRINT'
    ELSE 'OTHER'
  END,
  CASE u.id % 10
    WHEN 0 THEN '打印店自助机'
    WHEN 1 THEN '商业街干洗店'
    WHEN 2 THEN '化学楼试剂柜'
    WHEN 3 THEN '西点屋'
    WHEN 4 THEN '教学楼B305'
    WHEN 5 THEN '传媒学院器材室'
    WHEN 6 THEN '校医院检验科'
    WHEN 7 THEN '西门外卖柜'
    WHEN 8 THEN '电脑城售后点'
    ELSE '体育学院打印室'
  END,
  CASE u.id % 10
    WHEN 0 THEN '报告厅后门'
    WHEN 1 THEN '就业中心门口'
    WHEN 2 THEN '生物楼冷库'
    WHEN 3 THEN '行政楼三楼会议室'
    WHEN 4 THEN '南门门卫室'
    WHEN 5 THEN '操场看台'
    WHEN 6 THEN '家属区12栋'
    WHEN 7 THEN '研究生宿舍A座'
    WHEN 8 THEN '创新创业中心'
    ELSE '田径场检录处'
  END,
  ROUND(8 + (u.id % 10) * 1.5, 2),
  u.username,
  COALESCE(NULLIF(TRIM(u.phone), ''), '13900000000'),
  CONCAT('加急测试单，发布者 ', u.username, '（id=', u.id, '），截止明天。'),
  'OPEN',
  DATE_ADD(NOW(6), INTERVAL 1 DAY),
  0, 1,
  1, 2.00, 1.60, 0.40, NOW(6),
  NOW(6), NOW(6)
FROM user_account u
WHERE u.enabled = 1;

SELECT ROW_COUNT() AS urgent_inserted;

-- 4) 核对（这次应能查到）
SELECT id, title, status, publisher_name, urgent_enabled, latest_delivery_at, created_at
FROM delivery_task
WHERE created_at >= DATE_SUB(NOW(6), INTERVAL 15 MINUTE)
ORDER BY id DESC
LIMIT 80;

SELECT
  publisher_name,
  SUM(urgent_enabled = 0) AS normal_cnt,
  SUM(urgent_enabled = 1) AS urgent_cnt
FROM delivery_task
WHERE created_at >= DATE_SUB(NOW(6), INTERVAL 15 MINUTE)
GROUP BY publisher_name
ORDER BY publisher_name;
