$ks = "F:/KotlinProject/CandyIDE/tools/candyjs.p12"
$kt = "C:/Users/ZhuanZ/AppData/Local/Programs/Microsoft/jdk-17.0.13.11-hotspot/bin/keytool.exe"
& $kt -genkeypair -alias candyjsapk -keyalg RSA -keysize 2048 -validity 10950 -keystore $ks -storepass candyjs -dname "CN=CandyIDE JS-apk Runtime" -storetype PKCS12 2>&1 | Out-Null
if (-not (Test-Path $ks)) { Write-Host "keystore missing"; exit 1 }
$cert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($ks, "candyjs")
$dir = "F:/KotlinProject/CandyIDE/app/src/main/assets/sdk/jsapk"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
[System.IO.File]::WriteAllBytes("$dir/candy_jsapk.cert.der", $cert.Export([System.Security.Cryptography.X509Certificates.X509ContentType]::Cert))
$rsa = [System.Security.Cryptography.X509Certificates.RSACertificateExtensions]::GetRSAPrivateKey($cert)
[System.IO.File]::WriteAllBytes("$dir/candy_jsapk.key.pk8", $rsa.ExportPkcs8PrivateKey())
Write-Host ("cert: " + (Get-Item "$dir/candy_jsapk.cert.der").Length + " key: " + (Get-Item "$dir/candy_jsapk.key.pk8").Length)