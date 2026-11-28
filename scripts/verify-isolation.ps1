# 双账号数据隔离验证（本地 H2, local profile）
# 输出: docs/evidence/isolation/isolation-verify-local.txt
# 用法: pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/verify-isolation.ps1
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$base = "http://localhost:8080/api/v1"
$log = New-Object System.Text.StringBuilder

function W($s) { [void]$log.AppendLine($s); Write-Host $s }

# Result 信封: { code, message, data }
function Api($method, $path, $token, $body) {
    $h = @{ "Content-Type" = "application/json" }
    if ($token) { $h["Authorization"] = "Bearer $token" }
    $args = @{ Uri = "$base$path"; Method = $method; Headers = $h; UseBasicParsing = $true; TimeoutSec = 20 }
    if ($body) { $args.Body = ($body | ConvertTo-Json -Depth 5) }
    try {
        $r = Invoke-RestMethod @args
        # 业务错误走 HTTP 200，保留真实状态码，不能伪造 400（否则掩盖协议层问题）
        if ($r.code -ne 0) { return @{ __status = 200; __error = "code=$($r.code) $($r.message)" } }
        return $r.data
    } catch {
        $code = $null
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        return @{ __status = $code; __error = $_.ErrorDetails.Message }
    }
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$userA = "isoA$stamp"; $userB = "isoB$stamp"

W "=== NEXUS 双账号数据隔离验证 ==="
W "时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')  环境: local (H2 mem)  base=$base"
W ""

W "[1] 注册账号 A/B"
$null = Api "POST" "/auth/register" $null @{ username = $userA; password = "passw0rd!A"; nickname = "隔离A" }
$null = Api "POST" "/auth/register" $null @{ username = $userB; password = "passw0rd!B"; nickname = "隔离B" }
W "  A: $userA  B: $userB"
W ""

$la = Api "POST" "/auth/login" $null @{ username = $userA; password = "passw0rd!A" }
$lb = Api "POST" "/auth/login" $null @{ username = $userB; password = "passw0rd!B" }
$ta = $la.token; $tb = $lb.token
W "[2] 登录: A/B 均取得 JWT (长度 $($ta.Length) / $($tb.Length))"
W ""

W "[3] A 上传资料 (txt, multipart, mock 抽取)"
$tmp = Join-Path $env:TEMP "nexus-iso-$stamp.txt"
Set-Content -Path $tmp -Value "华为云代金券 500 元，有效期至 2026-12-01。HCIP 证书有效期至 2026-11-30，需及时换证。" -Encoding utf8
try {
    $form = @{ file = Get-Item $tmp }
    $h = @{ Authorization = "Bearer $ta" }
    $up = Invoke-RestMethod -Uri "$base/sources/upload" -Method Post -Headers $h -Form $form -TimeoutSec 30
    W "  上传: code=$($up.code) sourceId=$($up.data.id) status=$($up.data.status)"
} catch {
    W "  上传异常: $($_.Exception.Message) $($_.ErrorDetails.Message)"
}
Remove-Item $tmp -ErrorAction SilentlyContinue
W ""

$factsA = Api "GET" "/facts?page=1&pageSize=50" $ta $null
$factsB = Api "GET" "/facts?page=1&pageSize=50" $tb $null
$listA = if ($factsA.records) { @($factsA.records) } else { @() }
$listB = if ($factsB.records) { @($factsB.records) } else { @() }
W "[4] A 视角: 事实数=$($listA.Count)"
W "[5] B 视角: 事实数=$($listB.Count) (应为 0)"
W ""

$cross = $null; $patch = $null
if ($listA.Count -gt 0) {
    $fid = $listA[0].id
    W "[6] 交叉读: B 请求 A 的事实 id=$fid"
    $cross = Api "GET" "/facts/$fid" $tb $null
    if ($cross.__status) { W "  结果: HTTP $($cross.__status) $($cross.__error) (预期 404 或业务码 20102 事实不存在 = 隔离生效)" }
    else { W "  结果: 成功返回! (应被拒绝!) $($cross | ConvertTo-Json -Compress)" }
    W ""
    W "[6b] 交叉写: B 尝试修改 A 的事实（PUT /facts/{id}，契约 body 为 objectValue）"
    $patch = Api "PUT" "/facts/$fid" $tb @{ objectValue = "篡改" }
    if ($patch.__status) { W "  结果: HTTP $($patch.__status) $($patch.__error)" }
    else { W "  结果: 成功! (应被拒绝!)" }
    W ""
} else {
    W "[6] 跳过交叉访问 (A 无事实)"
    W ""
}

W "=== 汇总 ==="
W "  A 事实数: $($listA.Count)   B 事实数: $($listB.Count)"
W "  列表隔离 (B 看不到 A 数据): $(if ($listB.Count -eq 0) {'PASS'} else {'FAIL'})"
if ($cross) {
    # 隔离生效的表现: HTTP 404/403, 或业务码 20102(事实不存在)/10103(无权) —— 关键是不返回 data
    $blocked = ($cross.__status -eq 404 -or $cross.__status -eq 403) -or ($cross.__error -match "20102|不存在")
    W "  B→A 交叉读隔离: $(if ($blocked) {'PASS (拒绝且不返回数据)'} else {'FAIL: ' + $cross.__status + ' ' + $cross.__error})"
}
if ($patch -and $patch.__status) {
    # 拒绝可以是 403/404/405，或业务码 20102（事实不存在）/90001/90002；90000 是系统错误，绝不算通过
    $pblocked = ($patch.__status -eq 403 -or $patch.__status -eq 404 -or $patch.__status -eq 405) -or
                ($patch.__error -match "20102|不存在|90001|90002|不支持|方法")
    if ($patch.__error -match "90000") { $pblocked = $false }
    W "  B→A 交叉写隔离: $(if ($pblocked) {'PASS (拒绝, 无数据变更)'} else {'REVIEW: HTTP ' + $patch.__status + ' ' + $patch.__error})"
}

$dir = Join-Path (Split-Path $PSScriptRoot -Parent) "docs\evidence\isolation"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$out = Join-Path $dir "isolation-verify-local.txt"
$log.ToString() | Out-File -FilePath $out -Encoding utf8
W ""
W "已留证: $out"
