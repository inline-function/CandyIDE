$adb = "F:/AndroidSDK/platform-tools/adb.exe"
function Wait($ms) { Start-Sleep -Milliseconds $ms }
&$adb shell input tap 630 1500 2>&1 | Out-Null
Wait 600
&$adb shell input text "pr" 2>&1 | Out-Null
Wait 700
$w = (&$adb shell "dumpsys window windows" 2>&1) -join [char]10
foreach ($l in ($w -split [char]10)) { if ($l -match "candy|.popup|Popup|token") { Write-Host $l.Trim() } }