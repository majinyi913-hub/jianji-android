$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$jdk=(Get-ChildItem -LiteralPath '.tools\jdk' -Directory | Select-Object -First 1).FullName
$tools="$projectRoot\.tools\sdk\build-tools\android-15"
$androidJar='.tools\sdk\platforms\android-35\android.jar'
function Run([string]$exe,[string[]]$params){& $exe @params;if($LASTEXITCODE -ne 0){throw "Failed: $exe"}}
New-Item -ItemType Directory -Force 'build\android-tests\classes','build\android-tests\dex' | Out-Null
Run "$tools\aapt2.exe" @('link','-o','build\android-tests\resources.apk','-I',$androidJar,'--manifest','tests\AndroidManifest.xml')
Run "$jdk\bin\javac.exe" @('--release','8','-encoding','UTF-8','-cp',"$androidJar;$projectRoot\build\classes.jar",'-d','build\android-tests\classes','tests\LedgerInstrumentation.java')
Run "$jdk\bin\jar.exe" @('cf','build\android-tests\classes.jar','-C','build\android-tests\classes','.')
Run "$jdk\bin\java.exe" @('-cp',"$tools\lib\d8.jar",'com.android.tools.r8.D8','--min-api','26','--lib',$androidJar,'--classpath','build\classes.jar','--output','build\android-tests\dex','build\android-tests\classes.jar')
Copy-Item -LiteralPath 'build\android-tests\resources.apk' -Destination 'build\android-tests\unsigned.apk' -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::Open("$projectRoot\build\android-tests\unsigned.apk",[IO.Compression.ZipArchiveMode]::Update)
try{[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,"$projectRoot\build\android-tests\dex\classes.dex",'classes.dex')|Out-Null}finally{$zip.Dispose()}
Run "$tools\zipalign.exe" @('-f','4','build\android-tests\unsigned.apk','build\android-tests\aligned.apk')
$sign=Get-Content -LiteralPath 'signing\release.json' -Raw | ConvertFrom-Json
$env:JIANJI_STORE_PASSWORD=$sign.password
try{Run "$jdk\bin\java.exe" @('-jar',"$tools\lib\apksigner.jar",'sign','--ks',"signing\$($sign.store)",'--ks-pass','env:JIANJI_STORE_PASSWORD','--out','build\android-tests\tests.apk','build\android-tests\aligned.apk')}finally{Remove-Item Env:JIANJI_STORE_PASSWORD}
Write-Output 'Test APK ready: build\android-tests\tests.apk'
