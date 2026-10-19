$t = Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw
$nodes = [regex]::Matches($t, "<node [^>]*/>|<node [^>]*>")
Write-Host ("nodes: " + $nodes.Count)
$Q = [char]34; $LB = "[[]"; $RB = "[]]"
foreach ($x in $nodes) {
    $v = $x.Value
    if ($v -match "bounds=" + $Q + $LB + "([0-9]+),([0-9]+)" + $RB + $LB + "([0-9]+),([0-9]+)" + $RB + $Q) {
        $x1 = [int]$Matches[1]; $y1 = [int]$Matches[2]; $x2 = [int]$Matches[3]; $y2 = [int]$Matches[4]
        $w = $x2 - $x1; $h = $y2 - $y1
        $cls = ""
        if ($v -match "class=" + $Q + "([^" + $Q + "]+)" + $Q) { $cls = $Matches[1] }
        $cl = ""
        if ($v -match "clickable=" + $Q + "([^" + $Q + "]+)" + $Q) { $cl = $Matches[1] }
        if ($w -gt 800 -or $h -gt 600) { Write-Host ($x1.ToString("0000") + "," + $y1.ToString("0000") + " " + $w.ToString("0000") + "x" + $h.ToString("0000") + " clk=" + $cl + " " + $cls) }
    }
}