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
foreach ($y in @(730, 770, 810)) {
    Write-Host ("=== try y=" + $y)
    &$adb logcat -c 2>&1 | Out-Null
    &$adb shell input swipe 500 $y 500 $y 900 2>&1 | Out-Null
    Wait 900
    $raw = Dump
    if ($raw.Contains("重命名")) {
        Write-Host "  menu up"
        $p = FindXY $raw "text" "重命名"
        if ($p) { &$adb shell input tap $p[0] $p[1] 2>&1 | Out-Null }
        Wait 900
        $raw = Dump
        Write-Host ("  dialog: " + ($raw.Contains("替换") -or $raw.Contains("新名称") -or $raw.Contains("重命名「")))
        $log = (&$adb logcat -d 2>&1) -join [char]10
        foreach ($l in ($log -split [char]10)) { if ($l -match "SUBL-REN") { Write-Host ("  " + $l) } }
        if ($raw.Contains("替换")) { break }
    } else { Write-Host "  no menu" }
}