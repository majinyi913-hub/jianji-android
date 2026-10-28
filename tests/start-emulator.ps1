param([string]$ToolsRoot='')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if(-not $ToolsRoot){$ToolsRoot=Join-Path $projectRoot '.tools'}
$env:ANDROID_SDK_ROOT=Join-Path $ToolsRoot 'sdk'
$env:ANDROID_HOME=$env:ANDROID_SDK_ROOT
$env:ANDROID_USER_HOME=Join-Path $ToolsRoot 'android-user'
$env:ANDROID_AVD_HOME=Join-Path $ToolsRoot 'avd'
$env:ANDROID_I_WANT_MY_TCG='yes'
New-Item -ItemType Directory -Force '.tools\avd\JianJiTest.avd',$env:ANDROID_USER_HOME | Out-Null
$avdPath=Join-Path $env:ANDROID_AVD_HOME 'JianJiTest.avd'
@"
avd.ini.encoding=UTF-8
path=$avdPath
target=android-30
"@ | Set-Content -LiteralPath '.tools\avd\JianJiTest.ini' -Encoding UTF8
@'
AvdId=JianJiTest
PlayStore.enabled=false
abi.type=x86_64
avd.ini.displayname=JianJi Test
avd.ini.encoding=UTF-8
disk.dataPartition.size=1G
fastboot.forceColdBoot=yes
hw.accelerometer=yes
hw.audioInput=no
hw.battery=yes
hw.cpu.arch=x86_64
hw.cpu.ncore=2
hw.dPad=no
hw.device.name=JianJiTest
hw.gps=no
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
hw.keyboard=yes
hw.lcd.density=320
hw.lcd.height=1600
hw.lcd.width=720
hw.mainKeys=no
hw.ramSize=2048
hw.sdCard=no
hw.sensors.orientation=yes
hw.sensors.proximity=no
hw.trackBall=no
image.sysdir.1=system-images\android-30\default\x86_64\
runtime.network.latency=none
runtime.network.speed=full
showDeviceFrame=no
tag.display=Default
tag.id=default
vm.heapSize=256
'@ | Set-Content -LiteralPath '.tools\avd\JianJiTest.avd\config.ini' -Encoding ASCII
$args=@('-avd','JianJiTest','-no-window','-no-audio','-no-snapshot','-no-boot-anim','-gpu','swiftshader_indirect','-feature','-Vulkan','-cores','1','-accel','off','-port','5554','-no-metrics','-verbose','-show-kernel')
$process=Start-Process -FilePath (Join-Path $ToolsRoot 'sdk\emulator\emulator.exe') -ArgumentList $args -WorkingDirectory $ToolsRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $projectRoot '.tools\emulator-out.log') -RedirectStandardError (Join-Path $projectRoot '.tools\emulator-err.log') -PassThru
Write-Output "Emulator PID: $($process.Id)"
$process.WaitForExit()
Write-Output "Emulator exit: $($process.ExitCode)"
