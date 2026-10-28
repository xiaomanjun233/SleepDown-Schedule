param([Parameter(ValueFromRemainingArguments = $true)][string[]]$BuildArguments)
$ErrorActionPreference = 'Stop'
$buildRoot = Split-Path $PSScriptRoot -Parent
$env:GRADLE_USER_HOME = 'D:\gradle-home'
# Keep native test/JNI paths short on Windows; test names add their own nested directories.
$env:TEMP = 'D:\sd-tmp'
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Path $env:GRADLE_USER_HOME, $env:TEMP -Force | Out-Null
if (!$env:JAVA_HOME) { $env:JAVA_HOME = 'D:\Android studio\JDK' }
Push-Location $buildRoot
try {
    & (Join-Path $buildRoot 'gradlew.bat') @BuildArguments
    exit $LASTEXITCODE
} finally { Pop-Location }
