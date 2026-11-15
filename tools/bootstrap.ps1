param([string]$ProxyUrl = $env:HTTPS_PROXY)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ($ProxyUrl) { $env:HTTPS_PROXY = $ProxyUrl }
Push-Location $projectRoot
try {
    $archiveRoot = "$projectRoot\.toolchain\archives"
    New-Item -ItemType Directory -Path $archiveRoot -Force | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    function Get-Archive([string]$Url, [string]$File) {
        & node "$PSScriptRoot\download.mjs" $Url $File
        if ($LASTEXITCODE -ne 0) { throw "Download failed: $Url" }
    }
    if (!(Get-ChildItem "$projectRoot\.toolchain" -Directory -Filter 'jdk-*')) {
        Get-Archive 'https://api.adoptium.net/v3/binary/version/jdk-17.0.21+9/windows/x64/jdk/hotspot/normal/eclipse' "$archiveRoot\jdk.zip"
        [System.IO.Compression.ZipFile]::ExtractToDirectory("$archiveRoot\jdk.zip", "$projectRoot\.toolchain")
    }
    if (!(Test-Path -LiteralPath "$projectRoot\.toolchain\gradle-8.11.1")) {
        Get-Archive 'https://downloads.gradle.org/distributions/gradle-8.11.1-bin.zip' "$archiveRoot\gradle.zip"
        $actual = (Get-FileHash -LiteralPath "$archiveRoot\gradle.zip" -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actual -ne 'f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6') { throw 'Gradle checksum mismatch' }
        [System.IO.Compression.ZipFile]::ExtractToDirectory("$archiveRoot\gradle.zip", "$projectRoot\.toolchain")
    }
    if (!(Test-Path -LiteralPath "$projectRoot\.toolchain\sdk\cmdline-tools\latest")) {
        Get-Archive 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' "$archiveRoot\commandlinetools.zip"
        [System.IO.Compression.ZipFile]::ExtractToDirectory("$archiveRoot\commandlinetools.zip", "$projectRoot\.toolchain\sdk\cmdline-tools\staging")
        Move-Item -LiteralPath "$projectRoot\.toolchain\sdk\cmdline-tools\staging\cmdline-tools" -Destination "$projectRoot\.toolchain\sdk\cmdline-tools\latest"
    }
    $env:JAVA_HOME = (Get-ChildItem "$projectRoot\.toolchain" -Directory -Filter 'jdk-*' | Select-Object -First 1).FullName
    $env:ANDROID_HOME = "$projectRoot\.toolchain\sdk"
    $env:ANDROID_USER_HOME = "$projectRoot\.toolchain\android-user"
    New-Item -ItemType Directory -Path "$env:ANDROID_USER_HOME\cache","$projectRoot\.toolchain\gradle-home" -Force | Out-Null
    $sdkArguments = @("--sdk_root=$env:ANDROID_HOME")
    if ($ProxyUrl) {
        $taskProxy = [uri]$ProxyUrl
        $sdkArguments += @('--proxy=http', "--proxy_host=$($taskProxy.Host)", "--proxy_port=$($taskProxy.Port)")
        @("systemProp.http.proxyHost=$($taskProxy.Host)", "systemProp.http.proxyPort=$($taskProxy.Port)",
          "systemProp.https.proxyHost=$($taskProxy.Host)", "systemProp.https.proxyPort=$($taskProxy.Port)",
          'systemProp.http.nonProxyHosts=localhost|127.*') | Set-Content "$projectRoot\.toolchain\gradle-home\gradle.properties" -Encoding Ascii
    }
    1..100 | ForEach-Object { 'y' } | & "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" @sdkArguments 'platforms;android-36' 'build-tools;35.0.0' 'platform-tools'
    if ($LASTEXITCODE -ne 0) { throw 'SDK installation failed' }
    Write-Output 'Local toolchain ready. Run tools/build.ps1.'
} finally { Pop-Location }
