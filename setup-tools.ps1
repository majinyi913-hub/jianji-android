# Downloads portable official tools into this project, without a system installation.
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
New-Item -ItemType Directory -Force '.tools','.tools\jdk','.tools\sdk\platforms','.tools\sdk\build-tools' | Out-Null
$packages = @(
    @{Url='https://aka.ms/download-jdk/microsoft-jdk-21-windows-x64.zip'; File='jdk.zip'; Target='.tools\jdk'},
    @{Url='https://dl.google.com/android/repository/platform-35_r02.zip'; File='platform.zip'; Target='.tools\sdk\platforms'},
    @{Url='https://dl.google.com/android/repository/build-tools_r35_windows.zip'; File='build-tools.zip'; Target='.tools\sdk\build-tools'}
)
foreach ($package in $packages) {
    $file = Join-Path '.tools' $package.File
    if (-not (Test-Path -LiteralPath $file)) {
        & curl.exe -L --fail --retry 3 $package.Url -o $file
        if ($LASTEXITCODE -ne 0) { throw "Download failed: $($package.File)" }
    }
    & tar.exe -xf $file -C $package.Target
    if ($LASTEXITCODE -ne 0) { throw "Extraction failed: $file" }
}
Write-Output 'Ready. Run: pwsh -File build.ps1'
