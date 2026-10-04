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

$env:ADB_SERVER_SOCKET = 'tcp:127.0.0.1:5038'
$env:ANDROID_ADB_SERVER_PORT = '5038'

$commandIndex = 0
while ($commandIndex -lt $args.Count) {
    $argument = [string]$args[$commandIndex]
    if ($argument -in '-s', '-t', '-H', '-P', '-L') {
        $commandIndex += 2
    } elseif ($argument -in '-a', '-d', '-e' -or $argument -match '^-[stHPL].+') {
        $commandIndex++
    } else {
        break
    }
}
if ($commandIndex -lt $args.Count -and $args[$commandIndex] -eq 'connect') {
    $connectOutput = & $adbPath -P 5038 @args 2>&1
    $adbExitCode = $LASTEXITCODE
    $connectOutput | Write-Output
    # ADB can print "cannot connect" and still return 0. Do not report that as success.
    if ($adbExitCode -eq 0 -and -not ($connectOutput -match '^(already connected to|connected to) ')) {
        $adbExitCode = 1
    }
    exit $adbExitCode
}

& $adbPath -P 5038 @args
exit $LASTEXITCODE
