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
function ClickD($raw, $t) {
    $re = "<node[^>]*content-desc=" + $Q + $t + $Q + "[^>]*bounds=" + $Q + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $Q
    $m = [regex]::Match($raw, $re)
    if (-not $m.Success) { Write-Host ("MISS " + $t); return $false }
    $cx = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
    $cy = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
    Write-Host ("tap " + $t + " @ " + $cx + "," + $cy)
    &$adb shell input tap $cx $cy 2>&1 | Out-Null
    Wait 700
    return $true
}
&$adb shell input tap 100 1200 2>&1 | Out-Null
Wait 900
$raw = Dump
Write-Host ("drawer closed: " + (-not $raw.Contains("诊断")) + " / editor up: " + $raw.Contains("hello.subl"))
$re2 = "<node[^>]*text=" + $Q + "new.subl" + $Q + "[^>]*bounds=" + $Q + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $Q
$m2 = [regex]::Match($raw, $re2)
if ($m2.Success) { $cx = ([int]$m2.Groups[1].Value + [int]$m2.Groups[3].Value) / 2; $cy = ([int]$m2.Groups[2].Value + [int]$m2.Groups[4].Value) / 2; Write-Host "switch new.subl"; &$adb shell input tap $cx $cy 2>&1 | Out-Null; Wait 900 } else { Write-Host "no new.subl tab" }
&$adb shell input tap 630 1200 2>&1 | Out-Null
Wait 1000
&$adb shell input text "fun%sf%s()" 2>&1 | Out-Null
Wait 600
ClickD (Dump) "保存" | Out-Null
Wait 1200
$f = (&$adb shell "run-as candy.ide cat files/candy/projects/demo/new.subl" 2>&1) -join [char]10
Write-Host "FILE-BEGIN"
Write-Host $f
Write-Host "FILE-END"