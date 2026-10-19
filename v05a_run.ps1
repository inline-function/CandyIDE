$ErrorActionPreference = "Continue"
$adb = "F:/AndroidSDK/platform-tools/adb.exe"
$x = "F:/KotlinProject/CandyIDE/d.xml"
$flag = "F:/KotlinProject/CandyIDE/v05a.txt"
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
        $r = (&$adb shell uiautomator dump /sdcard/d.xml 2>&1) -join ""
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
Log ("[2] editor tab hello.subl: " + $raw.Contains("hello.subl"))

Log "[3] run flow"
ClickD $raw "运行" | Out-Null
Wait 5000
$raw = Dump
Log ("[3a] drawer tabs present: " + ($raw.Contains("输出") -and $raw.Contains("诊断")))
Log ("[3b] output Hello: " + $raw.Contains("Hello, Sublimation!"))

ClickD $raw "输出/日志/诊断" | Out-Null
Wait 600
ClickD (Dump) "文件树" | Out-Null
Wait 1000
$raw = Dump
Log ("[4] tree hello.subl: " + $raw.Contains("hello.subl"))
Log ("[4b] tree demo root: " + $raw.Contains("demo"))

Log "[5] new file"
ClickD $raw "新建文件" | Out-Null
Wait 800
ClickT (Dump) "创建" | Out-Null
Wait 1500
$raw = Dump
Log ("[5a] new.subl in tree: " + $raw.Contains("new.subl"))

Log "[6] new dir"
ClickD $raw "新建目录" | Out-Null
Wait 800
&$adb shell input text "d1" 2>&1 | Out-Null
Wait 400
ClickT (Dump) "创建" | Out-Null
Wait 1200
$raw = Dump
Log ("[6a] dir d1 in tree: " + $raw.Contains("d1"))

Log "[7] auto indent"
ClickT $raw "new.subl" | Out-Null
Wait 800
&$adb shell input text "fun%sf%s()" 2>&1 | Out-Null
Wait 300
&$adb shell input text "%s{" 2>&1 | Out-Null
Wait 300
&$adb shell input keyevent 66 2>&1 | Out-Null
Wait 600
&$adb shell input text "println(1)" 2>&1 | Out-Null
Wait 300
&$adb shell input keyevent 66 2>&1 | Out-Null
Wait 600
&$adb shell input text "}" 2>&1 | Out-Null
Wait 400
$raw = Dump
ClickD $raw "保存" | Out-Null
Wait 1200
$f = (&$adb shell "run-as candy.ide cat files/candy/projects/demo/new.subl" 2>&1) -join ";"
Log ("[7a] saved file: " + $f)

$alive = (&$adb shell "ps -A | grep candy.ide" 2>&1)
Log ("[8] alive: " + $alive)
$crash = (&$adb logcat -d -b crash 2>&1 | Select-Object -Last 5) -join ";"
Log ("[9] crash: " + $crash)
Get-Content $flag