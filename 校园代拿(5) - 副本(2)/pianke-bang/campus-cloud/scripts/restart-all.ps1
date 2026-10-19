# 重启全部 campus 后端（gateway + 各微服务）。会先结束占用 8080/8101-8106 端口的 Java 进程。
$ErrorActionPreference = "SilentlyContinue"
$root = Split-Path -Parent $PSScriptRoot
$ports = 8080,8101,8102,8103,8104,8105,8106

foreach ($port in $ports) {
  $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($conn) {
    Stop-Process -Id $conn.OwningProcess -Force
    Write-Host "已停止端口 $port (PID $($conn.OwningProcess))"
  }
}
Start-Sleep -Seconds 2

Set-Location $root
Write-Host ">>> 编译..."
mvn -q compile -DskipTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$services = @(
  @{ Name = "gateway"; Path = "campus-gateway" },
  @{ Name = "auth"; Path = "campus-auth-service" },
  @{ Name = "user"; Path = "campus-user-service" },
  @{ Name = "task"; Path = "campus-task-service" },
  @{ Name = "audit"; Path = "campus-audit-service" },
  @{ Name = "chat"; Path = "campus-chat-service" },
  @{ Name = "wallet"; Path = "campus-wallet-service" }
)

foreach ($svc in $services) {
  $module = $svc.Path
  Start-Process powershell -WindowStyle Minimized -ArgumentList @(
    "-NoExit",
    "-Command",
    "Set-Location '$root'; mvn -q -pl $module -am spring-boot:run -DskipTests"
  )
  Write-Host "已启动 $($svc.Name) -> $module"
  Start-Sleep -Seconds 3
}

Write-Host ">>> 全部服务已在独立窗口启动，请等待约 30-60 秒后访问 http://localhost:8080"
