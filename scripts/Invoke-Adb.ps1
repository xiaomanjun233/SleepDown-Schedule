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
$adbPath = [System.IO.Path]::GetFullPath($adbPath)

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

# A matching protocol version does not prove that the daemon uses the SDK or this user's keys.
# Phone-link software can also start its SYSTEM daemon on 5038 after inheriting port settings.
function Get-ProjectAdbServer {
    $serverOutput = & $adbPath -P 5038 server-status
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the project ADB server on port 5038.' }
    $statusText = $serverOutput -join "`n"
    $executable = [regex]::Match($statusText, '(?m)^executable_absolute_path:\s*"([^"]+)"')
    $keystore = [regex]::Match($statusText, '(?m)^keystore_path:\s*"([^"]+)"')
    if (-not $executable.Success -or -not $keystore.Success) {
        throw 'ADB server-status did not identify the server executable and user keystore.'
    }
    [pscustomobject]@{
        Executable = [System.IO.Path]::GetFullPath($executable.Groups[1].Value.Replace('\\', '\'))
        Keystore = [System.IO.Path]::GetFullPath($keystore.Groups[1].Value.Replace('\\', '\'))
    }
}

$requestedCommand = if ($commandIndex -lt $args.Count) { [string]$args[$commandIndex] } else { 'help' }
if ($requestedCommand -notin 'help', 'version', 'kill-server') {
    $androidUserDirectory = if ($env:ANDROID_USER_HOME) { $env:ANDROID_USER_HOME } else {
        Join-Path $env:USERPROFILE '.android'
    }
    $userKeystore = [System.IO.Path]::GetFullPath((Join-Path $androidUserDirectory 'adbkey'))
    $server = Get-ProjectAdbServer
    if ($server.Executable -ne $adbPath -or $server.Keystore -ne $userKeystore) {
        Write-Host 'Reclaiming project port 5038 for the SDK ADB and current user pairing keys.'
        & $adbPath -P 5038 kill-server | Out-Host
        if ($LASTEXITCODE -ne 0) { throw 'Cannot stop the conflicting ADB server on port 5038.' }
        & $adbPath -P 5038 start-server | Out-Host
        if ($LASTEXITCODE -ne 0) { throw 'Cannot start the SDK ADB server on port 5038.' }
        $server = Get-ProjectAdbServer
        if ($server.Executable -ne $adbPath -or $server.Keystore -ne $userKeystore) {
            throw 'Port 5038 was taken again by another ADB server; project command was not sent.'
        }
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
