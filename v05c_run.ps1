$ErrorActionPreference = "Continue"
$adb = "F:/AndroidSDK/platform-tools/adb.exe"
$x = "F:/KotlinProject/CandyIDE/d.xml"
$flag = "F:/KotlinProject/CandyIDE/v05c.txt"
"" | Out-File $flag
$Q = [char]34
$LB = "[[]"
$RB = "[]]"
$NUM = "[0-9]+"
function Wait($ms) { Start-Sleep -Milliseconds $ms }
function Dump {
    $ok = $false
    for ($i = 0; $i -lt 4 -and -not $ok; $i++) {
        &$adb shell rm /sdcard/d.xml 2>&1 | Out-Null
        &$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
        Wait 500
        $p = (&$adb pull /sdcard/d.xml $x 2>&1) -join ""
        if ($p.Contains("1 file pulled") -and (Test-Path $x)) { $t = Get-Content $x -Raw; if ($t.Length -gt 500 -and $t.Contains("<node")) { $ok = $true } }
        if (-not $ok) { Wait 700 }
    }
    return Get-Content $x -Raw
}
function Tap($px, $py) { &$adb shell input tap $px $py 2>&1 | Out-Null }
function Find($raw, $attr, $t) {
    $re = "<node[^>]*" + $attr + "=" + $Q + $t + $Q + "[^>]*bounds=" + $Q + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $Q
    $m = [regex]::Match($raw, $re)
    if ($m.Success) { return [pscustomobject]@{ x = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2; y = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2 } }
    return $null
}
function ClickT($raw, $t) { $p = Find $raw "text" $t; if ($p) { Tap $p.x $p.y; ("  tap text " + $t) | Out-File $flag -Append; return $true }; ("  MISS text " + $t) | Out-File $flag -Append; return $false }
function ClickD($raw, $t) { $p = Find $raw "content-desc" $t; if ($p) { Tap $p.x $p.y; ("  tap desc " + $t) | Out-File $flag -Append; return $true }; ("  MISS desc " + $t) | Out-File $flag -Append; return $false }
function Log($m) { $m | Out-File $flag -Append; Write-Host $m }
function CrashDump($tag) {
    $alog = (&$adb logcat -d -b crash 2>&1) -join [char]10
    $fi = $alog.IndexOf("FATAL EXCEPTION")
    if ($fi -ge 0) { Log ("[" + $tag + "] CRASH: " + $alog.Substring($fi, [Math]::Min(700, $alog.Length - $fi))) }
    else { Log ("[" + $tag + "] no crash") }
}
&$adb shell svc power stayon true 2>&1 | Out-Null
&$adb logcat -c 2>&1 | Out-Null
&$adb shell am force-stop candy.ide 2>&1 | Out-Null
&$adb shell am start -n candy.ide/.MainActivity 2>&1 | Out-Null
Wait 4000
$raw = Dump
Log ("[1] projects demo: " + $raw.Contains("demo"))
ClickT $raw "demo" | Out-Null
Wait 2500
$raw = Dump
Log ("[2] editor hello.subl: " + $raw.Contains("hello.subl"))

Log "[3] open tree + new file + tab switch (crash test)"
ClickD $raw "文件树" | Out-Null
$raw = Dump
ClickD $raw "新建文件" | Out-Null
ClickT (Dump) "创建" | Out-Null
Wait 1200
$raw = Dump
Log ("[3a] tree has new.subl: " + $raw.Contains("new.subl"))
&$adb logcat -c 2>&1 | Out-Null
ClickT $raw "new.subl" | Out-Null
Wait 2200
$raw = Dump
Log ("[3b] tabs now: " + (([regex]::Matches($raw, "text=" + $Q + "hello.subl" + $Q)).Count) + " hello + " + (([regex]::Matches($raw, "text=" + $Q + "new.subl" + $Q)).Count) + " new")
CrashDump "3c"

Log "[4] 从 new.subl 切回 hello.subl"
&$adb logcat -c 2>&1 | Out-Null
ClickT (Dump) "hello.subl" | Out-Null
Wait 1500
CrashDump "4a"

Log "[5] 关抽屉：点遮罩（右侧 95% 区域）"
ClickD $raw "文件树" | Out-Null
$raw = Dump
Log ("[5a] drawer open: " + $raw.Contains("新建文件"))
Tap 1190 1300
Wait 800
$raw = Dump
Log ("[5b] drawer closed after scrim tap: " + (-not $raw.Contains("新建文件")))

Log "[6] 自动缩进：在 new.subl（已打开的空文件）里打字"
&$adb logcat -c 2>&1 | Out-Null
Tap 630 1150
Wait 600
&$adb shell input text "fun%sf%s()" 2>&1 | Out-Null
Wait 300
&$adb shell input text "%s{" 2>&1 | Out-Null
Wait 300
&$adb shell input keyevent 66 2>&1 | Out-Null
Wait 800
&$adb shell input text "println(1)" 2>&1 | Out-Null
Wait 300
&$adb shell input keyevent 66 2>&1 | Out-Null
Wait 800
&$adb shell input text "}" 2>&1 | Out-Null
Wait 500
$raw = Dump
ClickD $raw "保存" | Out-Null
Wait 1200
$f = (&$adb shell "run-as candy.ide cat files/candy/projects/demo/new.subl" 2>&1) -join ";"
Log ("[6a] saved indent file: " + $f)
CrashDump "6b"

Log "[7] 长按菜单 + 点别处消失"
&$adb shell input swipe 630 1150 630 1150 900 2>&1 | Out-Null
Wait 800
$raw = Dump
Log ("[7a] long menu: " + $raw.Contains("转到定义") + " / " + $raw.Contains("剪切文本"))
Tap 630 1500
Wait 600
Log ("[7b] menu gone: " + (-not (Dump).Contains("转到定义")))

Log "[8] 运行流程"
&$adb logcat -c 2>&1 | Out-Null
ClickD (Dump) "运行" | Out-Null
Wait 6000
$raw = Dump
Log ("[8a] drawer open: " + ($raw.Contains("诊断") -or $raw.Contains("输出")))
Log ("[8b] output Hello: " + $raw.Contains("Hello, Sublimation!"))
Log ("[8c] no clear btn: " + (-not $raw.Contains("清空")))
CrashDump "8d"

$alive = (&$adb shell "ps -A | grep candy.ide" 2>&1)
Log ("[9] alive: " + $alive)
Get-Content $flag