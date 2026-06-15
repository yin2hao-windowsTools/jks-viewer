param(
    [string]$GradleTask = "packageWindowsPortable"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$OutputDir = Join-Path $ProjectRoot "output"
$ReleaseDir = Join-Path $ProjectRoot "build\release"
$GradleWrapper = Join-Path $ProjectRoot "gradlew.bat"

if (-not (Test-Path -LiteralPath $GradleWrapper)) {
    throw "Gradle wrapper not found: $GradleWrapper"
}

Push-Location $ProjectRoot
try {
    if (Test-Path -LiteralPath $ReleaseDir) {
        Remove-Item -LiteralPath $ReleaseDir -Recurse -Force
    }

    if (Test-Path -LiteralPath $OutputDir) {
        Remove-Item -LiteralPath $OutputDir -Recurse -Force
    }

    & $GradleWrapper $GradleTask
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle task failed with exit code $LASTEXITCODE"
    }

    if (-not (Test-Path -LiteralPath $ReleaseDir)) {
        throw "Release directory was not created: $ReleaseDir"
    }

    New-Item -ItemType Directory -Path $OutputDir | Out-Null
    Copy-Item -Path (Join-Path $ReleaseDir "*") -Destination $OutputDir -Recurse -Force

    Write-Host "Build output copied to: $OutputDir"
}
finally {
    Pop-Location
}
