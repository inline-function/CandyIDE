$ErrorActionPreference = "Continue"
$adb = "F:/AndroidSDK/platform-tools/adb.exe"
$x = "F:/KotlinProject/CandyIDE/d.xml"
$flag = "F:/KotlinProject/CandyIDE/v05d.txt"
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
function FindXY($raw, $attr, $t) {
    $re = "<node[^>]*" + $attr + "=" + $Q + $t + $Q + "[^>]*bounds=" + $Q + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $Q
    $m = [regex]::Match($raw, $re)
    if ($m.Success) { return @((([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2), (([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)) }
    return $null
}
function Log($m) { $m | Out-File $flag -Append; Write-Host $m }
function CrashDump($tag) {
    $alog = (&$adb logcat -d -b crash 2>&1) -join [char]10
    $fi = $alog.IndexOf("FATAL EXCEPTION")
    if ($fi -ge 0) { Log ("[" + $tag + "] CRASH: " + $alog.Substring($fi, [Math]::Min(600, $alog.Length - $fi))) }
    else { Log ("[" + $tag + "] no crash") }
}
# 抽屉开 = text="输出" 的 tab 节点存在（顶栏图标是 content-desc，不算）
function DrawerOpen($raw) {
    $re = "<node[^>]*class=" + $Q + "android.widget.TextView" + $Q + "[^>]*text=" + $Q + "输出" + $Q + "[^>]"
    return ([regex]::IsMatch($raw, $re))
}
&$adb shell svc power stayon true 2>&1 | Out-Null
&$adb shell am force-stop candy.ide 2>&1 | Out-Null
&$adb shell am start -n candy.ide/.MainActivity 2>&1 | Out-Null
Wait 4000
$raw = Dump
Log ("[1] projects: " + $raw.Contains("demo"))
$p = FindXY $raw "text" "demo"; if ($p) { Tap $p[0] $p[1] }
Wait 2500
$raw = Dump
Log ("[2] editor: " + $raw.Contains("hello.subl"))

Log "[3] 切到 new.subl 标签 + 打字测自动缩进"
$p = FindXY $raw "text" "new.subl"
if ($p) { Tap $p[0] $p[1]; Log ("  switched tab new.subl") } else { Log "  MISS tab new.subl" }
Wait 1000
Tap 630 1200
Wait 900
&$adb shell input text "fun%sf%s()" 2>&1 | Out-Null
Wait 400
&$adb shell input text "%s{" 2>&1 | Out-Null
Wait 400
&$adb shell input keyevent 66 2>&1 | Out-Null
Wait 900
&$adb shell input text "println(1)" 2>&1 | Out-Null
Wait 400
&$adb shell input keyevent 66 2>&1 | Out-Null
Wait 900
&$adb shell input text "}" 2>&1 | Out-Null
Wait 400
$raw = Dump
$p = FindXY $raw "content-desc" "保存"
if ($p) { Tap $p[0] $p[1]; Log "  saved" } else { Log "  MISS save" }
Wait 1300
$f = (&$adb shell "run-as candy.ide cat files/candy/projects/demo/new.subl" 2>&1) -join [char]10
Log ("[3a] file content:")
Log $f
CrashDump "3b"

Log "[4] 回 hello.subl 运行 → 输出抽屉"
$raw = Dump
$p = FindXY $raw "text" "hello.subl"
if ($p) { Tap $p[0] $p[1] }
Wait 900
$raw = Dump
$p = FindXY $raw "content-desc" "运行"
if ($p) { Tap $p[0] $p[1] }
Wait 6000
$raw = Dump
Log ("[4a] drawer open: " + (DrawerOpen $raw))
Log ("[4b] output Hello: " + $raw.Contains("Hello, Sublimation!"))
Log ("[4c] no 清空: " + (-not $raw.Contains("清空")))
CrashDump "4d"

Log "[5] 点遮罩关输出抽屉"
$p = FindXY $raw "content-desc" "输出/日志/诊断"
if ($p) { Tap $p[0] $p[1] }
Wait 1000
$raw = Dump
Log ("[5a] drawer reopened: " + (DrawerOpen $raw))
Tap 60 1200
Wait 900
Log ("[5b] drawer closed by scrim: " + (-not (DrawerOpen (Dump))))

Log "[6] 诊断悬浮窗：hello.subl 末尾加个语法错误"
$raw = Dump
$p = FindXY $raw "text" "hello.subl"
if ($p) { Tap $p[0] $p[1] }
Wait 800
Tap 630 1500
Wait 700
&$adb shell input keyevent 66 2>&1 | Out-Null
Wait 400
&$adb shell input text "fun%sbad()" 2>&1 | Out-Null
Wait 400
$raw = Dump
$p = FindXY $raw "content-desc" "保存"
if ($p) { Tap $p[0] $p[1] }
Wait 1800
$raw = Dump
Log ("[6a] diag badge count nodes: " + ($raw -split "诊断").Length)
# 光标移动到错误处：CodeEditor 无 text，点击行区域。先点编辑区中部触发光标变化事件
Tap 700 1500
Wait 800
Wait 800
$raw = Dump
$ms = [regex]::Matches($raw, "text=" + $Q + "[^" + $Q + "]" + $Q)
Log ("[6b] dump texts after cursor at error: " + ($ms.Count))
CrashDump "6c"

Log "[7] 长按重命名 LSP"
&$adb shell input swipe 500 1400 500 1400 900 2>&1 | Out-Null
Wait 800
$raw = Dump
Log ("[7a] menu: " + $raw.Contains("重命名"))
$p = FindXY $raw "text" "重命名"
if ($p) { Tap $p[0] $p[1] }
Wait 800
$raw = Dump
Log ("[7b] rename dialog: " + $raw.Contains("重命名"))
CrashDump "7c"
Get-Content $flag