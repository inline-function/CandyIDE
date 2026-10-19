$adb = "F:/AndroidSDK/platform-tools/adb.exe"
$Q = [char]34; $LB = "[[]"; $RB = "[]]"; $NUM = "[0-9]+"
function Wait($ms) { Start-Sleep -Milliseconds $ms }
function Dump {
    $ok = $false
    for ($i = 0; $i -lt 4 -and -not $ok; $i++) {
        &$adb shell rm /sdcard/d.xml 2>&1 | Out-Null
        &$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
        Wait 500
        $p = (&$adb pull /sdcard/d.xml "F:/KotlinProject/CandyIDE/d1.xml" 2>&1) -join ""
        if ($p.Contains("1 file pulled")) { $t = Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw; if ($t.Length -gt 500) { $ok = $true } }
        if (-not $ok) { Wait 700 }
    }
    return Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw
}
function FindXY($raw, $attr, $t) {
    $re = "<node[^>]*" + $attr + "=" + $Q + $t + $Q + "[^>]*bounds=" + $Q + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $Q
    $m = [regex]::Match($raw, $re)
    if ($m.Success) { return @((([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2), (([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)) }
    return $null
}
function TapN($raw, $attr, $t) {
    $p = FindXY $raw $attr $t
    if ($p) { Write-Host ("tap " + $t + " @ " + $p[0] + "," + $p[1]); &$adb shell input tap $p[0] $p[1] 2>&1 | Out-Null; Wait 900; return $true }
    Write-Host ("MISS " + $t)
    return $false
}
&$adb shell am force-stop candy.ide 2>&1 | Out-Null
&$adb shell am start -n candy.ide/.MainActivity 2>&1 | Out-Null
Wait 4000
$raw = Dump
TapN $raw "text" "demo" | Out-Null
Wait 2200
$raw = Dump
TapN $raw "content-desc" "文件树" | Out-Null
$raw = Dump
TapN $raw "text" "new.subl" | Out-Null
Wait 1200
$raw = Dump
Write-Host ("tab new.subl now: " + ($raw -split "new.subl").Length)
&$adb shell input tap 630 1200 2>&1 | Out-Null
Wait 900
&$adb shell input text "fun%sf%s()" 2>&1 | Out-Null
Wait 500
&$adb shell input text "%s{" 2>&1 | Out-Null
Wait 500
&$adb shell input keyevent 66 2>&1 | Out-Null
Wait 1000
&$adb shell input text "println(1)" 2>&1 | Out-Null
Wait 500
&$adb shell input keyevent 66 2>&1 | Out-Null
Wait 1000
&$adb shell input text "}" 2>&1 | Out-Null
Wait 500
$raw = Dump
TapN $raw "content-desc" "保存" | Out-Null
Wait 1400
$f = (&$adb shell "run-as candy.ide cat files/candy/projects/demo/new.subl" 2>&1)
Write-Host "FILE-BEGIN"
foreach ($l in $f) { Write-Host ("|" + $l + "|") }
Write-Host "FILE-END"