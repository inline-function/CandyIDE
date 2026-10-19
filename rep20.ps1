$Q = [char]34
$t = Get-Content "F:/KotlinProject/CandyIDE/d1.xml" -Raw
function T($pat, $name) { $m = [regex]::Match($t, $pat); Write-Host ($name + ": " + $m.Success) }
T 'demo' 'plain-demo'
T ('<node[^>]*text=' + $Q + 'demo' + $Q) 'node-text'
T ('text=' + $Q + 'demo' + $Q + '[^>]*bounds') 'text-to-bounds'
T ('<node[^>]*bounds=') 'node-bounds'
T ('bounds=' + $Q + '[[') 'bounds-open-bracket'
T ('[^.>=]*') 'weird-negclass'