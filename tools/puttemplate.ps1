New-Item -ItemType Directory -Force -Path 'F:/KotlinProject/CandyIDE/app/src/main/assets/sdk/jsapk' | Out-Null
Copy-Item 'F:/KotlinProject/CandyIDE/jsapk-template/build/outputs/apk/release/jsapk-template-release-unsigned.apk' 'F:/KotlinProject/CandyIDE/app/src/main/assets/sdk/jsapk/template.apk' -Force
$f = Get-Item 'F:/KotlinProject/CandyIDE/app/src/main/assets/sdk/jsapk/template.apk'
Write-Host ("template.apk: " + $f.Length + " bytes")