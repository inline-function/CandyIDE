$adb = "F:/AndroidSDK/platform-tools/adb.exe"
&$adb shell "run-as candy.ide sh -c 'echo Ly8gU3VibGltYXRpb24g57yW6K+R56S65L6LIOKAlOKAlCDngrnlj7PkuIrop5LjgIzov5DooYzjgI3nvJbor5HmiJAgSlMg5bm25omn6KGMCkBpbXB1cmUgZnVuIG1haW4oKSB7CiAgdmFyIHMgPSAiSGVsbG8sIFN1YmxpbWF0aW9uISIKICBwcmludChzKQogIHZhciBhZGQgPSB7IHggPT4geCArIDEgfQogIHByaW50KGFkZCg0MSkpCiAgdmFyIHAgPSAoMywgNCkKICB2YXIgKGEsIGIpID0gcAogIHByaW50KGEgKyBiKQp9Cg== | base64 -d > files/candy/projects/demo/hello.subl'" 2>&1
&$adb shell "run-as candy.ide sh -c 'echo -n > files/candy/projects/demo/new.subl'" 2>&1
$f = (&$adb shell "run-as candy.ide cat files/candy/projects/demo/hello.subl" 2>&1)
foreach ($l in $f) { Write-Host ("|" + $l + "|") }