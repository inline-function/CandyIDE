$adb = "F:/AndroidSDK/platform-tools/adb.exe"
$Q = [char]34; $LB = "[[]"; $RB = "[]]"; $NUM = "[0-9]+"
function Wait($ms) { Start-Sleep -Milliseconds $ms }
function Dump {
    &$adb shell rm /sdcard/d.xml 2>&1 | Out-Null
    &$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
    Wait 450
    &$adb pull /sdcard/d.xml "F:/KotlinProject/CandyIDE/d1.xml" 2>&1 | Out-Null
    Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw
}
function FindXY($raw, $attr, $t) {
    $re = "<node[^>]*" + $attr + "=" + $Q + $t + $Q + "[^>]*bounds=" + $Q + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $Q
    $m = [regex]::Match($raw, $re)
    if ($m.Success) { return @((([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2), (([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)) }
    return $null
}
# 当前在 new.subl，先输入一个 print 触发补全
&$adb shell input tap 630 1500 2>&1 | Out-Null
Wait 600
&$adb shell input text "pr" 2>&1 | Out-Null
Wait 1000
$raw = Dump
Write-Host "--- completion dump texts ---"
$ms = [regex]::Matches($raw, "text=" + $Q + "([^" + $Q + "]{1,40})" + $Q)
foreach ($x in $ms) { Write-Host ("  " + $x.Groups[1].Value) }
$i = $raw.IndexOf("Completion")
Write-Host ("completion class present: " + ($i -ge 0))