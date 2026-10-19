$adb = "F:/AndroidSDK/platform-tools/adb.exe"
function Wait($ms) { Start-Sleep -Milliseconds $ms }
$Q = [char]34
&$adb shell input tap 630 1500 2>&1 | Out-Null
Wait 500
&$adb shell input text "pr" 2>&1 | Out-Null
Wait 600
&$adb shell rm /sdcard/d.xml 2>&1 | Out-Null
&$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
Wait 300
&$adb pull /sdcard/d.xml "F:/KotlinProject/CandyIDE/d3.xml" 2>&1 | Out-Null
$t = Get-Content "F:/KotlinProject/CandyIDE/d3.xml" -Raw
Write-Host ("print: " + $t.Contains("print"))
Write-Host ("pr: " + ($t.IndexOf("pr") -ge 0))
$i = $t.IndexOf("print")
if ($i -ge 0) { Write-Host $t.Substring([Math]::Max(0, $i - 300), 380) }