$adb = "F:/AndroidSDK/platform-tools/adb.exe"
$Q = [char]34
function Wait($ms) { Start-Sleep -Milliseconds $ms }
function Dump {
    &$adb shell rm /sdcard/d.xml 2>&1 | Out-Null
    &$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
    Wait 450
    &$adb pull /sdcard/d.xml "F:/KotlinProject/CandyIDE/d1.xml" 2>&1 | Out-Null
    Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw
}
&$adb shell am force-stop candy.ide 2>&1 | Out-Null
&$adb shell am start -n candy.ide/.MainActivity 2>&1 | Out-Null
Wait 4000
$raw = Dump
Write-Host "SCREEN1:"
$ms = [regex]::Matches($raw, "text=" + $Q + "([^" + $Q + "]{1,40})" + $Q)
foreach ($x in $ms) { Write-Host ("  " + $x.Groups[1].Value) }
$ms2 = [regex]::Matches($raw, "content-desc=" + $Q + "([^" + $Q + "]{1,40})" + $Q)
Write-Host "DESCS1:"
foreach ($x in $ms2) { Write-Host ("  " + $x.Groups[1].Value) }