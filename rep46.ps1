$adb = "F:/AndroidSDK/platform-tools/adb.exe"
function Wait($ms) { Start-Sleep -Milliseconds $ms }
function Dump {
    $ok = $false
    for ($i = 0; $i -lt 4 -and -not $ok; $i++) {
        &$adb shell rm /sdcard/d.xml 2>&1 | Out-Null
        &$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
        Wait 450
        $p = (&$adb pull /sdcard/d.xml "F:/KotlinProject/CandyIDE/d1.xml" 2>&1) -join ""
        if ($p.Contains('1 file pulled')) { $t = Get-Content 'F:/KotlinProject/CandyIDE/d1.xml' -Raw; if ($t.Length -gt 500) { $ok = $true } }
        if (-not $ok) { Wait 700 }
    }
    return Get-Content 'F:/KotlinProject/CandyIDE/d1.xml' -Raw
}
$Q = [char]34; $LB = "[[]"; $RB = "[]]"; $NUM = "[0-9]+"
function FindXY($raw, $attr, $t) {
    $re = "<node[^>]*" + $attr + "=" + $Q + $t + $Q + "[^>]*bounds=" + $Q + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $Q
    $m = [regex]::Match($raw, $re)
    if ($m.Success) { return @((([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2), (([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)) }
    return $null
}
$raw = Dump
Write-Host ("editor: " + $raw.Contains("hello.subl"))
# 开输出抽屉 → 诊断 tab
$p = FindXY $raw "content-desc" "输出/日志/诊断"
if ($p) { &$adb shell input tap $p[0] $p[1] 2>&1 | Out-Null }
Wait 800
$raw = Dump
Write-Host ("drawer: " + $raw.Contains("诊断"))
$p = FindXY $raw "text" "诊断"
if ($p) { &$adb shell input tap $p[0] $p[1] 2>&1 | Out-Null }
Wait 800
$raw = Dump
Write-Host ("diag rows: " + ($raw -split "hello.subl:").Length)
# 点击某一行（位置链接 hello.subl:3:3）
$re2 = "<node[^>]*text=" + $Q + "hello.subl:3:3" + $Q
Write-Host ("row 3:3 present: " + ([regex]::IsMatch($raw, $re2)))
$p = FindXY $raw "text" "hello.subl:3:3"
if ($p) { Write-Host ("tap row @ " + $p[0] + "," + $p[1]); &$adb shell input tap $p[0] $p[1] 2>&1 | Out-Null } else { Write-Host "MISS row" }
Wait 1000
$raw = Dump
Write-Host ("drawer closed after goto: " + (-not $raw.Contains("诊断")))
Write-Host ("editor back: " + $raw.Contains("hello.subl"))