$adb = "F:/AndroidSDK/platform-tools/adb.exe"
$f = (&$adb shell "run-as candy.ide cat files/candy/projects/demo/hello.subl" 2>&1)
foreach ($l in $f) { Write-Host ("|" + $l + "|") }