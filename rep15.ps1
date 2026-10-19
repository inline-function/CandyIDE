$Q = [char]34
$t = Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw
Write-Host ("len: " + $t.Length)
$ms = [regex]::Matches($t, "text=" + $Q + "([^" + $Q + "]{1,60})" + $Q)
foreach ($x in $ms) { Write-Host $x.Groups[1].Value }