$adb = "F:/AndroidSDK/platform-tools/adb.exe"
function Wait($ms) { Start-Sleep -Milliseconds $ms }
&$adb shell input tap 630 1500 2>&1 | Out-Null
Wait 600
&$adb logcat -c 2>&1 | Out-Null
&$adb shell input text "pr" 2>&1 | Out-Null
Wait 1200
&$adb shell rm /sdcard/d.xml 2>&1 | Out-Null
&$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
Wait 400
&$adb pull /sdcard/d.xml "F:/KotlinProject/CandyIDE/d2.xml" 2>&1 | Out-Null
$t = Get-Content "F:/KotlinProject/CandyIDE/d2.xml" -Raw
Write-Host ("len: " + $t.Length)
Write-Host ("print: " + $t.Contains("print"))
$roots = [regex]::Matches($t, "<hierarchy")
Write-Host ("hierarchy tags: " + $roots.Count)
$logs = (&$adb logcat -d 2>&1) -join [char]10
foreach ($l in ($logs -split [char]10)) { if ($l -match "SUBL-COMP") { Write-Host $l } }