$adb = "F:/AndroidSDK/platform-tools/adb.exe"
function Wait($ms) { Start-Sleep -Milliseconds $ms }
function Dump {
    &$adb shell rm /sdcard/d.xml 2>&1 | Out-Null
    &$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
    Wait 450
    &$adb pull /sdcard/d.xml "F:/KotlinProject/CandyIDE/d1.xml" 2>&1 | Out-Null
    Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw
}
$raw = Dump
Write-Host ("drawer tabs present: " + $raw.Contains("日志"))
Write-Host "tap scrim x=60"
&$adb shell input tap 60 1200 2>&1 | Out-Null
Wait 1000
$raw = Dump
Write-Host ("after tap tabs: " + $raw.Contains("日志"))