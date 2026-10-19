$adb = "F:/AndroidSDK/platform-tools/adb.exe"
$f = (&$adb shell "run-as candy.ide cat files/candy/projects/demo/hello.subl" 2>&1)
foreach ($l in $f) { Write-Host ("|" + $l + "|") }
$j = (&$adb shell "run-as candy.ide cat files/candy/projects/demo/new.subl" 2>&1)
Write-Host "new.subl:"
foreach ($l in $j) { Write-Host ("|" + $l + "|") }