@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

rem 指向你本地的 Sentinel 1.8 源码目录（可按实际路径修改）
set "SENTINEL_SRC=%~dp0..\..\..\..\微服务工具\Sentinel-1.8\Sentinel-1.8"
if not exist "%SENTINEL_SRC%\pom.xml" (
  echo 未找到 Sentinel 源码: %SENTINEL_SRC%
  echo 请确认 微服务工具\Sentinel-1.8\Sentinel-1.8 目录存在
  pause
  exit /b 1
)

set "DASHBOARD_PORT=8858"
set "JAR="

for %%F in ("%SENTINEL_SRC%\sentinel-dashboard\target\sentinel-dashboard-*.jar") do set "JAR=%%~f"

if not defined JAR (
  echo [1/2] 首次运行：编译 Sentinel Dashboard（约 2~5 分钟）...
  cd /d "%SENTINEL_SRC%"
  call mvn -pl sentinel-dashboard -am clean package -DskipTests
  if errorlevel 1 (
    echo 编译失败，请检查 JDK 8+ 与 Maven
    pause
    exit /b 1
  )
  for %%F in ("%SENTINEL_SRC%\sentinel-dashboard\target\sentinel-dashboard-*.jar") do set "JAR=%%~f"
)

if not defined JAR (
  echo 未找到 sentinel-dashboard jar，请手动编译后重试
  pause
  exit /b 1
)

echo [2/2] 启动 Sentinel Dashboard ...
echo 控制台: http://127.0.0.1:%DASHBOARD_PORT%
echo 默认账号: sentinel / sentinel
echo.
echo 请先启动 Dashboard，再启动 campus-cloud 各微服务，即可在控制台看到服务列表。
echo 关闭本窗口即停止 Dashboard。
echo.

rem Nacos 地址已写入 sentinel-dashboard/src/main/resources/application.properties，无需再传 -Dnacos 参数
java -Dserver.port=%DASHBOARD_PORT% -Dcsp.sentinel.dashboard.server=127.0.0.1:%DASHBOARD_PORT% -jar "%JAR%"

pause
