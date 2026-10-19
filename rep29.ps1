Set-Location "F:/KotlinProject/CandyIDE"
$jars = Get-ChildItem -Recurse -Filter "*.jar" | Where-Object { $_.FullName -like "*sora*" }
foreach ($j in $jars) { Write-Host $j.FullName }