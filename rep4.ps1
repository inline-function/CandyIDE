$adb = "F:/AndroidSDK/platform-tools/adb.exe"
$Q = [char]34; $LB = "[[]"; $RB = "[]]"; $NUM = "[0-9]+"
function Wait($ms) { Start-Sleep -Milliseconds $ms }
function Dump {
    &$adb shell rm /sdcard/d.xml 2>&1 | Out-Null
    &$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
    Wait 500
    &$adb pull /sdcard/d.xml "F:/KotlinProject/CandyIDE/d1.xml" 2>&1 | Out-Null
    Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw
}
$raw = Dump
Write-Host ("current screen has new.subl tab: " + $raw.Contains("new.subl"))
Write-Host ("current screen has hello.subl tab: " + $raw.Contains("hello.subl"))
# 点编辑区中部
&$adb shell input tap 630 1200 2>&1 | Out-Null
Wait 800
$im = (&$adb shell ime list -s 2>&1) -join ";"
Write-Host ("imes: " + $im)
$cur = (&$adb shell settings get secure default_input_method 2>&1) -join ";"
Write-Host ("cur ime: " + $cur)
&$adb shell input text "fun%sf%s()" 2>&1 | Out-Null
Wait 600
$raw = Dump
Write-Host ("after type, editor focused? dump has editor node: " + ($raw.Contains("io.github.rosemoe.sora.widget.CodeEditor")))
# 再保存看文件
$re = "<node[^>]*content-desc=" + $Q + "保存" + $Q + "[^>]*bounds=" + $Q + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $Q
$m = [regex]::Match($raw, $re)
if ($m.Success) { $cx = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2; $cy = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2; &$adb shell input tap $cx $cy 2>&1 | Out-Null; Wait 1000 } else { Write-Host "save miss" }
$f = (&$adb shell "run-as candy.ide cat files/candy/projects/demo/new.subl" 2>&1) -join [char]10
Write-Host ("FILE-CONTENT-BEGIN")
Write-Host $f
Write-Host ("FILE-CONTENT-END")