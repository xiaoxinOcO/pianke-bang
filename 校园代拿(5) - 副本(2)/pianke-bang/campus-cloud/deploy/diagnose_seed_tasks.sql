-- 诊断：为什么“成功了”但看不见任务
USE campus_delivery;

SELECT DATABASE() AS current_db;

SELECT COUNT(*) AS user_count FROM user_account;
SELECT COUNT(*) AS task_count FROM delivery_task;

-- 你脚本里写的那些 id，到底有没有
SELECT id, username, enabled
FROM user_account
WHERE id IN (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,1000000,1000001,1000002,1000003,1000004)
ORDER BY id;

-- 最近任务（不按标题过滤）
SELECT id, title, status, publisher_name, urgent_enabled, created_at
FROM delivery_task
ORDER BY id DESC
LIMIT 30;
