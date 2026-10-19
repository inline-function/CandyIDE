$Q = [char]34
$t = Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw
Write-Host ("len: " + $t.Length)
$i = $t.IndexOf('text="demo"')
Write-Host ("idx: " + $i)
if ($i -ge 0) { Write-Host $t.Substring([Math]::Max(0, $i - 140), 280) }