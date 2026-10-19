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
$raw = Dump
$rows = [regex]::Matches($raw, "text=" + $Q + "(hello.subl:[0-9]+:[0-9]+)" + $Q + "[^>]*bounds=" + $Q + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $Q)
Write-Host ("rows found: " + $rows.Count)
if ($rows.Count -gt 0) {
    $r1 = [int]$rows[0].Groups[2].Value; $c1 = [int]$rows[0].Groups[3].Value
    $r2 = [int]$rows[0].Groups[4].Value; $c2 = [int]$rows[0].Groups[5].Value
    $cx = ($r1 + $r2) / 2; $cy = ($c1 + $c2) / 2
    Write-Host ("tap " + $rows[0].Groups[1].Value + " bbox [" + $r1 + "," + $c1 + "][" + $r2 + "," + $c2 + "] center " + $cx + "," + $cy)
    &$adb shell input tap $cx $cy 2>&1 | Out-Null
    Wait 1800
    $raw = Dump
    Write-Host ("drawer after: " + $raw.Contains("诊断"))
}