$ErrorActionPreference = 'Continue'
$adb = "F:/AndroidSDK/platform-tools/adb.exe"
$xmlPath = "F:/KotlinProject/CandyIDE/d.xml"
$flagFile = "F:/KotlinProject/CandyIDE/v04-result.txt"
& $adb shell svc power stayon true | Out-Null

function Dump {
    &$adb shell uiautomator dump /sdcard/d.xml | Out-Null
    Start-Sleep -Milliseconds 400
    &$adb pull /sdcard/d.xml $xmlPath | Out-Null
    [xml](Get-Content $xmlPath -Raw)
}
function Wait-Ms($ms) { Start-Sleep -Milliseconds $ms }
function Log($m) { Write-Host $m; $m | Out-File $flagFile -Append }
function Click-Text($d, $t) {
    $n = $d.SelectNodes("//*[@text='$t']")
    foreach ($x in $n) {
        if ($x.bounds -match '[(d+),(d+)][(d+),(d+)]') {
            $cx = ([int]$Matches[1] + [int]$Matches[3]) / 2; $cy = ([int]$Matches[2] + [int]$Matches[4]) / 2
            &$adb shell input tap $cx $cy | Out-Null
            Write-Host ("  tapped text '" + $t + "' at " + $cx + "," + $cy); return $true
        }
    }
    Write-Host ("  MISS text '" + $t + "'"); return $false
}
function Click-Desc($d, $t) {
    $n = $d.SelectNodes("//*[@content-desc='$t']")
    foreach ($x in $n) {
        if ($x.bounds -match '[(d+),(d+)][(d+),(d+)]') {
            $cx = ([int]$Matches[1] + [int]$Matches[3]) / 2; $cy = ([int]$Matches[2] + [int]$Matches[4]) / 2
            &$adb shell input tap $cx $cy | Out-Null
            Write-Host ("  tapped desc '" + $t + "' at " + $cx + "," + $cy); return $true
        }
    }
    Write-Host ("  MISS desc '" + $t + "'"); return $false
}
function Has-Text($d, $t) { $d.InnerText.Contains($t) }

"=== v0.4 verify " + (Get-Date) + " ===" | Out-File $flagFile

$doc = Dump
Log ("1) list demo/JS: " + ((Has-Text $doc "demo") -and (Has-Text $doc "JS 平台工程")))

Click-Text $doc "demo" | Out-Null
Wait-Ms 2500
$doc = Dump
Log ("2) tab hello.subl: " + (Has-Text $doc "hello.subl"))
$doc2 = $doc

Click-Desc $doc2 "运行" | Out-Null
Wait-Ms 3500
$doc = Dump
Click-Desc $doc "输出/日志/诊断" | Out-Null
Wait-Ms 900
$doc = Dump
Log ("3) output Hello: " + (Has-Text $doc "Hello, Sublimation!"))
Log ("4) output 42: " + (Has-Text $doc "42"))
$log = (&$adb logcat -d -s CandyEngine | Select-Object -Last 4) -join ' ; '
Log ("logcat: " + $log)
