$ErrorActionPreference = "Continue"
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
function TapXY($px, $py) { &$adb shell input tap $px $py 2>&1 | Out-Null }
function ClickA($raw, $attr, $t) {
    $re = "<node[^>]*" + $attr + "=" + $Q + $t + $Q + "[^>]*bounds=" + $Q + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $Q
    $m = [regex]::Match($raw, $re)
    if (-not $m.Success) { Write-Host ("MISS " + $t); return $false }
    $cx = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
    $cy = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
    Write-Host ("tap " + $t + " @ " + $cx + "," + $cy)
    TapXY $cx $cy
    Wait 800
    return $true
}
&$adb shell am force-stop candy.ide 2>&1 | Out-Null
&$adb logcat -c 2>&1 | Out-Null
&$adb shell am start -n candy.ide/.MainActivity 2>&1 | Out-Null
Wait 4000
$raw = Dump
Write-Host ("projects: " + $raw.Contains("demo"))
ClickA $raw "text" "demo" | Out-Null
Wait 2000
$raw = Dump
Write-Host ("editor: " + $raw.Contains("hello.subl"))
ClickA $raw "content-desc" "文件树" | Out-Null
$raw = Dump
Write-Host ("drawer: " + $raw.Contains("新建文件"))
# 直接点 tree 里的 hello.subl 行（当前已打开 hello.subl，切换到别的需要先有别的文件）
$p = $null
Write-Host "---- 点击 tree hello.subl ----"
ClickA $raw "text" "hello.subl" | Out-Null
Wait 2000
$alog = (&$adb logcat -d 2>&1) -join [char]10
$fi = $alog.IndexOf("FATAL EXCEPTION")
Write-Host ("FATAL idx: " + $fi)
if ($fi -ge 0) { Write-Host $alog.Substring($fi, [Math]::Min(2500, $alog.Length - $fi)) } else { Write-Host "no fatal" }