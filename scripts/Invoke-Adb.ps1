# Keep project debugging separate from phone-link software using the default 5037 server.
# Examples: .\scripts\Invoke-Adb.ps1 devices -l
#           .\scripts\Invoke-Adb.ps1 pair <address:pairing-port> <pairing-code>
#           .\scripts\Invoke-Adb.ps1 -s <serial> install -r <apk>
$ErrorActionPreference = 'Stop'

$projectDirectory = Split-Path -Parent $PSScriptRoot
$propertiesPath = Join-Path $projectDirectory 'local.properties'
$sdkDirectory = $null
if (Test-Path -LiteralPath $propertiesPath) {
    $sdkLine = Get-Content -LiteralPath $propertiesPath |
        Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
    if ($sdkLine) {
        $sdkDirectory = $sdkLine.Substring('sdk.dir='.Length).Replace('\:', ':').Replace('\\', '\')
    }
}
if (-not $sdkDirectory) { $sdkDirectory = $env:ANDROID_HOME }
if (-not $sdkDirectory) { $sdkDirectory = $env:ANDROID_SDK_ROOT }
if (-not $sdkDirectory) { throw 'Set sdk.dir in local.properties or set ANDROID_HOME before using project ADB.' }

$adbPath = Join-Path $sdkDirectory 'platform-tools/adb.exe'
if (-not (Test-Path -LiteralPath $adbPath)) { throw "Android SDK ADB was not found: $adbPath" }

& $adbPath -P 5038 @args
exit $LASTEXITCODE
