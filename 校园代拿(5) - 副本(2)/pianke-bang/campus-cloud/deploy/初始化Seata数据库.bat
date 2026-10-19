@echo off
chcp 65001 >nul
setlocal

set "SQL=%~dp0seata-mysql-init.sql"
set "MYSQL_USER=root"
set "MYSQL_PWD=123456"

echo.
echo [1/2] 初始化本机 seata 库（127.0.0.1:3306）...
mysql -u%MYSQL_USER% -p%MYSQL_PWD% < "%SQL%"
if errorlevel 1 (
  echo.
  echo 失败：请确认 MySQL 已启动，且账号密码为 root / 123456。
  echo 若密码不同，请手动在 Navicat 或 IDEA 中执行：%SQL%
  pause
  exit /b 1
)

echo.
echo [2/2] 完成。请重启 seata-server（8091），再重试申诉裁决。
echo 配置路径：微服务工具\seata-server-2.0.0\seata\conf\application.yml
echo.
pause
