param([string]$Sdk="$env:LOCALAPPDATA\Android\Sdk")
$ErrorActionPreference='Stop'
$workspace=Split-Path $PSScriptRoot -Parent
$avdRoot=Join-Path $workspace '.android-local\avd'
$avd=Join-Path $avdRoot 'SmartAttendance_Test.avd'
$image=Join-Path $Sdk 'system-images\android-35\google_apis_playstore\x86_64'
if(!(Test-Path (Join-Path $image 'system.img'))) {throw 'Install Android 35 Google Play x86_64 system image in Android Studio.'}
New-Item -ItemType Directory -Force -Path $avd | Out-Null
@"
avd.ini.encoding=UTF-8
path=$avd
target=android-35
"@ | Set-Content (Join-Path $avdRoot 'SmartAttendance_Test.ini') -Encoding ASCII
@"
AvdId=SmartAttendance_Test
avd.ini.displayname=Smart Attendance Test
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=2
hw.ramSize=2048
hw.lcd.width=1080
hw.lcd.height=1920
hw.lcd.density=420
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
hw.camera.back=virtualscene
hw.camera.front=none
hw.keyboard=yes
hw.mainKeys=no
disk.dataPartition.size=3G
image.sysdir.1=$image\
tag.id=google_apis_playstore
tag.display=Google Play
PlayStore.enabled=true
"@ | Set-Content (Join-Path $avd 'config.ini') -Encoding ASCII
$env:ANDROID_AVD_HOME=$avdRoot
$env:ANDROID_HOME=$Sdk
$proc=Start-Process -FilePath (Join-Path $Sdk 'emulator\emulator.exe') -ArgumentList @('-avd','SmartAttendance_Test','-port','5580','-no-window','-no-audio','-no-snapshot','-no-boot-anim','-gpu','swiftshader_indirect') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $workspace '.tools\emulator.log') -RedirectStandardError (Join-Path $workspace '.tools\emulator-error.log')
Write-Output "Started isolated emulator PID $($proc.Id), serial emulator-5580."
