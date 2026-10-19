$adb = "F:/AndroidSDK/platform-tools/adb.exe"
$Q = [char]34; $LB = "[["; $RB = "[]]"; $NUM = "[0-9]+"
function Wait($ms) { Start-Sleep -Milliseconds $ms }
function Dump {
    &$adb shell rm /sdcard/d.xml 2>&1 | Out-Null
    &$adb shell uiautomator dump /sdcard/d.xml 2>&1 | Out-Null
    Wait 450
    &$adb pull /sdcard/d.xml "F:/KotlinProject/CandyIDE/d1.xml" 2>&1 | Out-Null
    Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw
}
function Show($tag, $raw) {
    Write-Host ("--- " + $tag + " ---")
    $ms = [regex]::Matches($raw, "(text|content-desc)=" + $Q + "([^" + $Q + "]{1,30})" + $Q)
    $seen = @{}
    foreach ($x in $ms) { $k = $x.Groups[2].Value; if (-not $seen.ContainsKey($k)) { $seen[$k] = 1; Write-Host ("  " + $k) } }
}
function FindXY($raw, $attr, $t) {
    $re = "<node[^>]*" + $attr + "=" + $Q + $t + $Q + "[^>]*bounds=" + $Q + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $Q
    $m = [regex]::Match($raw, $re)
    if ($m.Success) { return @((([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2), (([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)) }
    return $null
}
function TapN($raw, $attr, $t) {
    $p = FindXY $raw $attr $t
    if ($p) { Write-Host ("tap " + $t + " @ " + $p[0] + "," + $p[1]); &$adb shell input tap $p[0] $p[1] 2>&1 | Out-Null; Wait 800; return $true }
    Write-Host ("MISS " + $t)
    return $false
}
&$adb shell am force-stop candy.ide 2>&1 | Out-Null
&$adb shell am start -n candy.ide/.MainActivity 2>&1 | Out-Null
Wait 4000
$raw = Dump
Show "start" $raw
TapN $raw "text" "demo" | Out-Null
Wait 2200
$raw = Dump
Show "after-demo" $raw
TapN $raw "content-desc" "文件树" | Out-Null
$raw = Dump
Show "tree-open" $raw