param([string]$Serial = '185502e1', [string]$Class = '', [string]$PersistPhase = '')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    & "$PSScriptRoot\build.ps1" -Tasks @('assembleDebug','assembleDebugAndroidTest')
    $adbPath = "$projectRoot\.toolchain\sdk\platform-tools\adb.exe"
    if (!(Test-Path -LiteralPath $adbPath)) { $adbPath = 'adb' }
    & $adbPath -s $Serial install -r '.\app\build\outputs\apk\debug\app-debug.apk'
    if ($LASTEXITCODE -ne 0) { throw 'Application installation failed' }
    & $adbPath -s $Serial install -r '.\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    if ($LASTEXITCODE -ne 0) { throw 'Test APK installation failed' }
    $testArgs = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r')
    if ($Class) { $testArgs += @('-e', 'class', $Class) }
    if ($PersistPhase) { $testArgs += @('-e', 'persistPhase', $PersistPhase) }
    $testArgs += 'com.example.zhinote.test/androidx.test.runner.AndroidJUnitRunner'
    New-Item -ItemType Directory -Path '.\docs\evidence' -Force | Out-Null
    & $adbPath @testArgs | ForEach-Object {
        $_
        # HyperOS can block an instrumentation process from bringing its first activity forward.
        # Launch the same target activity through authorized ADB after settings have been prepared.
        if ($_ -match 'Settings ready') { & $adbPath -s $Serial shell am start -n com.example.zhinote/.MainActivity | Out-Null }
    } | Tee-Object -FilePath '.\docs\evidence\device-tests.txt'
    $result = Get-Content -LiteralPath '.\docs\evidence\device-tests.txt'
    if (!($result -match 'OK \(') -or ($result -match 'FAILURES!!!')) { throw 'Device tests failed; see docs/evidence/device-tests.txt' }
} finally { Pop-Location }
