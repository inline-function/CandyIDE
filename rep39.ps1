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
function TapN($raw, $attr, $t) {
    $p = FindXY $raw $attr $t
    if ($p) { Write-Host ("  tap " + $t + " @ " + $p[0] + "," + $p[1]); &$adb shell input tap $p[0] $p[1] 2>&1 | Out-Null; Wait 900; return $true }
    Write-Host ("  MISS " + $t)
    return $false
}
$raw = Dump
# 先点空白行把可能存在的诊断悬浮窗关掉（点编辑器其他位置 → tooltip 显示条件依赖光标）
Wait 300
&$adb shell input swipe 630 1190 630 1190 900 2>&1 | Out-Null
Wait 900
$raw = Dump
Write-Host ("menu up: " + ($raw.Contains('重命名') -and $raw.Contains('转到定义')))
if ($raw.Contains('重命名')) {
  $p = FindXY $raw "text" "重命名"
  if ($p) { &$adb shell input tap $p[0] $p[1] 2>&1 | Out-Null; Wait 900 }
  $raw = Dump
  Write-Host ("dialog: " + $raw.Contains('重命名'))
  $ms = [regex]::Matches($raw, "text=" + $Q + "([^" + $Q + "]{1,40})" + $Q)
  foreach ($x in $ms) { Write-Host ("  T: " + $x.Groups[1].Value) }
  for ($k = 0; $k -lt 6; $k++) { &$adb shell input keyevent 67 2>&1 | Out-Null; Wait 150 }
  Wait 300
  &$adb shell input text "printlnx" 2>&1 | Out-Null
  Wait 600
  $raw = Dump
  TapN $raw "text" "确认" | Out-Null
  Wait 1500
  $f = (&$adb shell "run-as candy.ide cat files/candy/projects/demo/hello.subl" 2>&1) -join [char]10
  Write-Host "AFTER-RENAME-BEGIN"
  foreach ($l in ($f -split [char]10)) { Write-Host ("|" + $l + "|") }
  Write-Host "AFTER-RENAME-END"
  $alog = (&$adb logcat -d -b crash 2>&1) -join [char]10
  Write-Host ("crash: " + $alog.Contains('FATAL'))
} else { Write-Host "menu never opened" }