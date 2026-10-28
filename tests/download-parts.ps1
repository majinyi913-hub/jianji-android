param([string]$Url,[long]$Bytes,[string]$Name,[int]$Parts=8)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
$folder=Join-Path (Get-Location) ".tools\parts\$Name"
New-Item -ItemType Directory -Force $folder | Out-Null
$length=[long][Math]::Ceiling($Bytes/[double]$Parts)
0..($Parts-1) | ForEach-Object -Parallel {
    $index=$_
    $start=$index*$using:length
    $end=[Math]::Min($using:Bytes-1,$start+$using:length-1)
    $outFile=Join-Path $using:folder "$index.part"
    & curl.exe -sS -L --fail --retry 2 --max-time 600 --range "$start-$end" $using:Url -o $outFile
    if($LASTEXITCODE -ne 0 -or (Get-Item -LiteralPath $outFile).Length -ne ($end-$start+1)){throw "Part $index failed"}
    Write-Output "Part $index ready"
} -ThrottleLimit 8
$destination=Join-Path (Get-Location) ".tools\$Name-complete.zip"
$stream=[IO.File]::Create($destination)
try { for($i=0;$i -lt $Parts;$i++){ $part=[IO.File]::OpenRead((Join-Path $folder "$i.part"));try{$part.CopyTo($stream)}finally{$part.Dispose()} } }
finally{$stream.Dispose()}
if((Get-Item -LiteralPath $destination).Length -ne $Bytes){throw 'Size mismatch'}
Write-Output "Download ready: $destination"
