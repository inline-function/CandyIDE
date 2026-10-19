$ErrorActionPreference = 'Continue'
$adb = "F:/AndroidSDK/platform-tools/adb.exe"
$xmlPath = "F:/KotlinProject/CandyIDE/d.xml"
$flagFile = "F:/KotlinProject/CandyIDE/v04-result.txt"
"=== CandyIDE v0.4 verify " + (Get-Date) + " ===" | Out-File $flagFile

function Dump {
    &$adb shell uiautomator dump /sdcard/d.xml | Out-Null
    Start-Sleep -Milliseconds 400
    &$adb pull /sdcard/d.xml $xmlPath | Out-Null
    [xml](Get-Content $xmlPath -Raw)
}
function Wait-Ms($ms) { Start-Sleep -Milliseconds $ms }
function Click-Text($d, $t) {
    $n = $d.SelectNodes("//*[@text='$t']")
    foreach ($x in $n) {
        if ($x.bounds -match '[(d+),(d+)][(d+),(d+)]') {
            $cx = ([int]$Matches[1] + [int]$Matches[3]) / 2; $cy = ([int]$Matches[2] + [int]$Matches[4]) / 2
            &$adb shell input tap $cx $cy | Out-Null
            Write-Host ("  tapped text '" + $t + "' at " + $cx + "," + $cy)
            return $true
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
            Write-Host ("  tapped desc '" + $t + "' at " + $cx + "," + $cy)
            return $true
        }
    }
    Write-Host ("  MISS desc '" + $t + "'"); return $false
}
function Has-Text($d, $t) { $d.InnerText.Contains($t) }

&$adb shell svc power stayon true | Out-Null
&$adb shell input keyevent 224 | Out-Null
Wait-Ms 500
&$adb shell am force-stop candy.ide | Out-Null
&$adb shell am start -n candy.ide/.MainActivity | Out-Null
Wait-Ms 2500
$doc = Dump
$s1 = (Has-Text $doc "demo") -and (Has-Text $doc "JS 平台工程")
"1) project list demo (JS): $s1" | Out-File $flagFile -Append

Click-Text $doc "demo" | Out-Null
Wait-Ms 1800
$doc = Dump
$s2 = Has-Text $doc "hello.subl"
"2) editor tab hello.subl: $s2" | Out-File $flagFile -Append

Click-Desc $doc "运行" | Out-Null
Wait-Ms 3500
$doc = Dump
Click-Desc $doc "输出/日志/诊断" | Out-Null
Wait-Ms 800
$doc = Dump
$s3 = Has-Text $doc "Hello, Sublimation!"
$s4 = Has-Text $doc "42"
$log = (&$adb logcat -d -s CandyEngine | Select-String "Hello|42|CandyIDE" | Select-Object -First 3) -join ' ; '
"3) run output: Hello=$s3 42=$s4 | logcat: $log" | Out-File $flagFile -Append

&$adb shell input keyevent 4 | Out-Null
Wait-Ms 1000
$doc = Dump
Click-Desc $doc "设置" | Out-Null
Wait-Ms 1000
$doc = Dump
$s5 = (Has-Text $doc "Shizuku") -and (Has-Text $doc "配色方案") -and (Has-Text $doc "自动换行") -and (Has-Text $doc "Darcula Contrast")
"5) settings page: $s5" | Out-File $flagFile -Append
Write-Host "---- RESULT FILE ----"
Get-Content $flagFile
