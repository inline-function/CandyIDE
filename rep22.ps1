$adb = "F:/AndroidSDK/platform-tools/adb.exe"
function Wait($ms) { Start-Sleep -Milliseconds $ms }
&$adb shell ime set com.huawei.ohos.inputmethod/com.android.inputmethod.latin.LatinIME 2>&1
Write-Host "ime switched"
&$adb shell input tap 630 1200 2>&1 | Out-Null
Wait 800
&$adb shell input text "abc" 2>&1 | Out-Null
Wait 800
&$adb shell input text "xyz" 2>&1 | Out-Null
Wait 600
$raw = Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw
$f = (&$adb shell "run-as candy.ide cat files/candy/projects/demo/new.subl" 2>&1)
Write-Host "FILE-BEGIN"
foreach ($l in $f) { Write-Host ("|" + $l + "|") }
Write-Host "FILE-END"