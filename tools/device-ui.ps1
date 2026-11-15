param([string]$Serial = '185502e1', [string]$Match = '', [switch]$Tap, [switch]$Description)
$ErrorActionPreference = 'Stop'
$adbPath = Join-Path (Split-Path -Parent $PSScriptRoot) '.toolchain\sdk\platform-tools\adb.exe'
$uiDump = $null
for ($attempt = 0; $attempt -lt 3; $attempt++) {
    $result = & $adbPath -s $Serial shell uiautomator dump --compressed /sdcard/zhinote-ui.xml 2>&1
    if ($result -match 'dumped to') { [xml]$uiDump = & $adbPath -s $Serial shell cat /sdcard/zhinote-ui.xml; break }
}
if (!$uiDump) { throw 'Device UI did not become idle; no tap performed.' }
$nodes = $uiDump.SelectNodes('//node')
if ($Tap) {
    $node = $nodes | Where-Object { if ($Description) { $_.'content-desc' -eq $Match } else { $_.text -eq $Match } } | Select-Object -First 1
    if (!$node) { throw "Visible UI item not found: $Match" }
    $coords = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    $x = [int](($coords[0] + $coords[2]) / 2); $y = [int](($coords[1] + $coords[3]) / 2)
    & $adbPath -s $Serial shell input tap $x $y
    "Tapped $Match at $x,$y"
} else {
    $nodes | Where-Object { $_.text -or $_.'content-desc' } | ForEach-Object { [PSCustomObject]@{text=$_.text;desc=$_.'content-desc';bounds=$_.bounds} } | Format-Table -AutoSize
}
