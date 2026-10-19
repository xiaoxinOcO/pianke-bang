# 修改 campus-common / 网关鉴权相关代码后，先编译再重启后端。
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host ">>> 编译 campus-cloud（含 campus-common）..."
mvn -q compile -DskipTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ">>> 编译完成。"
Write-Host ">>> 请自行重启后端（修改 campus-common 或网关后，需重启 gateway + 相关微服务）。"
Write-Host "    编译命令：campus-cloud 目录下  mvn compile -DskipTests"
