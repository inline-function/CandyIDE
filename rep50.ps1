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
&$adb shell am force-stop candy.ide 2>&1 | Out-Null
&$adb logcat -c 2>&1 | Out-Null
&$adb shell am start -n candy.ide/.MainActivity 2>&1 | Out-Null
Wait 4000
$raw = Dump
$p = FindXY $raw "text" "demo"; if ($p) { &$adb shell input tap $p[0] $p[1] 2>&1 | Out-Null }
Wait 2300
$raw = Dump
Write-Host ("editor: " + $raw.Contains("hello.subl"))
$p = FindXY $raw "content-desc" "输出/日志/诊断"
if ($p) { &$adb shell input tap $p[0] $p[1] 2>&1 | Out-Null }
Wait 900
$raw = Dump
$p = FindXY $raw "text" "诊断"
if ($p) { &$adb shell input tap $p[0] $p[1] 2>&1 | Out-Null }
Wait 900
$raw = Dump
$rows = [regex]::Matches($raw, "text=" + $Q + "(hello.subl:[0-9]+:[0-9]+)" + $Q + "[^>]*bounds=" + $Q + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $Q)
Write-Host ("rows: " + $rows.Count)
if ($rows.Count -gt 0) {
    $cx = ([int]$rows[0].Groups[2].Value + [int]$rows[0].Groups[4].Value) / 2
    $cy = ([int]$rows[0].Groups[3].Value + [int]$rows[0].Groups[5].Value) / 2
    Write-Host ("tap " + $rows[0].Groups[1].Value + " @ " + $cx + "," + $cy)
    &$adb shell input tap $cx $cy 2>&1 | Out-Null
    Wait 1500
    Write-Host ("drawer closed: " + (-not (Dump).Contains("诊断")))
}
$log = (&$adb logcat -d 2>&1) -join [char]10
foreach ($l in ($log -split [char]10)) { if ($l -match "SUBL-DIAG") { Write-Host ("TRACE: " + $l) } }