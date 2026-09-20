# TOD Turbo derleme betigi (Windows / PowerShell)
# Android Studio projesi degil, bilerek: tek Java dosyasi icin Gradle kurmaya gerek yok.
# Gereken: Android SDK build-tools + platforms/android-34 + bir JDK 17 (Android Studio'nunki olur).

$ErrorActionPreference = "Stop"
$w   = $PSScriptRoot
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$bt  = "$sdk\build-tools\35.0.1"
$jar = "$sdk\platforms\android-34\android.jar"
$jbr = "C:\Program Files\Android\Android Studio1\jbr\bin"

New-Item -ItemType Directory -Force "$w\out\res","$w\out\classes","$w\out\dex" | Out-Null

# 1) Gorselleri (banner/icon) derle -> res.zip
& "$bt\aapt2.exe" compile --dir "$w\res" -o "$w\out\res\res.zip"
if ($LASTEXITCODE) { throw "aapt2 compile patladi" }

# 2) Manifest + gorselleri paketle -> base.apk (henuz kod yok)
& "$bt\aapt2.exe" link -o "$w\out\base.apk" --manifest "$w\AndroidManifest.xml" -I $jar "$w\out\res\res.zip"
if ($LASTEXITCODE) { throw "aapt2 link patladi" }

# 3) Java -> .class
& "$jbr\javac.exe" -source 8 -target 8 -nowarn -Xlint:-options -bootclasspath $jar -classpath $jar `
    -d "$w\out\classes" "$w\src\com\yigit\todturbo\TurboActivity.java"
if ($LASTEXITCODE) { throw "javac patladi" }

# 4) .class -> classes.dex (Android kendi bytecode formatini kullanir)
# NOT: d8.bat kullanma. PowerShell'den cagrilinca dosya yolunu "C:" den bolup patliyor.
# Onun yerine d8.jar'i dogrudan java ile calistiriyoruz.
$cls = Get-ChildItem "$w\out\classes" -Recurse -Filter *.class | Select-Object -ExpandProperty FullName
& "$jbr\java.exe" -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --min-api 24 --lib $jar --output "$w\out\dex" $cls
if ($LASTEXITCODE) { throw "d8 patladi" }

# 5) classes.dex'i apk'nin (zip) icine koy
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [IO.Compression.ZipFile]::Open("$w\out\base.apk", 'Update')
[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($z, "$w\out\dex\classes.dex", "classes.dex") | Out-Null
$z.Dispose()

# 6) Hizala (Android dosyalari 4 byte hizali ister)
& "$bt\zipalign.exe" -f -p 4 "$w\out\base.apk" "$w\out\aligned.apk"
if ($LASTEXITCODE) { throw "zipalign patladi" }

# 7) Imzala. Imzasiz APK kurulmaz. debug.keystore herkeste ayni, sifresi "android".
& "$jbr\java.exe" -jar "$bt\lib\apksigner.jar" sign `
    --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android `
    --ks-key-alias androiddebugkey --out "$w\out\tod-turbo.apk" "$w\out\aligned.apk"
if ($LASTEXITCODE) { throw "imzalama patladi" }

& "$jbr\java.exe" -jar "$bt\lib\apksigner.jar" verify "$w\out\tod-turbo.apk"
Write-Host "BITTI -> $w\out\tod-turbo.apk"

# Kurmak icin:
#   adb connect <TV_IP>:5555
#   adb install -r out\tod-turbo.apk
