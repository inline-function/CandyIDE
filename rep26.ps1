$Q = [char]34
$t = Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw
Write-Host ("len: " + $t.Length)
$ms = [regex]::Matches($t, "<node[^>]*class=" + $Q + "([^" + $Q + "]+)" + $Q + "[^>]*text=" + $Q + "([^" + $Q + "]*)" + $Q)
foreach ($x in $ms) { Write-Host ($x.Groups[1].Value + "  =>  " + $x.Groups[2].Value) }
Write-Host "--- nodes with pr ---"
$i = $t.IndexOf("pr")
Write-Host ("any pr: " + ($i -ge 0))