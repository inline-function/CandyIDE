$adb = "F:/AndroidSDK/platform-tools/adb.exe"
function Wait($ms) { Start-Sleep -Milliseconds $ms }
function Dump {
    $ok = $false
    for ($i = 0; $i -lt 4 -and -not $ok; $i++) {
        &$adb shell rm /sdcard/d.xml 2>&1 | Out-Null
        &$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
        Wait 500
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
Write-Host ("drawer open?: " + $raw.Contains("诊断"))
if ($raw.Contains("诊断")) {
    # 关右抽屉：点左遮罩
    &$adb shell input tap 100 1300 2>&1 | Out-Null
    Wait 900
    $raw = Dump
}
Write-Host ("editor up: " + $raw.Contains("hello.subl"))
&$adb logcat -c 2>&1 | Out-Null
&$adb shell input swipe 630 1100 630 1100 900 2>&1 | Out-Null
Wait 900
$raw = Dump
Write-Host ("menu up: " + ($raw.Contains("转到定义") -and $raw.Contains("重命名")))
$p = FindXY $raw "text" "重命名"
if ($p) {
    Write-Host ("rename row @ " + $p[0] + "," + $p[1])
    &$adb shell input tap $p[0] $p[1] 2>&1 | Out-Null
} else { Write-Host "MISS row" }
Wait 1000
$raw = Dump
Write-Host ("dialog up: " + ($raw.Contains("替换") -or $raw.Contains("新名称")))
$log = (&$adb logcat -d 2>&1) -join [char]10
foreach ($l in ($log -split [char]10)) { if ($l -match "SUBL-REN") { Write-Host $l } }