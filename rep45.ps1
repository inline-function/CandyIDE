$adb = "F:/AndroidSDK/platform-tools/adb.exe"
function Wait($ms) { Start-Sleep -Milliseconds $ms }
function Dump {
    $ok = $false
    for ($i = 0; $i -lt 4 -and -not $ok; $i++) {
        &$adb shell rm /sdcard/d.xml 2>&1 | Out-Null
        &$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
        Wait 450
        $p = (&$adb pull /sdcard/d.xml "F:/KotlinProject/CandyIDE/d1.xml" 2>&1) -join ""
        if ($p.Contains('1 file pulled')) { $t = Get-Content 'F:/KotlinProject/CandyIDE/d1.xml' -Raw; if ($t.Length -gt 500) { $ok = $true } }
        if (-not $ok) { Wait 700 }
    }
    return Get-Content 'F:/KotlinProject/CandyIDE/d1.xml' -Raw
}
$Q = [char]34; $LB = "[[]"; $RB = "[]]"; $NUM = "[0-9]+"
function FindXY($raw, $attr, $t) {
    $re = "<node[^>]*" + $attr + "=" + $Q + $t + $Q + "[^>]*bounds=" + $Q + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $Q
    $m = [regex]::Match($raw, $re)
    if ($m.Success) { return @((([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2), (([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)) }
    return $null
}
&$adb shell "run-as candy.ide sh -c 'echo Ly8gU3VibGltYXRpb24g57yW6K+R56S65L6LIOKAlOKAlCDngrnlj7PkuIrop5LjgIzov5DooYzjgI3nvJbor5HmiJAgSlMg5bm25omn6KGMCkBpbXB1cmUgZnVuIG1haW4oKSB7CiAgdmFyIHMgPSAiSGVsbG8sIFN1YmxpbWF0aW9uISIKICBwcmludChzKQogIHZhciBhZGQgPSB7IHggPT4geCArIDEgfQogIHByaW50KGFkZCg0MSkpCiAgdmFyIHAgPSAoMywgNCkKICB2YXIgKGEsIGIpID0gcAogIHByaW50KGEgKyBiKQp9Cg== | base64 -d > files/candy/projects/demo/hello.subl'" 2>&1
&$adb shell am force-stop candy.ide 2>&1 | Out-Null
&$adb logcat -c 2>&1 | Out-Null
&$adb shell am start -n candy.ide/.MainActivity 2>&1 | Out-Null
Wait 4000
$raw = Dump
$p = FindXY $raw "text" "demo"; if ($p) { &$adb shell input tap $p[0] $p[1] 2>&1 | Out-Null }
Wait 2500
# 长按 x：行 4 (y≈730)，col 19 → x 在 add 之后
&$adb shell input swipe 500 730 500 730 900 2>&1 | Out-Null
Wait 900
$raw = Dump
if ($raw.Contains("重命名")) {
  Write-Host "menu ok"
  $p = FindXY $raw "text" "重命名"
  if ($p) { &$adb shell input tap $p[0] $p[1] 2>&1 | Out-Null }
  Wait 1000
  $raw = Dump
  Write-Host ("dialog: " + $raw.Contains("替换"))
  if ($raw.Contains("替换")) {
    &$adb shell input keyevent 67 2>&1 | Out-Null
    Wait 200
    &$adb shell input text "xx" 2>&1 | Out-Null
    Wait 500
    $raw = Dump
    $p = FindXY $raw "text" "替换"
    if ($p) { &$adb shell input tap $p[0] $p[1] 2>&1 | Out-Null; Write-Host "tapped 替换" }
    Wait 1200
    $log = (&$adb logcat -d 2>&1) -join [char]10
    foreach ($l in ($log -split [char]10)) { if ($l -match "SUBL-REN") { Write-Host $l } }
  }
} else { Write-Host "no menu" }