$adb = "F:/AndroidSDK/platform-tools/adb.exe"
function Wait($ms) { Start-Sleep -Milliseconds $ms }
&$adb shell am force-stop candy.ide 2>&1 | Out-Null
&$adb logcat -c 2>&1 | Out-Null
&$adb shell am start -n candy.ide/.MainActivity 2>&1 | Out-Null
Wait 4000
$Q = [char]34; $LB = "[[]"; $RB = "[]]"; $NUM = "[0-9]+"
function FindXY($raw, $attr, $t) {
    $re = "<node[^>]*" + $attr + "=" + $Q + $t + $Q + "[^>]*bounds=" + $Q + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $Q
    $m = [regex]::Match($raw, $re)
    if ($m.Success) { return @((([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2), (([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)) }
    return $null
}
function Dump {
    &$adb shell rm /sdcard/d.xml 2>&1 | Out-Null
    &$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
    Wait 450
    &$adb pull /sdcard/d.xml "F:/KotlinProject/CandyIDE/d1.xml" 2>&1 | Out-Null
    Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw
}
$raw = Dump
$p = FindXY $raw "text" "demo"; if ($p) { &$adb shell input tap $p[0] $p[1] 2>&1 | Out-Null }
Wait 2500
# hello.subl 有 print/main/s/add 等符号：在 var s = 后面输入 s
&$adb shell input tap 630 1100 2>&1 | Out-Null
Wait 700
&$adb logcat -c 2>&1 | Out-Null
&$adb shell input text "s" 2>&1 | Out-Null
Wait 1200
$log = (&$adb logcat -d 2>&1) -join [char]10
Write-Host "--- after typing s ---"
foreach ($l in ($log -split [char]10)) { if ($l -match "SUBL-COMP") { Write-Host $l } }
&$adb logcat -c 2>&1 | Out-Null
&$adb shell input text "a" 2>&1 | Out-Null
Wait 1000
$log = (&$adb logcat -d 2>&1) -join [char]10
Write-Host "--- after typing sa ---"
foreach ($l in ($log -split [char]10)) { if ($l -match "SUBL-COMP") { Write-Host $l } }