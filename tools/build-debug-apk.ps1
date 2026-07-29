param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$gradleWrapper = Join-Path $projectRoot "gradlew.bat"
$buildFile = Join-Path $projectRoot "app/build.gradle.kts"
$apkSource = Join-Path $projectRoot "app/build/outputs/apk/debug/app-debug.apk"
$distributionDirectory = Join-Path $projectRoot "dist"

if (-not $SkipBuild) {
    & $gradleWrapper :app:assembleDebug
    if ($LASTEXITCODE -ne 0) {
        throw "Debug APK build failed with exit code $LASTEXITCODE."
    }
}

if (-not (Test-Path -LiteralPath $apkSource)) {
    throw "Debug APK not found: $apkSource"
}

$versionLine = Select-String -LiteralPath $buildFile -Pattern 'versionName\s*=\s*"([^"]+)"' |
    Select-Object -First 1
if (-not $versionLine -or $versionLine.Line -notmatch 'versionName\s*=\s*"([^"]+)"') {
    throw "Unable to determine versionName from $buildFile"
}

$versionName = $Matches[1]
$distributionFile = Join-Path $distributionDirectory "WordSpring-v$versionName-debug.apk"

New-Item -ItemType Directory -Force -Path $distributionDirectory | Out-Null
Copy-Item -LiteralPath $apkSource -Destination $distributionFile -Force

$checksum = (Get-FileHash -LiteralPath $distributionFile -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host "APK created: $distributionFile"
Write-Host "SHA-256: $checksum"
