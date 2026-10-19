$t = Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw
Write-Host ("dump length: " + $t.Length)
$ms = [regex]::Matches($t, "class=.([^""<>]+)")
Write-Host ("class count: " + $ms.Count)
$seen = @{}
foreach ($x in $ms) { $k = $x.Groups[1].Value; if (-not $seen.ContainsKey($k)) { $seen[$k] = 1; Write-Host $k } }
$pr = $t.IndexOf("text=" + [char]34 + "pr")
Write-Host ("text pr idx: " + $pr)
if ($pr -ge 0) { Write-Host $t.Substring([Math]::Max(0, $pr - 150), 220) }