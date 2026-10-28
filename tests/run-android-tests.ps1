$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
$adb=Join-Path (Get-Location) '.tools\sdk\platform-tools\adb.exe'
$serial='emulator-5554'
Write-Output 'Waiting for isolated test emulator to boot...'
$ready=$false
for($attempt=0;$attempt -lt 120;$attempt++) {
    $status=& $adb -s $serial shell getprop sys.boot_completed 2>$null
    if($status -match '^1$'){$ready=$true;break}
    Start-Sleep -Seconds 5
}
if(-not $ready){throw 'Emulator did not finish booting within 10 minutes'}
Write-Output 'Installing delivery APK and instrumentation APK...'
& $adb -s $serial install -r 'deliverables\JianJi-1.3.1.apk'
if($LASTEXITCODE -ne 0){throw 'App installation failed'}
& $adb -s $serial install -r 'build\android-tests\tests.apk'
if($LASTEXITCODE -ne 0){throw 'Test installation failed'}
Write-Output 'Running database, backup and UI tests...'
& $adb -s $serial shell am instrument -w cn.jianji.tests/cn.jianji.tests.LedgerInstrumentation | Tee-Object -FilePath '.tools\android-tests.log'
if(-not (Select-String -LiteralPath '.tools\android-tests.log' -Pattern 'PASS:')){throw 'Android instrumentation failed; see .tools/android-tests.log'}
