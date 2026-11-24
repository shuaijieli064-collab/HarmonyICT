# NEXUS 演示数据预热脚本（PowerShell）
# 用途：本地/云上演示前一键重建全套演示数据（H2 重启即清空；RDS 持久则幂等跳过已存在账号）
# 前置：后端 8080 已启动；测试资料 nexus-ai/testdata/demo/budget-subscription.txt 存在
# 用法：pwsh scripts/seed-demo.ps1 [-BaseUrl http://localhost:8080] [-Username e2e-demo] [-Password "E2eDemo#2026"]

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "e2e-demo",
    [string]$Password = "E2eDemo#2026",
    [string]$DemoFile = "$PSScriptRoot\..\nexus-ai\testdata\demo\budget-subscription.txt"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Write-Step($msg) { Write-Host "[seed] $msg" -ForegroundColor Cyan }

# 1. 健康检查
$health = Invoke-RestMethod "$BaseUrl/actuator/health" -TimeoutSec 5
if ($health.status -ne "UP") { throw "后端未就绪: $($health.status)" }
Write-Step "后端就绪"

# 2. 注册（已存在则忽略）+ 登录
try {
    Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/register" -Method Post -ContentType "application/json" `
        -Body (@{ username = $Username; password = $Password; nickname = "演示账号" } | ConvertTo-Json) | Out-Null
    Write-Step "注册 $Username"
} catch {
    Write-Step "账号已存在，跳过注册"
}
$login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -ContentType "application/json" `
    -Body (@{ username = $Username; password = $Password } | ConvertTo-Json)
if ($login.code -ne 0) { throw "登录失败: $($login.message)" }
$headers = @{ Authorization = "Bearer $($login.data.token)" }
Write-Step "登录成功"

# 3. 上传演示资料并等待抽取完成
$upload = Invoke-RestMethod -Uri "$BaseUrl/api/v1/sources/upload" -Method Post -Headers $headers `
    -Form @{ file = Get-Item $DemoFile }
Write-Step "上传资料 sourceId=$($upload.data.sourceId)"
$deadline = (Get-Date).AddSeconds(60)
do {
    Start-Sleep 3
    $src = Invoke-RestMethod -Uri "$BaseUrl/api/v1/sources?page=1&pageSize=3" -Headers $headers
    $target = $src.data.records | Where-Object { $_.sourceId -eq $upload.data.sourceId }
} while ($target.status -eq "pending" -and (Get-Date) -lt $deadline)
if ($target.status -ne "parsed") { Write-Warning "抽取状态: $($target.status)（mock 模式仅支持 TXT）" }
$facts = Invoke-RestMethod -Uri "$BaseUrl/api/v1/facts?page=1&pageSize=1" -Headers $headers
Write-Step "事实总数: $($facts.data.total)"

# 4. Agent：建续费待办（L1 授权即执行）
function Invoke-AgentFlow([string]$intent) {
    $run = Invoke-RestMethod -Uri "$BaseUrl/api/v1/agent/runs" -Method Post -Headers $headers `
        -ContentType "application/json" -Body (@{ intent = $intent; triggerType = "manual" } | ConvertTo-Json)
    Start-Sleep 4
    $detail = Invoke-RestMethod -Uri "$BaseUrl/api/v1/agent/runs/$($run.data.id)" -Headers $headers
    $pending = $detail.data.logs | Where-Object { $_.result -eq "pending" } | Select-Object -First 1
    if ($null -eq $pending) { Write-Step "  （无待授权动作）"; return }
    Invoke-RestMethod -Uri "$BaseUrl/api/v1/agent/runs/$($run.data.id)/authorize" -Method Post -Headers $headers `
        -ContentType "application/json" -Body (@{ logIds = @([long]$pending.id); approve = $true } | ConvertTo-Json -Depth 4) | Out-Null
    Write-Step "  已授权执行: $intent"
}
Write-Step "Agent 链路 1/2：建续费待办"
Invoke-AgentFlow "云音乐订阅快续费了，帮我建个待办提醒确认续费"
Write-Step "Agent 链路 2/2：建答辩日程"
Invoke-AgentFlow "2026年11月30日安排毕业答辩日程"

# 5. 手动触发风险扫描（免等 60s 定时周期）
$scan = Invoke-RestMethod -Uri "$BaseUrl/api/v1/risks/scan" -Method Post -Headers $headers
Write-Step "风险扫描完成，本轮新增 $($scan.data.created) 条"

# 5.5 合成 14 天历史风险快照（仅 local profile 端点；云端 RDS 用持久快照无需注入）
try {
    $seedSnap = Invoke-RestMethod -Uri "$BaseUrl/api/v1/risks/snapshots/demo-history?days=14" `
        -Method Post -Headers $headers -TimeoutSec 15
    Write-Step "历史快照: 注入 $($seedSnap.data.inserted) 天（跳过已存在 $($seedSnap.data.skippedExisting) 天），趋势图 14 天可看"
} catch {
    Write-Step "历史快照注入跳过（非 local profile 或端点不存在，云端使用真实持久快照）"
}

# 5.6 拉一次趋势确认（演示前自检）
try {
    $trend = Invoke-RestMethod -Uri "$BaseUrl/api/v1/risks/trend?days=14" -Headers $headers
    $nonZero = ($trend.data | Where-Object { $_.openCount -gt 0 }).Count
    Write-Step "趋势自检: 14 天中 $($nonZero) 天有 open 风险"
} catch {
    Write-Step "趋势自检失败: $($_.Exception.Message)"
}

# 6. 汇总
$summary = Invoke-RestMethod -Uri "$BaseUrl/api/v1/dashboard/summary" -Headers $headers
$s = $summary.data
Write-Host ""
Write-Host "===== 演示数据就绪 =====" -ForegroundColor Green
Write-Host ("实体 {0} · 事实 {1} · 事件 {2} · open 风险 {3} · 任务 {4} · Agent 执行 {5} · 未读通知 {6}" -f `
    $s.entityTotal, $s.factTotal, $s.eventTotal, $s.openRiskTotal, $s.taskTotal, $s.agentRunTotal, $s.unreadNotificationTotal)
Write-Host "登录账号: $Username / $Password"
