$adb = "F:/AndroidSDK/platform-tools/adb.exe"
function Wait($ms) { Start-Sleep -Milliseconds $ms }
&$adb shell input tap 630 1500 2>&1 | Out-Null
Wait 500
&$adb shell input text "pr" 2>&1 | Out-Null
Wait 250
&$adb exec-out screencap -p > "F:/KotlinProject/CandyIDE/shot1.png" 2>&1
Wait 400
&$adb exec-out screencap -p > "F:/KotlinProject/CandyIDE/shot2.png" 2>&1
Wait 500
&$adb exec-out screencap -p > "F:/KotlinProject/CandyIDE/shot3.png" 2>&1
foreach ($n in 1..3) { $f = "F:/KotlinProject/CandyIDE/shot" + $n + ".png"; if (Test-Path $f) { Write-Host ($f + " " + (Get-Item $f).Length) } }