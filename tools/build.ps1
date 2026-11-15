param([string[]]$Tasks = @('assembleDebug', 'testDebugUnitTest', 'lintDebug'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$localJdk = Get-ChildItem -LiteralPath "$projectRoot\.toolchain" -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
if (Test-Path -LiteralPath "$projectRoot\.toolchain\sdk") { $env:ANDROID_HOME = "$projectRoot\.toolchain\sdk" }
$env:GRADLE_USER_HOME = "$projectRoot\.toolchain\gradle-home"
$env:ANDROID_USER_HOME = "$projectRoot\.toolchain\android-user"
New-Item -ItemType Directory -Path $env:ANDROID_USER_HOME -Force | Out-Null
Push-Location $projectRoot
try {
    $localGradle = "$projectRoot\.toolchain\gradle-8.11.1\bin\gradle.bat"
    if (Test-Path -LiteralPath $localGradle) { & $localGradle @Tasks --console=plain } else { & '.\gradlew.bat' @Tasks --console=plain }
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
} finally { Pop-Location }
