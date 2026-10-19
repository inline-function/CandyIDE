$ErrorActionPreference = 'Continue'
$adb = "F:/AndroidSDK/platform-tools/adb.exe"
$xmlPath = "F:/KotlinProject/CandyIDE/d.xml"
$flagFile = "F:/KotlinProject/CandyIDE/v04-result.txt"
"=== v0.4 verify " + (Get-Date) + " ===" | Out-File $flagFile

function Wait-Ms($ms) { Start-Sleep -Milliseconds $ms }
function Log($m) { Write-Host $m; $m | Out-File $flagFile -Append }
function Dump-Raw {
    &$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
    Start-Sleep -Milliseconds 400
    &$adb pull /sdcard/d.xml $xmlPath 2>&1 | Out-Null
    Get-Content $xmlPath -Raw
}
function Click-Attr($raw, $attr, $val) {
    $re = '<node[^>]*' + $attr + '="' + $val + '"[^>]*bounds="[(d+),(d+)][(d+),(d+)]"'
    $m = [regex]::Match($raw, $re)
    if ($m.Success) {
        $cx = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
        $cy = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
        &$adb shell input tap $cx $cy 2>&1 | Out-Null
        Write-Host ("  tapped " + $attr + "='" + $val + "' at " + $cx + "," + $cy)
        return $true
    }
    Write-Host ("  MISS " + $attr + "='" + $val + "'")
    return $false
}
function Click-Text($raw, $t) { Click-Attr $raw "text" $t }
function Click-Desc($raw, $t) { Click-Attr $raw "content-desc" $t }
function Has($raw, $t) { $raw.Contains($t) }

&$adb shell svc power stayon true 2>&1 | Out-Null

$raw = Dump-Raw
Log ("1) list demo/JS: " + ((Has $raw "demo") -and (Has $raw "JS 平台工程")))

Click-Text $raw "demo" | Out-Null
Wait-Ms 2500
$raw = Dump-Raw
Log ("2) tab hello.subl: " + (Has $raw "hello.subl"))
Log ("2b) demo source visible: " + (Has $raw "@impure"))
$rawEditor = $raw

Click-Desc $rawEditor "运行" | Out-Null
Wait-Ms 3500
$raw = Dump-Raw
Log ("3a) drawer open: " + (Has $raw "输出"))
Log ("3b) output Hello: " + (Has $raw "Hello, Sublimation!"))
Log ("3c) output 42: " + (Has $raw "42"))
$log = (&$adb logcat -d -s CandyEngine 2>&1 | Select-Object -Last 4) -join ' ; '
Log ("logcat: " + $log)
