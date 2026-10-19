@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo === 释放微服务占用端口 8080、8101-8106 ===
for %%p in (8080 8101 8102 8103 8104 8105 8106) do (
  for /f "tokens=5" %%a in ('netstat -ano ^| findstr :%%p ^| findstr LISTENING') do (
    echo 结束占用 %%p 的进程 PID=%%a
    taskkill /F /PID %%a 2>nul
  )
)

echo.
echo === 修复 Flyway 失败记录（需 MySQL 已启动，账号 root/123456）===
set MYSQL_CMD=
where mysql >nul 2>&1 && set MYSQL_CMD=mysql
if not defined MYSQL_CMD (
  if exist "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" (
    set "MYSQL_CMD=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
  )
)

if defined MYSQL_CMD (
  "%MYSQL_CMD%" -uroot -p123456 < "%~dp0fix-flyway-v8.sql"
  if errorlevel 1 (
    echo [警告] 自动执行 SQL 失败，请手动在 MySQL 中执行 fix-flyway-v8.sql
  ) else (
    echo [完成] 已清理 flyway_schema_history 中 success=0 的记录
  )
) else (
  echo 未找到 mysql 命令，请手动执行：
  echo   %~dp0fix-flyway-v8.sql
)

echo.
echo 完成后请在 IDEA 中：
echo   1. Maven - campus-gateway - package（或 Reload Project）
echo   2. 先启动 Nacos 8848，再启动各业务服务，最后启动 campus-gateway
echo.
pause
