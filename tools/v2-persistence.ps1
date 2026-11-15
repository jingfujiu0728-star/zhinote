param([string]$Serial = '185502e1')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$adbPath = "$projectRoot\.toolchain\sdk\platform-tools\adb.exe"
& "$PSScriptRoot\device-tests.ps1" -Serial $Serial -Class 'com.example.zhinote.ForceStopPersistenceDeviceTest' -PersistPhase 'seed'
Copy-Item -LiteralPath "$projectRoot\docs\evidence\device-tests.txt" -Destination "$projectRoot\docs\v2\persistence-before-force-stop.txt"
& $adbPath -s $Serial shell am force-stop com.example.zhinote
& $adbPath -s $Serial shell am start -W -n com.example.zhinote/.MainActivity
& "$PSScriptRoot\device-tests.ps1" -Serial $Serial -Class 'com.example.zhinote.ForceStopPersistenceDeviceTest' -PersistPhase 'verify'
Copy-Item -LiteralPath "$projectRoot\docs\evidence\device-tests.txt" -Destination "$projectRoot\docs\v2\persistence-after-force-stop.txt"
