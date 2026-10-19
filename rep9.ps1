$t = Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw
$nodes = [regex]::Matches($t, "<node [^>]*>")
Write-Host ("nodes: " + $nodes.Count)
$Q = [char]34; $LB = "[[]"; $RB = "[]]"
foreach ($x in $nodes) {
    $v = $x.Value
    if ($v -match "bounds=" + $Q + $LB + "([0-9]+),([0-9]+)" + $RB + $LB + "([0-9]+),([0-9]+)" + $RB + $Q) {
        $x1 = [int]$Matches[1]; $y1 = [int]$Matches[2]; $x2 = [int]$Matches[3]; $y2 = [int]$Matches[4]
        $cls = ""; $cl = ""; $tx = ""; $ds = ""
        if ($v -match "class=" + $Q + "([^" + $Q + "]+)" + $Q) { $cls = $Matches[1].Substring($Matches[1].LastIndexOf(".") + 1) }
        if ($v -match "clickable=" + $Q + "([^" + $Q + "]+)" + $Q) { $cl = $Matches[1] }
        if ($v -match "text=" + $Q + "([^" + $Q + "]*)" + $Q) { $tx = $Matches[1] }
        if ($v -match "content-desc=" + $Q + "([^" + $Q + "]*)" + $Q) { $ds = $Matches[1] }
        Write-Host ($x1.ToString("0000") + "," + $y1.ToString("0000") + " " + ($x2 - $x1).ToString("0000") + "x" + ($y2 - $y1).ToString("0000") + " clk=" + $cl.PadRight(5) + " " + $cls.PadRight(18) + " t=" + $tx + " d=" + $ds)
    }
}