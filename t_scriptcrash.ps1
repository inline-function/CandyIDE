$ErrorActionPreference = 'Continue'
$adb = "F:/AndroidSDK/platform-tools/adb.exe"
$x = "F:/KotlinProject/CandyIDE/d.xml"
$flag = "F:/KotlinProject/CandyIDE/crash-result.txt"
"=== script-diag repro " + (Get-Date) + " ===" | Out-File $flag
$LB = [regex]::Escape('['); $RB = [regex]::Escape(']'); $NUM = '[0-9]+'

function Wait-Ms($ms) { Start-Sleep -Milliseconds $ms }
function Dump-Raw {
    &$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
    Start-Sleep -Milliseconds 400
    &$adb pull /sdcard/d.xml $x 2>&1 | Out-Null
    Get-Content $x -Raw
}
function Tap($px, $py) { &$adb shell input tap $px $py 2>&1 | Out-Null }
function Click-Text($raw, $t) {
    $re = '<node[^>]*text="' + $t + '"[^>]*bounds="' + $LB + '(' + $NUM + '),(' + $NUM + ')' + $RB + $LB + '(' + $NUM + '),(' + $NUM + ')' + $RB + '"'
    $m = [regex]::Match($raw, $re)
    if ($m.Success) { $cx = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2; $cy = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2; Tap $cx $cy; ("  tap '" + $t + "' @ " + $cx + "," + $cy) | Out-File $flag -Append; return $true }
    ("  MISS '" + $t + "'") | Out-File $flag -Append; return $false
}
function Click-Desc($raw, $t) {
    $re = '<node[^>]*content-desc="' + $t + '"[^>]*bounds="' + $LB + '(' + $NUM + '),(' + $NUM + ')' + $RB + $LB + '(' + $NUM + '),(' + $NUM + ')' + $RB + '"'
    $m = [regex]::Match($raw, $re)
    if ($m.Success) { $cx = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2; $cy = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2; Tap $cx $cy; ("  tap desc '" + $t + "' @ " + $cx + "," + $cy) | Out-File $flag -Append; return $true }
    ("  MISS desc '" + $t + "'") | Out-File $flag -Append; return $false
}
function Has($raw, $t) { $raw.Contains($t) }

&$adb logcat -c 2>&1 | Out-Null
&$adb shell am start -n candy.ide/.MainActivity 2>&1 | Out-Null
Wait-Ms 2500
$raw = Dump-Raw
# 回到工程列表
if (-not (Has $raw "新建工程")) { &$adb shell input keyevent 4 2>&1 | Out-Null; Wait-Ms 800; $raw = Dump-Raw }
("projects screen: " + (Has $raw "新建工程")) | Out-File $flag -Append

# 新建脚本工程
Click-Text $raw "新建工程" | Out-Null
Wait-Ms 800
$raw = Dump-Raw
("dialog: " + (Has $raw "工程类型")) | Out-File $flag -Append
# 工程名输入：点文本框再 input text
$reField = '<node[^>]*class="android.widget.EditText"[^>]*bounds="' + $LB + '(' + $NUM + '),(' + $NUM + ')' + $RB + $LB + '(' + $NUM + '),(' + $NUM + ')' + $RB + '"'
$m = [regex]::Match($raw, $reField)
if ($m.Success) {
  $cx = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2; $cy = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
  Tap $cx $cy; Wait-Ms 600
  &$adb shell input text "crashtest" 2>&1 | Out-Null
  Wait-Ms 500
}
$raw = Dump-Raw
Click-Text $raw "脚本工程" | Out-Null
Wait-Ms 400
$raw = Dump-Raw
Click-Text $raw "创建" | Out-Null
Wait-Ms 3500
$raw = Dump-Raw
("editor opened (hello.subs tab): " + (Has $raw "hello.subs")) | Out-File $flag -Append
("file tree hello.subs: " + (Has $raw "hello.subs")) | Out-File $flag -Append

# 等 LSP 诊断
Wait-Ms 6000
# 打开抽屉
$raw = Dump-Raw
$ok = Click-Desc $raw "输出/日志/诊断"
Wait-Ms 800
$raw = Dump-Raw
("drawer open: " + (Has $raw "输出")) | Out-File $flag -Append
# 诊断页签
Click-Text $raw "诊断" | Out-Null
Wait-Ms 800
$raw = Dump-Raw
("diag tab text presence: " + (Has $raw "诊断")) | Out-File $flag -Append
# 保存当前 dump 以查看诊断条目
$raw | Out-File "F:/KotlinProject/CandyIDE/diagdrawer.txt"
# 点第一条诊断（文本含 line/col？先点非tab文本区域）
$rows = [regex]::Matches($raw, '<node[^>]*text="([^"]*)"[^>]*clickable="true"[^>]*bounds="[^"]*"')
("clickable rows: " + $rows.Count) | Out-File $flag -Append
$clicked = $false
foreach ($r in $rows) {
    $t = $r.Groups[1].Value
    if ($t -ne "诊断" -and $t -ne "输出" -and $t -ne "日志" -and $t.Length -gt 4) {
        Click-Text $raw $t | Out-Null
        ("clicked diag row: " + $t) | Out-File $flag -Append
        $clicked = $true
        break
    }
}
if (-not $clicked) { "no diag row found to click" | Out-File $flag -Append }
Wait-Ms 2000
$alive = (&$adb shell "ps -A | grep candy.ide" 2>&1)
("process after click: " + $alive) | Out-File $flag -Append
$crash = (&$adb logcat -d -b crash 2>&1 | Select-Object -Last 25)
("CRASH LOG: " + $crash) | Out-File $flag -Append
Get-Content $flag
