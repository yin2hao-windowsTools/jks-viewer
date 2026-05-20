param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('parse-version', 'install-wix', 'publish-release')]
    [string] $Command,

    [string] $Tag = $env:GITHUB_REF_NAME,
    [string] $AppVersion = '',
    [string] $ReleaseDir = 'build\release'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Write-GithubOutput {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Name,

        [Parameter(Mandatory = $true)]
        [string] $Value
    )

    if ([string]::IsNullOrWhiteSpace($env:GITHUB_OUTPUT)) {
        Write-Output "$Name=$Value"
        return
    }

    "$Name=$Value" | Out-File -FilePath $env:GITHUB_OUTPUT -Append -Encoding utf8
}

function Invoke-ParseVersionTag {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Tag
    )

    if ($Tag -notmatch '^v(?<number>\d+(?:\.\d+)*)(?:-(?<suffix>[0-9A-Za-z][0-9A-Za-z.-]*))?$') {
        throw "Tag '$Tag' is invalid. Use v-prefixed numeric tags such as v0.0.1 or v0.0.7-alpha."
    }

    $segments = @($Matches['number'] -split '\.')
    while ($segments.Count -lt 3) {
        $segments += '0'
    }

    $normalizedNumber = ($segments | Select-Object -First 3) -join '.'
    $suffix = if ($Matches.ContainsKey('suffix')) { $Matches['suffix'] } else { '' }
    $isPrerelease = -not [string]::IsNullOrWhiteSpace($suffix)
    $resolvedAppVersion = if ($isPrerelease) { "$normalizedNumber-$suffix" } else { $normalizedNumber }

    Write-GithubOutput -Name 'tag' -Value $Tag
    Write-GithubOutput -Name 'app_version' -Value $resolvedAppVersion
    Write-GithubOutput -Name 'installer_version' -Value $normalizedNumber
    Write-GithubOutput -Name 'is_prerelease' -Value $isPrerelease.ToString().ToLowerInvariant()
}

function Find-WixToolsetBin {
    $roots = @()
    $programFilesX86 = [Environment]::GetEnvironmentVariable('ProgramFiles(x86)')
    if (-not [string]::IsNullOrWhiteSpace($programFilesX86) -and (Test-Path $programFilesX86)) {
        $roots += $programFilesX86
    }
    if (Test-Path 'C:\ProgramData\chocolatey\lib') {
        $roots += 'C:\ProgramData\chocolatey\lib'
    }

    foreach ($root in $roots) {
        $candle = Get-ChildItem $root -Filter candle.exe -Recurse -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($null -eq $candle) {
            continue
        }

        $candidate = $candle.DirectoryName
        if (Test-Path (Join-Path $candidate 'light.exe')) {
            return $candidate
        }
    }

    return $null
}

function Invoke-InstallWixToolset {
    choco install wixtoolset -y --no-progress
    if (Get-NativeExitCode -ne 0) {
        throw "Chocolatey failed to install WiX Toolset."
    }

    $wixBin = Find-WixToolsetBin
    if (-not $wixBin) {
        throw "WiX Toolset was not found after installation."
    }

    Add-Content -Path $env:GITHUB_PATH -Value $wixBin
    $env:PATH = "$wixBin;$env:PATH"
    Write-Output "WiX Toolset found at $wixBin"
}

function Get-ReleaseAssets {
    param(
        [Parameter(Mandatory = $true)]
        [string] $ReleaseDir
    )

    if (-not (Test-Path $ReleaseDir)) {
        throw "Release directory '$ReleaseDir' does not exist."
    }

    $assets = @(Get-ChildItem $ReleaseDir -File | ForEach-Object { $_.FullName })
    if ($assets.Count -eq 0) {
        throw "No release assets were found in $ReleaseDir."
    }

    return $assets
}

function Invoke-Gh {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Arguments
    )

    & gh @Arguments
    if (Get-NativeExitCode -ne 0) {
        throw "gh $($Arguments -join ' ') failed."
    }
}

function Get-NativeExitCode {
    if (Test-Path variable:global:LASTEXITCODE) {
        return $global:LASTEXITCODE
    }

    return 0
}

function Invoke-PublishRelease {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Tag,

        [Parameter(Mandatory = $true)]
        [string] $AppVersion,

        [Parameter(Mandatory = $true)]
        [string] $ReleaseDir
    )

    $title = "JKS Viewer v$AppVersion"
    $notes = @(
        'Windows build artifacts:',
        '- EXE installer',
        '- MSI installer',
        '- Portable zip'
    ) -join [Environment]::NewLine

    $releasesJson = & gh release list --limit 1000 --json tagName
    if (Get-NativeExitCode -ne 0) {
        throw "Failed to list GitHub releases."
    }

    $releaseExists = @(($releasesJson | ConvertFrom-Json) | Where-Object { $_.tagName -eq $Tag }).Count -gt 0
    $assets = Get-ReleaseAssets -ReleaseDir $ReleaseDir

    if ($releaseExists) {
        $uploadArgs = @('release', 'upload', $Tag) + $assets + @('--clobber')
        Invoke-Gh -Arguments $uploadArgs
    } else {
        $createArgs = @('release', 'create', $Tag) + $assets + @('--title', $title, '--notes', $notes)
        Invoke-Gh -Arguments $createArgs
    }
}

switch ($Command) {
    'parse-version' {
        Invoke-ParseVersionTag -Tag $Tag
    }
    'install-wix' {
        Invoke-InstallWixToolset
    }
    'publish-release' {
        Invoke-PublishRelease -Tag $Tag -AppVersion $AppVersion -ReleaseDir $ReleaseDir
    }
}
