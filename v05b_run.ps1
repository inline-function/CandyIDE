$ErrorActionPreference = "Continue"
$adb = "F:/AndroidSDK/platform-tools/adb.exe"
$x = "F:/KotlinProject/CandyIDE/d.xml"
$flag = "F:/KotlinProject/CandyIDE/v05b.txt"
"" | Out-File $flag
$Q = [char]34
$LB = "[[]"
$RB = "[]]"
$NUM = "[0-9]+"
function Wait($ms) { Start-Sleep -Milliseconds $ms }
function Dump {
    $ok = $false
    for ($i = 0; $i -lt 3 -and -not $ok; $i++) {
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
Log ("[2] editor hello.subl tab: " + $raw.Contains("hello.subl"))

# 3: 打开文件树（正确坐标由 desc 定位）
ClickD $raw "文件树" | Out-Null
Wait 800
$raw = Dump
Log ("[3] drawer 新建文件 btn: " + ($raw -match ($Q + "新建文件" + $Q)))

# 4: 新建 indent.subl
ClickD $raw "新建文件" | Out-Null
Wait 800
ClickT (Dump) "创建" | Out-Null
Wait 1200
$raw = Dump
Log ("[4a] indent.subl in tree? (default new.subl) " + $raw.Contains("new.subl"))

# 5: CRUD 文件名先不改，直接测切换文件闪退修复：点 new.subl
function ClickTXY($t) { $r = Dump; $p = Find $r "text" $t; if ($p) { Tap $p.x $p.y; return $true }; return $false }
&$adb logcat -c 2>&1 | Out-Null
ClickTXY "new.subl" | Out-Null
Wait 2000
$raw = Dump
Log ("[5a] opened new.subl (tab+title): " + ($raw -split "new.subl").Length)
ClickTXY "hello.subl" | Out-Null
Wait 2000
$raw = Dump
Log ("[5b] back to hello.subl: " + ($raw -split "hello.subl").Length)
$fatal = (&$adb logcat -d 2>&1) -join "`n"
Log ("[5c] FATAL: " + ($fatal.Contains("FATAL EXCEPTION")))

# 6: 关抽屉：点右侧遮罩区（屏幕右端 95% 处）
&$adb shell input tap 1200 1300 2>&1 | Out-Null
Wait 800
$raw = Dump
$treeOpen = ($raw -match ($Q + "新建文件" + $Q))
Log ("[6] drawer still open after scrim tap: " + $treeOpen)

# 7: 自动缩进：新建 indent.subl 再进去打字
ClickD $raw "文件树" | Out-Null
Wait 800
$raw = Dump
ClickD $raw "新建文件" | Out-Null
Wait 800
&$adb shell input text "indent.subl" 2>&1 | Out-Null
Wait 400
ClickT (Dump) "创建" | Out-Null
Wait 1200
ClickTXY "indent.subl" | Out-Null
Wait 800
&$adb shell input text "fun%sf%s()" 2>&1 | Out-Null
Wait 300
&$adb shell input text "%s{" 2>&1 | Out-Null
Wait 300
&$adb shell input keyevent 66 2>&1 | Out-Null
Wait 700
&$adb shell input text "println(1)" 2>&1 | Out-Null
Wait 300
&$adb shell input keyevent 66 2>&1 | Out-Null
Wait 700
&$adb shell input text "}" 2>&1 | Out-Null
Wait 400
$raw = Dump
ClickD $raw "保存" | Out-Null
Wait 1200
$f = (&$adb shell "run-as candy.ide cat files/candy/projects/demo/indent.subl" 2>&1) -join ";"
Log ("[7] indent file: " + $f)

# 8: 长按菜单 + 点别处消失
&$adb shell input swipe 600 1100 600 1100 900 2>&1 | Out-Null
Wait 800
$raw = Dump
Log ("[8a] long menu items: " + $raw.Contains("转到定义") + " " + $raw.Contains("剪切文本"))
&$adb shell input tap 600 1500 2>&1 | Out-Null
Wait 600
$raw = Dump
Log ("[8b] menu dismissed by tap: " + (-not $raw.Contains("转到定义")))

# 9: 运行流程
&$adb logcat -c 2>&1 | Out-Null
ClickD $raw "运行" | Out-Null
Wait 5000
$raw = Dump
Log ("[9a] drawer open after run: " + ($raw -match ($Q + "诊断" + $Q)))
Log ("[9b] output Hello: " + $raw.Contains("Hello, Sublimation!"))
Log ("[9c] NO clear btn: " + (-not ($raw -match ($Q + "清空" + $Q))))

$alive = (&$adb shell "ps -A | grep candy.ide" 2>&1)
Log ("[10] alive: " + $alive)
$crash = (&$adb logcat -d -b crash 2>&1 | Select-Object -Last 4) -join ";"
Log ("[11] crash: " + $crash)
Get-Content $flag