$adb = "F:/AndroidSDK/platform-tools/adb.exe"
$Q = [char]34; $LB = "[[]"; $RB = "[]]"; $NUM = "[0-9]+"
function Wait($ms) { Start-Sleep -Milliseconds $ms }
function Dump {
    $ok = $false
    for ($i = 0; $i -lt 4 -and -not $ok; $i++) {
        &$adb shell rm /sdcard/d.xml 2>&1 | Out-Null
        &$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
        Wait 500
        $p = (&$adb pull /sdcard/d.xml "F:/KotlinProject/CandyIDE/d1.xml" 2>&1) -join ""
        if ($p.Contains('1 file pulled')) { $t = Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw; if ($t.Length -gt 500) { $ok = $true } }
        if (-not $ok) { Wait 700 }
    }
    return Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw
}
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
function Crash($tag) {
    $alog = (&$adb logcat -d -b crash 2>&1) -join [char]10
    if ($alog.Contains('FATAL EXCEPTION')) { Write-Host ("[" + $tag + "] CRASH!") } else { Write-Host ("[" + $tag + "] ok") }
}
function ShowTexts($tag, $raw) {
    Write-Host ("--- " + $tag + " ---")
    $ms = [regex]::Matches($raw, "text=" + $Q + "([^" + $Q + "]{1,40})" + $Q)
    $seen = @{}
    foreach ($x in $ms) { $k = $x.Groups[1].Value; if (-not $seen.ContainsKey($k)) { $seen[$k] = 1; Write-Host ("  " + $k) } }
}
&$adb shell am force-stop candy.ide 2>&1 | Out-Null
&$adb logcat -c 2>&1 | Out-Null
&$adb shell am start -n candy.ide/.MainActivity 2>&1 | Out-Null
Wait 4000
$raw = Dump
TapN $raw "text" "demo" | Out-Null
Wait 2200
$raw = Dump
Write-Host "[A] 目录选中新建：开树 -> 新建目录 src"
TapN $raw "content-desc" "文件树" | Out-Null
$raw = Dump
TapN $raw "content-desc" "新建目录" | Out-Null
Wait 700
&$adb shell input text "src" 2>&1 | Out-Null
Wait 400
TapN (Dump) "text" "创建" | Out-Null
Wait 1200
$raw = Dump
Write-Host "[A2] 选中 src 目录"
TapN $raw "text" "src" | Out-Null
Wait 700
$raw = Dump
Write-Host ("  新建到这里提示: " + $raw.Contains('新建到这里'))
TapN $raw "content-desc" "新建文件" | Out-Null
Wait 700
&$adb shell input text "inner.subl" 2>&1 | Out-Null
Wait 400
TapN (Dump) "text" "创建" | Out-Null
Wait 1200
$f = (&$adb shell "run-as candy.ide ls files/candy/projects/demo/src" 2>&1) -join ";"
Write-Host ("[A3] demo/src listing: " + $f)
Crash "A4"

# 关掉抽屉
&$adb shell input tap 1200 1300 2>&1 | Out-Null
Wait 800

Write-Host "[B] 长按重命名 LSP"
&$adb shell input swipe 630 1100 630 1100 900 2>&1 | Out-Null
Wait 800
$raw = Dump
ShowTexts "long-menu" $raw
TapN $raw "text" "重命名" | Out-Null
$raw = Dump
ShowTexts "rename-dialog" $raw
# 对话框里有文本框，直接追加重命名后缀
&$adb shell input keyevent 66 2>&1 | Out-Null
Wait 300
$raw = Dump
TapN $raw "text" "确认" | Out-Null
Wait 1200
$f2 = (&$adb shell "run-as candy.ide cat files/candy/projects/demo/hello.subl" 2>&1) -join [char]10
Write-Host "[B2] hello.subl after rename: lines=" + ($f2 -split [char]10).Count
Crash "B3"

Write-Host "[C] 诊断页 + 超链接"
$raw = Dump
TapN $raw "content-desc" "输出/日志/诊断" | Out-Null
$raw = Dump
ShowTexts "drawer" $raw
TapN $raw "text" "诊断" | Out-Null
Wait 800
$raw = Dump
ShowTexts "diag-tab" $raw
Crash "C2"