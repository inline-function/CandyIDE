$adb = "F:/AndroidSDK/platform-tools/adb.exe"
&$adb push "F:/KotlinProject/CandyIDE/hello.subl" /sdcard/hello.subl 2>&1 | Out-Null
&$adb shell "run-as candy.ide cp /sdcard/hello.subl files/candy/projects/demo/hello.subl" 2>&1
$f = (&$adb shell "run-as candy.ide cat files/candy/projects/demo/hello.subl" 2>&1) -join [char]10
Write-Host ("hello lines: " + ($f -split [char]10).Count)