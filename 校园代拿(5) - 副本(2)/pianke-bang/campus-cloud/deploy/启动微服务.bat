@echo off
chcp 65001 >nul
setlocal
set ROOT=%~dp0..
set NACOS_ADDR=127.0.0.1:8848
set NACOS_USERNAME=nacos
set NACOS_PASSWORD=nacos
set NACOS_REGISTER_IP=127.0.0.1

echo [1/2] 编译 campus-cloud ...
cd /d "%ROOT%"
call mvn -q -DskipTests install
if errorlevel 1 (
  echo 编译失败，请检查 Maven/JDK
  pause
  exit /b 1
)

echo [2/2] 请在本机已启动 Nacos 3.x 后，用 IDE 或新 CMD 窗口分别运行各模块主类：
echo   0. RabbitMQ 用 deploy 目录 docker compose up -d（管理台 http://127.0.0.1:15672 guest/guest）
echo   1. 需要 Sentinel 时再运行 deploy\启动Sentinel.bat（控制台 http://127.0.0.1:8858 账号 sentinel/sentinel）
echo   - AuthServiceApplication      端口 8101
echo   - UserServiceApplication      端口 8102
echo   - TaskServiceApplication      端口 8103
echo   - AuditServiceApplication     端口 8104
echo   - ChatServiceApplication      端口 8105
echo   - WalletServiceApplication    端口 8106
echo   - CampusGatewayApplication    端口 8080
echo.
echo Nacos 控制台: http://127.0.0.1:8081
echo 微服务注册: %NACOS_ADDR% （控制台 8081 仅网页，注册用 8848）
echo 也可在 campus-cloud 下执行 scripts\restart-all.ps1，用 spring-boot:run 拉起网关和六个服务
pause
