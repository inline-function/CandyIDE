$ErrorActionPreference = 'Stop'
$tools = "F:/KotlinProject/CandyIDE/tools"
New-Item -ItemType Directory -Force -Path $tools | Out-Null
$t = "$tools/jbm.jar"
if (-not (Test-Path $t)) {
  curl.exe -s -S -o $t "https://repo1.maven.org/maven2/com/formdev/flatlaf-fonts-jetbrains-mono/2.304/flatlaf-fonts-jetbrains-mono-2.304.jar"
}
Write-Host ("jar: " + (Get-Item $t).Length)
$ex = "$tools/jbmex"
Remove-Item -Recurse -Force $ex -ErrorAction SilentlyContinue
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::ExtractToDirectory($t, $ex)
$dest = "F:/KotlinProject/CandyIDE/app/src/main/res/font"
New-Item -ItemType Directory -Force -Path $dest | Out-Null
$srcDir = Join-Path $ex "com/formdev/flatlaf/fonts/jetbrains_mono"
$names = @("JetBrainsMono-Regular.ttf", "JetBrainsMono-Bold.ttf", "JetBrainsMono-Italic.ttf", "JetBrainsMono-BoldItalic.ttf")
$out = @("jetbrainsmono_regular.ttf", "jetbrainsmono_bold.ttf", "jetbrainsmono_italic.ttf", "jetbrainsmono_bolditalic.ttf")
for ($i = 0; $i -lt $names.Length; $i++) {
  Copy-Item (Join-Path $srcDir $names[$i]) (Join-Path $dest $out[$i]) -Force
}
Write-Host "OFL:"
Copy-Item (Join-Path $srcDir "OFL.txt") "$dest/../OFL-jetbrains.txt" -Force
Get-ChildItem $dest | ForEach-Object { Write-Host ($_.Name + " " + $_.Length) }
