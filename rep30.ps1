$adb = "F:/AndroidSDK/platform-tools/adb.exe"
function Wait($ms) { Start-Sleep -Milliseconds $ms }
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
&$adb shell rm /sdcard/d.xml 2>&1 | Out-Null
&$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
Wait 500
&$adb pull /sdcard/d.xml "F:/KotlinProject/CandyIDE/d1.xml" 2>&1 | Out-Null
$raw = Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw
$p = FindXY $raw "text" "demo"; if ($p) { &$adb shell input tap $p[0] $p[1] 2>&1 | Out-Null }
Wait 2500
$log = (&$adb logcat -d 2>&1) -join [char]10
Write-Host "--- logs after open project ---"
foreach ($l in ($log -split [char]10)) { if ($l -match "SUBL-COMP") { Write-Host $l } }
# 点编辑器并输入 pri
&$adb shell input tap 630 1500 2>&1 | Out-Null
Wait 800
&$adb logcat -c 2>&1 | Out-Null
&$adb shell input text "pri" 2>&1 | Out-Null
Wait 1500
$log = (&$adb logcat -d 2>&1) -join [char]10
Write-Host "--- logs after typing pri ---"
foreach ($l in ($log -split [char]10)) { if ($l -match "SUBL-COMP") { Write-Host $l } }