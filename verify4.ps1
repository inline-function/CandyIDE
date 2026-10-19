$ErrorActionPreference = 'Continue'
$adb = "F:/AndroidSDK/platform-tools/adb.exe"
$xmlPath = "F:/KotlinProject/CandyIDE/d.xml"
$flagFile = "F:/KotlinProject/CandyIDE/v04-result.txt"
"=== v0.4 verify " + (Get-Date) + " ===" | Out-File $flagFile

$LB = [regex]::Escape('[')
$RB = [regex]::Escape(']')
$NUM = '[0-9]+'

function Wait-Ms($ms) { Start-Sleep -Milliseconds $ms }
function Log($m) { Write-Host $m; $m | Out-File $flagFile -Append }
function Dump-Raw {
    &$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
    Start-Sleep -Milliseconds 400
    &$adb pull /sdcard/d.xml $xmlPath 2>&1 | Out-Null
    Get-Content $xmlPath -Raw
}
function Tap($x, $y) { &$adb shell input tap $x $y 2>&1 | Out-Null }
function Click-Attr($raw, $attr, $val) {
    $re = '<node[^>]*' + $attr + '="' + $val + '"[^>]*bounds="' + $LB + '(' + $NUM + '),(' + $NUM + ')' + $RB + $LB + '(' + $NUM + '),(' + $NUM + ')' + $RB + '"'
    $m = [regex]::Match($raw, $re)
    if ($m.Success) {
        $cx = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
        $cy = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
        Tap $cx $cy
        Write-Host ("  tap " + $attr + "='" + $val + "' @ " + $cx + "," + $cy)
        return $true
    }
    Write-Host ("  MISS " + $attr + "='" + $val + "'")
    return $false
}
function Click-Text($raw, $t) { Click-Attr $raw "text" $t }
function Click-Desc($raw, $t) { Click-Attr $raw "content-desc" $t }
function Has($raw, $t) { $raw.Contains($t) }
function Center-Of($raw, $attr, $val) {
    $re = '<node[^>]*' + $attr + '="' + $val + '"[^>]*bounds="' + $LB + '(' + $NUM + '),(' + $NUM + ')' + $RB + $LB + '(' + $NUM + '),(' + $NUM + ')' + $RB + '"'
    $m = [regex]::Match($raw, $re)
    if ($m.Success) { return @(([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2, ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2) }
    return $null
}

&$adb shell svc power stayon true 2>&1 | Out-Null
&$adb logcat -c 2>&1 | Out-Null

# --- 1) 工程列表 ---
$raw = Dump-Raw
Log ("[1] list demo + JS tag: " + ((Has $raw "demo") -and (Has $raw "JS 平台工程")))

# --- 2) 打开 demo → 编辑器 ---
Click-Text $raw "demo" | Out-Null
Wait-Ms 3000
$raw = Dump-Raw
Log ("[2] editor tab 'hello.subl': " + (Has $raw "hello.subl"))
Log ("[2b] demo source '@impure': " + (Has $raw "@impure"))

# --- 3) 运行（编译模式）---
$c = Center-Of $raw "content-desc" "运行"
if ($c) { Tap $c[0] $c[1]; Log ("  run tapped") } else { Log ("  run MISS") }
Wait-Ms 4000
$raw = Dump-Raw
Log ("[3] run output 'Hello': " + (Has $raw "Hello, Sublimation!"))
Log ("[3b] run output '42': " + (Has $raw ">42"))
Log ("[3c] run output '7': " + (Has $raw ">7"))
$log = (&$adb logcat -d -s CandyEngine 2>&1 | Select-Object -Last 5) -join ' ; '
Log ("[3d] logcat: " + $log)
