param([switch]$TestOnly)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$jdk = Get-ChildItem -LiteralPath "$projectRoot\.tools\jdk" -Directory | Select-Object -First 1
if (-not $jdk) { throw 'JDK missing. Run setup-tools.ps1 first.' }
$java = Join-Path $jdk.FullName 'bin\java.exe'
$javac = Join-Path $jdk.FullName 'bin\javac.exe'
$jar = Join-Path $jdk.FullName 'bin\jar.exe'
$keytool = Join-Path $jdk.FullName 'bin\keytool.exe'
$buildTools = "$projectRoot\.tools\sdk\build-tools\android-15"
$androidJar = '.tools\sdk\platforms\android-35\android.jar'
[xml]$manifest = Get-Content -LiteralPath 'app\src\main\AndroidManifest.xml' -Raw
$version = $manifest.manifest.versionName
$apkPath = "deliverables\JianJi-$version.apk"
function Run-Checked([string]$exe, [string[]]$arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed: $exe ($LASTEXITCODE)" }
}
New-Item -ItemType Directory -Force 'build\tests','build\classes','build\generated','build\dex','deliverables' | Out-Null
Run-Checked $javac @('--release','8','-encoding','UTF-8','-d','build\tests','app\src\main\java\cn\jianji\app\Ledger.java','tests\LedgerTest.java')
Run-Checked $java @('-cp','build\tests','cn.jianji.app.LedgerTest')
if ($TestOnly) { exit 0 }
Run-Checked "$buildTools\aapt2.exe" @('compile','--dir','app\src\main\res','-o','build\resources.zip')
Run-Checked "$buildTools\aapt2.exe" @('link','-o','build\resources.apk','-I',$androidJar,'--manifest','app\src\main\AndroidManifest.xml','--java','build\generated','--min-sdk-version','26','--target-sdk-version','35','build\resources.zip')
$sources = @(Get-ChildItem -LiteralPath 'app\src\main\java','build\generated' -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
Run-Checked $javac (@('--release','8','-encoding','UTF-8','-classpath',$androidJar,'-d','build\classes') + $sources)
Run-Checked $jar @('cf','build\classes.jar','-C','build\classes','.')
Run-Checked $java @('-cp',"$buildTools\lib\d8.jar",'com.android.tools.r8.D8','--release','--min-api','26','--lib',$androidJar,'--output','build\dex','build\classes.jar')
Copy-Item -LiteralPath 'build\resources.apk' -Destination 'build\unsigned.apk' -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::Open("$projectRoot\build\unsigned.apk", [IO.Compression.ZipArchiveMode]::Update)
try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,"$projectRoot\build\dex\classes.dex",'classes.dex',[IO.Compression.CompressionLevel]::Optimal) | Out-Null }
finally { $archive.Dispose() }
Run-Checked "$buildTools\zipalign.exe" @('-f','-p','4','build\unsigned.apk','build\aligned.apk')
New-Item -ItemType Directory -Force 'signing' | Out-Null
if (-not (Test-Path -LiteralPath 'signing\release.json')) {
    $secret = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    @{ password=$secret; alias='jianji'; store='jianji-release.p12' } | ConvertTo-Json | Set-Content -LiteralPath 'signing\release.json' -Encoding UTF8
}
$signing = Get-Content -LiteralPath 'signing\release.json' -Raw | ConvertFrom-Json
$env:JIANJI_STORE_PASSWORD = $signing.password
$store = "signing\$($signing.store)"
if (-not (Test-Path -LiteralPath $store)) { Run-Checked $keytool @('-genkeypair','-keystore',$store,'-storetype','PKCS12','-storepass:env','JIANJI_STORE_PASSWORD','-alias',$signing.alias,'-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Jianji, OU=Personal, O=Jianji, C=CN') }
try {
    Run-Checked $java @('-jar',"$buildTools\lib\apksigner.jar",'sign','--ks',$store,'--ks-key-alias',$signing.alias,'--ks-pass','env:JIANJI_STORE_PASSWORD','--out',$apkPath,'build\aligned.apk')
} finally { Remove-Item Env:JIANJI_STORE_PASSWORD -ErrorAction SilentlyContinue }
Run-Checked $java @('-jar',"$buildTools\lib\apksigner.jar",'verify','--verbose','--print-certs',$apkPath)
Run-Checked "$buildTools\zipalign.exe" @('-c','-v','4',$apkPath)
$hash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
"$hash  JianJi-$version.apk" | Set-Content -LiteralPath "deliverables\SHA256-$version.txt" -Encoding ASCII
Write-Output "APK ready: $projectRoot\$apkPath"
