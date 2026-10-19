$adb = "F:/AndroidSDK/platform-tools/adb.exe"
$Q = [char]34; $LB = "[[]"; $RB = "[]]"; $NUM = "[0-9]+"
function Wait($ms) { Start-Sleep -Milliseconds $ms }
function FindXY($raw, $attr, $t) {
    $re = "<node[^>]*" + $attr + "=" + $Q + $t + $Q + "[^>]*bounds=" + $Q + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $Q
    $m = [regex]::Match($raw, $re)
    if ($m.Success) { return @((([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2), (([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)) }
    return $null
}
&$adb shell input tap 630 1200 2>&1 | Out-Null
Wait 900
&$adb shell input text "abcxyz" 2>&1 | Out-Null
Wait 800
&$adb shell input keyevent 66 2>&1 | Out-Null
Wait 900
&$adb shell input text "qqq" 2>&1 | Out-Null
Wait 800
$raw = $null
&$adb shell rm /sdcard/d.xml 2>&1 | Out-Null
&$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
Wait 500
&$adb pull /sdcard/d.xml "F:/KotlinProject/CandyIDE/d1.xml" 2>&1 | Out-Null
$raw = Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw
$p = FindXY $raw "content-desc" "保存"
if ($p) { Write-Host ("tap save @ " + $p[0] + "," + $p[1]); &$adb shell input tap $p[0] $p[1] 2>&1 | Out-Null } else { Write-Host "MISS save" }
Wait 1400
$f = (&$adb shell "run-as candy.ide cat files/candy/projects/demo/new.subl" 2>&1)
Write-Host "FILE-BEGIN"
foreach ($l in $f) { Write-Host ("|" + $l + "|") }
Write-Host "FILE-END"