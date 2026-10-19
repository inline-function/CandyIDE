$adb = "F:/AndroidSDK/platform-tools/adb.exe"
function Wait($ms) { Start-Sleep -Milliseconds $ms }
&$adb shell input tap 630 1500 2>&1 | Out-Null
Wait 600
&$adb shell input text "pr" 2>&1 | Out-Null
Wait 700
&$adb shell rm /sdcard/d.xml 2>&1 | Out-Null
&$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
Wait 400
&$adb pull /sdcard/d.xml "F:/KotlinProject/CandyIDE/d4.xml" 2>&1 | Out-Null
$t = Get-Content "F:/KotlinProject/CandyIDE/d4.xml" -Raw
$Q = [char]34
$ms = [regex]::Matches($t, "text=" + $Q + "([^" + $Q + "]{1,60})" + $Q)
Write-Host "TEXTS:"
$seen = @{}
foreach ($x in $ms) { $k = $x.Groups[1].Value; if (-not $seen.ContainsKey($k)) { $seen[$k] = 1; Write-Host ("  " + $k) } }
$clss = [regex]::Matches($t, "class=" + $Q + "([^" + $Q + "]+)" + $Q)
$seen2 = @{}
Write-Host "CLASSES:"
foreach ($x in $clss) { $k = $x.Groups[1].Value; if (-not $seen2.ContainsKey($k)) { $seen2[$k] = 1; Write-Host ("  " + $k) } }