# Push Sentinel rules to Nacos (Nacos 3.x uses v3 admin API)
# Usage: powershell -ExecutionPolicy Bypass -File push-sentinel-rules-to-nacos.ps1

$ErrorActionPreference = "Stop"
$NacosAddr = "127.0.0.1:8848"
$Group = "SENTINEL_GROUP"
$ScriptDir = $PSScriptRoot
$RulesDir = Join-Path $ScriptDir "sentinel-nacos-rules"
$PublishUri = "http://$NacosAddr/nacos/v3/admin/cs/config"

$Apps = @(
    "campus-gateway",
    "campus-auth-service",
    "campus-user-service",
    "campus-task-service",
    "campus-audit-service",
    "campus-chat-service",
    "campus-wallet-service"
)

function Publish-NacosConfig {
    param(
        [string]$DataId,
        [string]$Content
    )
    $body = @{
        dataId    = $DataId
        groupName = $Group
        type      = "json"
        content   = $Content
    }
    $resp = Invoke-RestMethod -Method Post -Uri $PublishUri -Body $body
    if ($resp.code -ne 0) {
        throw "Publish failed dataId=$DataId response=$($resp | ConvertTo-Json -Compress)"
    }
    Write-Host "[OK] $DataId"
}

Write-Host "Pushing Sentinel rules to Nacos ($NacosAddr) group $Group ..."
Write-Host ""

$emptyFlow = Get-Content (Join-Path $RulesDir "empty-flow-rules.json") -Raw -Encoding UTF8

foreach ($app in $Apps) {
    Publish-NacosConfig -DataId "$app-flow-rules" -Content $emptyFlow
}

Publish-NacosConfig -DataId "campus-gateway-gw-flow-rules" -Content $emptyFlow

$gatewayDegrade = Get-Content (Join-Path $RulesDir "campus-gateway-degrade-rules.json") -Raw -Encoding UTF8
Publish-NacosConfig -DataId "campus-gateway-degrade-rules" -Content $gatewayDegrade

$taskDegrade = Get-Content (Join-Path $RulesDir "campus-task-service-degrade-rules.json") -Raw -Encoding UTF8
Publish-NacosConfig -DataId "campus-task-service-degrade-rules" -Content $taskDegrade

foreach ($app in $Apps) {
    if ($app -eq "campus-gateway" -or $app -eq "campus-task-service") {
        continue
    }
    Publish-NacosConfig -DataId "$app-degrade-rules" -Content "[]"
}

Write-Host ""
Write-Host "Done. Check Nacos console -> Config -> group SENTINEL_GROUP"
Write-Host "Restart microservices to load rules."
