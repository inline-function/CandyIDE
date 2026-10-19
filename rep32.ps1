$Q = [char]34; $LB = "[[]"; $RB = "[]]"; $NUM = "[0-9]+"
$t = Get-Content "F:/KotlinProject/CandyIDE/d2.xml" -Raw
Write-Host "--- all text nodes in order ---"
$re = "<node[^>]*text=" + $Q + "([^" + $Q + "]*)" + $Q + "[^>]*bounds=" + $Q + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB
$ms = [regex]::Matches($t, $re)
foreach ($x in $ms) { Write-Host ("  [" + $x.Groups[1].Value + "] @ " + $x.Groups[2].Value + "," + $x.Groups[3].Value) }