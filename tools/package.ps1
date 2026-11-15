param([string]$EvidenceDirectory = '')
$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$utf8 = New-Object System.Text.UTF8Encoding($false)
$metadataPath = Join-Path $projectRoot 'app/build/outputs/apk/debug/output-metadata.json'
if (!(Test-Path -LiteralPath $metadataPath)) { throw 'Run tools/build.ps1 before packaging.' }
$metadata = Get-Content -Encoding UTF8 -Raw -LiteralPath $metadataPath | ConvertFrom-Json
if ($metadata.applicationId -ne 'com.example.zhinote' -or $metadata.elements.Count -ne 1) {
    throw 'Unexpected package identity or split APK output.'
}
$element = $metadata.elements[0]
$version = $element.versionName
if ($version -notmatch '^\d+\.\d+\.\d+$' -or $element.versionCode -lt 2) { throw 'Unexpected version.' }
if ($element.outputFile -ne 'app-debug.apk') { throw 'Unexpected APK filename.' }
$apkPath = Join-Path (Split-Path -Parent $metadataPath) $element.outputFile
$config = Get-Content -Encoding UTF8 -Raw -LiteralPath (Join-Path $projectRoot 'app/build.gradle.kts')
if ($config -notmatch ('versionName\s*=\s*"' + [regex]::Escape($version) + '"') -or
    $config -notmatch ('versionCode\s*=\s*' + $element.versionCode + '\b')) {
    throw 'APK metadata does not match source version. Rebuild first.'
}
$productionFiles = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app/src/main'), (Join-Path $projectRoot 'app/src/debug') -Recurse -File)
# Test-only Gradle configuration can be newer than an up-to-date production APK.
# The source version is checked above; Gradle build results verify the full configuration.
if ($productionFiles | Where-Object { $_.LastWriteTimeUtc -gt (Get-Item -LiteralPath $apkPath).LastWriteTimeUtc }) {
    throw 'Application source is newer than APK. Rebuild first.'
}
$unitFiles = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app/build/test-results/testDebugUnitTest') -Filter 'TEST-*.xml')
if ($unitFiles.Count -eq 0) { throw 'Unit test results are missing.' }
foreach ($file in $unitFiles) {
    [xml]$report = Get-Content -Encoding UTF8 -Raw -LiteralPath $file.FullName
    if ([int]$report.testsuite.failures -ne 0 -or [int]$report.testsuite.errors -ne 0) { throw 'Unit tests failed.' }
}
$lintPath = Join-Path $projectRoot 'app/build/reports/lint-results-debug.xml'
[xml]$lint = Get-Content -Encoding UTF8 -Raw -LiteralPath $lintPath
if ($lint.issues.issue | Where-Object { $_.severity -in @('Error', 'Fatal') }) { throw 'Lint errors remain.' }

$localJdk = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.toolchain') -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
$sdkRoot = Join-Path $projectRoot '.toolchain/sdk'
if (!(Test-Path -LiteralPath $sdkRoot)) { $sdkRoot = $env:ANDROID_HOME }
if (!$sdkRoot) { $sdkRoot = $env:ANDROID_SDK_ROOT }
if (!$sdkRoot) { throw 'Set ANDROID_HOME to verify APK signing.' }
$buildTools = Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1
$signerPath = Join-Path $buildTools.FullName 'apksigner.bat'
$signature = @(& $signerPath verify --verbose --print-certs $apkPath 2>&1)
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
$originalApk = Join-Path $projectRoot 'backups/before-v2/ZhiNote-1.0.0-debug.apk'
if (Test-Path -LiteralPath $originalApk) {
    $originalSignature = @(& $signerPath verify --print-certs $originalApk 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'Original APK signature verification failed.' }
    $digest = ($signature | Select-String 'certificate SHA-256 digest:').Line
    $originalDigest = ($originalSignature | Select-String 'certificate SHA-256 digest:').Line
    if ($digest -ne $originalDigest) { throw 'Signing certificate changed; overlay installation is unsafe.' }
}

if (!$EvidenceDirectory) {
    $EvidenceDirectory = if ($version -eq '2.0.0') { 'docs/v2' } else { 'docs/v2/ai-fix' }
}
$evidenceRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot $EvidenceDirectory))
if (!$evidenceRoot.StartsWith($projectRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Evidence directory must stay within the project.'
}
$linkRoot = '../' * ($EvidenceDirectory.Replace('\', '/').Trim('/').Split('/').Count)
New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
[System.IO.File]::WriteAllLines((Join-Path $evidenceRoot 'apk-signature.txt'), [string[]]$signature, $utf8)
$unitDestination = Join-Path $evidenceRoot 'unit-test-results'
New-Item -ItemType Directory -Path $unitDestination -Force | Out-Null
$unitFiles | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $unitDestination -Force }
Copy-Item -LiteralPath $lintPath -Destination $evidenceRoot -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'app/build/reports/lint-results-debug.html') -Destination $evidenceRoot -Force

# Compare implementation files against the private pre-upgrade hash manifest.
$rootNames = @('.gitignore', 'README.md', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat', 'app/build.gradle.kts')
$sourceDirectories = @('app/src', 'app/schemas', 'gradle', 'tools')
$sourceFiles = @($rootNames | ForEach-Object { Get-Item -LiteralPath (Join-Path $projectRoot $_) })
foreach ($directory in $sourceDirectories) { $sourceFiles += Get-ChildItem -LiteralPath (Join-Path $projectRoot $directory) -Recurse -File }
$baselinePath = Join-Path $projectRoot 'backups/before-v2/source-manifest.txt'
if (Test-Path -LiteralPath $baselinePath) {
    $baseline = @{}
    foreach ($line in Get-Content -Encoding UTF8 -LiteralPath $baselinePath) {
        if ($line -match '^([A-Fa-f0-9]{64})\s+(.+)$') { $baseline[$matches[2].Replace('\', '/')] = $matches[1] }
    }
    $changes = @()
    foreach ($file in $sourceFiles | Sort-Object FullName) {
        $relative = $file.FullName.Substring($projectRoot.Length + 1).Replace('\', '/')
        $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
        if (!$baseline.ContainsKey($relative)) { $changes += [pscustomobject]@{ Status = 'Added'; Path = $relative; SHA256 = $hash } }
        elseif ($baseline[$relative] -ne $hash) { $changes += [pscustomobject]@{ Status = 'Modified'; Path = $relative; SHA256 = $hash } }
    }
    foreach ($relative in $baseline.Keys) {
        if (!(Test-Path -LiteralPath (Join-Path $projectRoot $relative))) { $changes += [pscustomobject]@{ Status = 'Removed'; Path = $relative; SHA256 = '' } }
    }
    $changes | Sort-Object Path | Export-Csv -LiteralPath (Join-Path $evidenceRoot 'changed-files.csv') -NoTypeInformation -Encoding UTF8
    $rows = @('# V2 修改文件清单', '', '对照升级前源码 SHA-256 清单核对工程、应用代码、schemas 和工具。完整哈希见 [CSV](changed-files.csv)。文档与截图另外列在文末。', '', '| 状态 | 文件 |', '| --- | --- |')
    foreach ($change in $changes | Sort-Object Path) {
        $label = switch ($change.Status) { 'Added' { '新增' }; 'Modified' { '修改' }; 'Removed' { '删除' } }
        $rows += '| ' + $label + ' | [' + $change.Path + '](' + $linkRoot + $change.Path + ') |'
    }
    $rows += @('', '## 文档和验证资料', '', '- 新增 docs/v2/升级报告.md、AI使用说明.md、测试报告.md、性能记录.md，以及本清单和哈希 CSV。', '- 新增 docs/v2/ 下的模块/最终测试输出、JUnit XML、Lint 报告、签名/交付核验、星群与双击截图、HyperOS 桌面截图和绑定证据。', '- README.md 以及 docs/测试记录.md、docs/性能记录.md 增加 V2 入口；V1 历史记录保留。', '', '状态按内容哈希比较。新增摘要迁移、AI 和小部件实现均在原项目中，未删除原有业务模块。')
    [System.IO.File]::WriteAllLines((Join-Path $evidenceRoot '修改文件清单.md'), [string[]]$rows, $utf8)
}

# Use an allowlist: local databases, private keys, caches and old deliveries stay out.
$archiveFiles = @($sourceFiles)
$archiveFiles += Get-ChildItem -LiteralPath (Join-Path $projectRoot 'docs') -Recurse -File
$archiveFiles = @($archiveFiles | Sort-Object FullName -Unique)
foreach ($file in $archiveFiles) {
    $relative = $file.FullName.Substring($projectRoot.Length + 1).Replace('\', '/')
    if ($relative -match '(^|/)(\.toolchain|\.gradle|\.kotlin|backups|build|deliverables)(/|$)' -or
        $file.Extension -in @('.keystore', '.jks', '.db', '.wal', '.enc')) { throw "Private/build artifact in source: $relative" }
}
$deliveryRoot = Join-Path $projectRoot 'deliverables'
New-Item -ItemType Directory -Path $deliveryRoot -Force | Out-Null
$deliveryApk = Join-Path $deliveryRoot "ZhiNote-$version-debug.apk"
$deliveryZip = Join-Path $deliveryRoot "ZhiNote-$version-source.zip"
Copy-Item -LiteralPath $apkPath -Destination $deliveryApk -Force
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$temporaryZip = "$deliveryZip.partial"
$stream = [System.IO.File]::Open($temporaryZip, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
$archive = New-Object System.IO.Compression.ZipArchive($stream, [System.IO.Compression.ZipArchiveMode]::Create, $false)
try {
    foreach ($file in $archiveFiles) {
        $relative = $file.FullName.Substring($projectRoot.Length + 1).Replace('\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, "ZhiNote/$relative", [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $archive.Dispose(); $stream.Dispose() }
Move-Item -LiteralPath $temporaryZip -Destination $deliveryZip -Force
$checksums = @($deliveryApk, $deliveryZip) | ForEach-Object { (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant() + '  ' + [System.IO.Path]::GetFileName($_) }
[System.IO.File]::WriteAllLines((Join-Path $deliveryRoot 'V2-SHA256SUMS.txt'), [string[]]$checksums, $utf8)
[System.IO.File]::WriteAllLines((Join-Path $deliveryRoot "ZhiNote-$version-SHA256SUMS.txt"), [string[]]$checksums, $utf8)
$allV2Checksums = Get-ChildItem -LiteralPath $deliveryRoot -Filter 'ZhiNote-2.*' -File |
    Where-Object { $_.Extension -in @('.apk', '.zip') } | Sort-Object Name | ForEach-Object {
        (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() + '  ' + $_.Name
    }
[System.IO.File]::WriteAllLines((Join-Path $deliveryRoot 'V2-SHA256SUMS.txt'), [string[]]$allV2Checksums, $utf8)
Write-Output "Packaged $version, versionCode $($element.versionCode), $($archiveFiles.Count) source files."
Get-Item -LiteralPath $deliveryApk, $deliveryZip | Select-Object Name, Length
