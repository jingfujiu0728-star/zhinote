param([string]$Serial = '185502e1', [string]$Destination = 'docs\evidence\screenshot.png')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $adbPath = "$projectRoot\.toolchain\sdk\platform-tools\adb.exe"
    & $adbPath -s $Serial shell screencap -p /sdcard/ZhiNote-evidence.png
    & $adbPath -s $Serial pull /sdcard/ZhiNote-evidence.png $Destination
    if ($LASTEXITCODE -ne 0) { throw 'Screenshot failed' }
    & $adbPath -s $Serial shell rm /sdcard/ZhiNote-evidence.png
} finally { Pop-Location }
