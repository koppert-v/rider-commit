[CmdletBinding()]
param(
    [string]$RiderPath,
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Test-RiderInstallation {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $false
    }

    return (Test-Path -LiteralPath (Join-Path $Path "bin\rider64.exe")) -or
        (Test-Path -LiteralPath (Join-Path $Path "product-info.json"))
}

function Find-RiderInstallation {
    $candidates = [System.Collections.Generic.List[string]]::new()

    if (-not [string]::IsNullOrWhiteSpace($env:RIDER_HOME)) {
        $candidates.Add($env:RIDER_HOME)
    }

    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        $candidates.Add((Join-Path $env:LOCALAPPDATA "Programs\Rider"))
    }

    $jetBrainsDirectory = Join-Path $env:ProgramFiles "JetBrains"
    if (Test-Path -LiteralPath $jetBrainsDirectory) {
        Get-ChildItem -LiteralPath $jetBrainsDirectory -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like "*Rider*" } |
            Sort-Object Name -Descending |
            ForEach-Object { $candidates.Add($_.FullName) }
    }

    return $candidates | Where-Object { Test-RiderInstallation $_ } | Select-Object -First 1
}

$projectDirectory = $PSScriptRoot
$gradleWrapper = Join-Path $projectDirectory "gradlew.bat"

if (-not (Test-Path -LiteralPath $gradleWrapper)) {
    throw "Gradle wrapper was not found: $gradleWrapper"
}

if ([string]::IsNullOrWhiteSpace($RiderPath)) {
    $RiderPath = Find-RiderInstallation
}

if (-not (Test-RiderInstallation $RiderPath)) {
    throw "Rider installation was not found. Pass it explicitly: .\build-plugin.ps1 -RiderPath 'C:\path\to\Rider'"
}

$RiderPath = (Resolve-Path -LiteralPath $RiderPath).Path
$tasks = [System.Collections.Generic.List[string]]::new()
$tasks.Add("clean")
if (-not $SkipTests) {
    $tasks.Add("test")
}
$tasks.Add("buildPlugin")

Write-Host "Building AI Commit for Rider" -ForegroundColor Cyan
Write-Host "Rider SDK: $RiderPath"

Push-Location $projectDirectory
try {
    & $gradleWrapper @tasks "-PlocalPlatformPath=$RiderPath" "--no-parallel" "--stacktrace"
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$artifact = Get-ChildItem -LiteralPath (Join-Path $projectDirectory "build\distributions") -Filter "*.zip" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if ($null -eq $artifact) {
    throw "Build completed, but no plugin ZIP was found."
}

Write-Host "Build completed successfully:" -ForegroundColor Green
Write-Host $artifact.FullName
