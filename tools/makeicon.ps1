$src = "C:/Users/ZhuanZ/Desktop/芙芙莎明/icon.png"
$res = "F:/KotlinProject/CandyIDE/app/src/main/res"
Add-Type -AssemblyName System.Drawing

$bmp = [System.Drawing.Bitmap]::FromFile($src)
$W = $bmp.Width; $H = $bmp.Height

function SaveDpi($bmp, $res, $folder, $name, $size) {
  $dir = Join-Path $res $folder
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
  $out = New-Object System.Drawing.Bitmap $size, $size
  $g = [System.Drawing.Graphics]::FromImage($out)
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
  $g.DrawImage($bmp, 0, 0, $size, $size)
  $out.Save((Join-Path $dir ($name + ".png")), [System.Drawing.Imaging.ImageFormat]::Png)
  $g.Dispose(); $out.Dispose()
}

function SaveRound($bmp, $res, $folder, $name, $size) {
  $dir = Join-Path $res $folder
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
  $out = New-Object System.Drawing.Bitmap $size, $size
  $g = [System.Drawing.Graphics]::FromImage($out)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.Clear([System.Drawing.Color]::Transparent)
  $path = New-Object System.Drawing.Drawing2D.GraphicsPath
  $path.AddEllipse(0, 0, $size, $size)
  $g.SetClip($path)
  $g.DrawImage($bmp, 0, 0, $size, $size)
  $out.Save((Join-Path $dir ($name + ".png")), [System.Drawing.Imaging.ImageFormat]::Png)
  $g.Dispose(); $out.Dispose()
}

# legacy（各密度的方形/圆形 PNG）
$dpiMap = @{ "mipmap-mdpi" = 48; "mipmap-hdpi" = 72; "mipmap-xhdpi" = 96; "mipmap-xxhdpi" = 144; "mipmap-xxxhdpi" = 192 }
foreach ($k in $dpiMap.Keys) {
  SaveDpi $bmp $res $k "ic_launcher" $dpiMap[$k]
  SaveDpi $bmp $res $k "ic_launcher_round" $dpiMap[$k]
}
# v26 自适应图标前景（108dp 全密度的 108/162/216/324/432）
$fgMap = @{ "mipmap-mdpi" = 108; "mipmap-hdpi" = 162; "mipmap-xhdpi" = 216; "mipmap-xxhdpi" = 324; "mipmap-xxxhdpi" = 432 }
foreach ($k in $fgMap.Keys) {
  SaveDpi $bmp $res $k "ic_launcher_foreground" $fgMap[$k]
}
$bmp.Dispose()

# 背景色：图中天蓝
$bgDir = Join-Path $res "drawable"
New-Item -ItemType Directory -Force -Path $bgDir | Out-Null
Set-Content -Path (Join-Path $bgDir "ic_launcher_background.xml") -Value '<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#5EC5EB"/>
</shape>' -Encoding UTF8

# v26 adaptive 描述
$aDir = Join-Path $res "mipmap-anydpi-v26"
New-Item -ItemType Directory -Force -Path $aDir | Out-Null
Set-Content -Path (Join-Path $aDir "ic_launcher.xml") -Value '<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>' -Encoding UTF8
Set-Content -Path (Join-Path $aDir "ic_launcher_round.xml") -Value '<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>' -Encoding UTF8

Get-ChildItem -Recurse (Join-Path $res "mipmap-*") -Filter '*.png' | ForEach-Object { Write-Host ($_.Directory.Name + "/" + $_.Name + " " + $_.Length) }
Get-ChildItem -Recurse (Join-Path $res "drawable") -Filter '*.xml' | ForEach-Object { Write-Host ($_.Directory.Name + "/" + $_.Name) }
Write-Host "done"
