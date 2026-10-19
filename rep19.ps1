$Q = [char]34; $LB = "[["; $RB = "[]]"; $NUM = "[0-9]+"
$t = Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw
$attr = "text"; $tt = "demo"
$re = "<node[^>]*" + $attr + "=" + $Q + $tt + $Q + "[^>]*bounds=" + $Q + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $LB + "(" + $NUM + "),(" + $NUM + ")" + $RB + $Q
Write-Host ("re: " + $re)
$m = [regex]::Match($t, $re)
Write-Host ("match: " + $m.Success)
Write-Host ("groups: " + $m.Groups.Count)