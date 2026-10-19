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
if ($raw.Contains("替换")) {
    Write-Host "dialog already up (from last run)"
} else {
    # 重新长按出菜单
    &$adb shell input swipe 500 730 500 730 900 2>&1 | Out-Null
    Wait 900
    $raw = Dump
    $p = FindXY $raw "text" "重命名"
    if ($p) { &$adb shell input tap $p[0] $p[1] 2>&1 | Out-Null }
    Wait 900
    $raw = Dump
}
if ($raw.Contains("替换")) {
    Write-Host "dialog up: renaming x -> xx"
    # TextField 预填 x：一次性删掉再输入
    for ($k = 0; $k -lt 3; $k++) { &$adb shell input keyevent 67 2>&1 | Out-Null; Wait 150 }
    Wait 300
    &$adb shell input text "xx" 2>&1 | Out-Null
    Wait 600
    $raw = Dump
    $p = FindXY $raw "text" "替换"
    if ($p) { Write-Host ("tap 替换 @ " + $p[0] + "," + $p[1]); &$adb shell input tap $p[0] $p[1] 2>&1 | Out-Null } else { Write-Host "MISS 替换" }
    Wait 1500
    # 保存
    $raw = Dump
    $p = FindXY $raw "content-desc" "保存"
    if ($p) { &$adb shell input tap $p[0] $p[1] 2>&1 | Out-Null; Write-Host "saved" } else { Write-Host "MISS save" }
    Wait 1200
    $f = (&$adb shell "run-as candy.ide cat files/candy/projects/demo/hello.subl" 2>&1) -join [char]10
    Write-Host "AFTER:"
    foreach ($l in ($f -split [char]10)) { Write-Host ("|" + $l + "|") }
} else { Write-Host "no dialog" }