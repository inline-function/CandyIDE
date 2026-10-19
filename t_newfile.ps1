$ErrorActionPreference = 'Continue'
$adb = "F:/AndroidSDK/platform-tools/adb.exe"
$x = "F:/KotlinProject/CandyIDE/d.xml"
$out = "F:/KotlinProject/CandyIDE/t1.txt"

function Wait-Ms($ms) { Start-Sleep -Milliseconds $ms }

$LB = [regex]::Escape('[')
$RB = [regex]::Escape(']')
$NUM = '[0-9]+'
function Dump-Raw {
    &$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
    Start-Sleep -Milliseconds 400
    &$adb pull /sdcard/d.xml $x 2>&1 | Out-Null
    Get-Content $x -Raw
}
function Tap($px, $py) { &$adb shell input tap $px $py 2>&1 | Out-Null }
function Click-Attr($raw, $attr, $val) {
    $re = '<node[^>]*' + $attr + '="' + $val + '"[^>]*bounds="' + $LB + '(' + $NUM + '),(' + $NUM + ')' + $RB + $LB + '(' + $NUM + '),(' + $NUM + ')' + $RB + '"'
    $m = [regex]::Match($raw, $re)
    if ($m.Success) {
        $cx = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
        $cy = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
        Tap $cx $cy
        ("  tap " + $attr + "='" + $val + "' @ " + $cx + "," + $cy) | Out-File $out -Append
        return $true
    }
    ("  MISS " + $attr + "='" + $val + "'") | Out-File $out -Append
    return $false
}

&$adb shell am start -n candy.ide/.MainActivity 2>&1 | Out-Null
Wait-Ms 2500
$raw = Dump-Raw

# 确保在编辑器：若在工程列表则打开 demo
if ($raw.Contains("hello.subl")) {
    "on editor already" | Out-File $out -Append
} else {
    Click-Attr $raw "text" "demo" | Out-Null
    Wait-Ms 2500
    $raw = Dump-Raw
    ("editor tab: " + $raw.Contains("hello.subl")) | Out-File $out -Append
}

# 打开文件树抽屉
Click-Attr $raw "content-desc" "文件树" | Out-Null
Wait-Ms 1200
$raw = Dump-Raw
("drawer open, tree has hello.subl: " + $raw.Contains("hello.subl")) | Out-File $out -Append
$rawTree = $raw

# 点新建文件
Click-Attr $rawTree "content-desc" "新建文件" | Out-Null
Wait-Ms 1000
$raw = Dump-Raw
("create dialog shown (创建 btn): " + $raw.Contains("创建")) | Out-File $out -Append
("dialog has new.subl initial: " + $raw.Contains("new.subl")) | Out-File $out -Append

# 截图当前（对话框 + 树）便于我肉眼检查
&$adb shell screencap -p /sdcard/shot1.png 2>&1 | Out-Null
&$adb pull /sdcard/shot1.png F:/KotlinProject/CandyIDE/shot1.png 2>&1 | Out-Null

# 点创建
Click-Attr $raw "text" "创建" | Out-Null
Wait-Ms 1500
$raw = Dump-Raw
("after create, tree contains new.subl: " + $raw.Contains("new.subl")) | Out-File $out -Append
Get-Content $out
