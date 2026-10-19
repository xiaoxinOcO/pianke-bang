@echo off
chcp 65001 >nul
set "NACOS_HOME=C:\Users\黄俊达\ai-infra\nacos\standalone\nacos-3.2.1"
set "CONF=%NACOS_HOME%\conf\application.properties"

if not exist "%CONF%" (
  echo 未找到 Nacos 配置: %CONF%
  echo 请修改本 bat 顶部 NACOS_HOME 为你的安装路径
  pause
  exit /b 1
)

echo === 停止 Nacos ===
call "%NACOS_HOME%\bin\shutdown.cmd"
timeout /t 5 /nobreak >nul

echo === 关闭鉴权（开发环境）===
powershell -NoProfile -Command ^
  "$p='%CONF%'; $c=Get-Content $p -Raw; " ^
  "$c=$c -replace 'nacos\.core\.auth\.enabled=true','nacos.core.auth.enabled=false'; " ^
  "$c=$c -replace 'nacos\.core\.auth\.admin\.enabled=true','nacos.core.auth.admin.enabled=false'; " ^
  "$c=$c -replace 'nacos\.core\.auth\.console\.enabled=true','nacos.core.auth.console.enabled=false'; " ^
  "Set-Content $p $c -Encoding UTF8"

echo === 启动 Nacos standalone ===
cd /d "%NACOS_HOME%\bin"
call startup.cmd -m standalone

echo.
echo 完成后请重启所有微服务，并在控制台 http://127.0.0.1:8081 ：
echo   - 命名空间选 public（默认）
echo   - 关闭「隐藏空服务」开关
echo.
pause
