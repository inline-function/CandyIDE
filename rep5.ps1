$adb = "F:/AndroidSDK/platform-tools/adb.exe"
$Q = [char]34
&$adb shell rm /sdcard/d.xml 2>&1 | Out-Null
&$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
Start-Sleep -Milliseconds 500
&$adb pull /sdcard/d.xml "F:/KotlinProject/CandyIDE/d1.xml" 2>&1 | Out-Null
$t = Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw
$ms = [regex]::Matches($t, "(text|content-desc)=" + $Q + "[^" + $Q + "]{1,30}" + $Q)
foreach ($x in $ms) { Write-Host $x.Value }
Write-Host ("sora: " + $t.Contains("CodeEditor"))