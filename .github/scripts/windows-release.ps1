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

function Get-ReleaseAssetRows {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Assets,

        [Parameter(Mandatory = $true)]
        [string] $Tag
    )

    $baseUrl = "$($env:GITHUB_SERVER_URL)/$($env:GITHUB_REPOSITORY)/releases/download/$Tag"

    return @(
        $Assets |
            Sort-Object @{
                Expression = {
                    $name = [IO.Path]::GetFileName($_).ToLowerInvariant()
                    if ($name -match '-windows-x64\.exe$') { return 0 }
                    if ($name -match '-windows-x64\.msi$') { return 1 }
                    if ($name -match '-windows-portable\.zip$') { return 2 }
                    return 9
                }
            }, @{
                Expression = { [IO.Path]::GetFileName($_) }
            } |
            ForEach-Object {
                $fileName = [IO.Path]::GetFileName($_)
                $lowerName = $fileName.ToLowerInvariant()
                $assetType = switch -Regex ($lowerName) {
                    '-windows-x64\.exe$' { 'EXE installer'; break }
                    '-windows-x64\.msi$' { 'MSI installer'; break }
                    '-windows-portable\.zip$' { 'portable ZIP'; break }
                    default { 'package' }
                }

                [PSCustomObject]@{
                    Platform = 'Windows'
                    Type = $assetType
                    File = $fileName
                    Url = "$baseUrl/$fileName"
                }
            }
    )
}

function Get-PreviousReleaseTag {
    param(
        [Parameter(Mandatory = $true)]
        [string] $CurrentTag
    )

    $tags = @(
        git tag --sort=-v:refname |
            Where-Object { $_ -match '^v\d+(?:\.\d+)*(?:-[0-9A-Za-z][0-9A-Za-z.-]*)?$' -and $_ -ne $CurrentTag }
    )

    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to list git tags.'
    }

    return $tags | Select-Object -First 1
}

function Get-CommitGroups {
    param(
        [Parameter(Mandatory = $true)]
        [string] $CurrentTag,

        [string] $PreviousTag
    )

    $range = if ([string]::IsNullOrWhiteSpace($PreviousTag)) {
        $CurrentTag
    } else {
        "$PreviousTag..$CurrentTag"
    }

    $commitLines = @(
        git log $range --pretty=format:'%H%x09%s'
    )
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to collect git commits for range '$range'."
    }

    $groupOrder = @('feature', 'fix', 'ci', 'optimize', 'enhance', 'docs', 'refactor', 'test', 'chore', 'other')
    $groups = @{}

    foreach ($group in $groupOrder) {
        $groups[$group] = New-Object System.Collections.Generic.List[object]
    }

    foreach ($line in $commitLines) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }

        $parts = $line -split "`t", 2
        if ($parts.Count -lt 2) {
            continue
        }

        $sha = $parts[0]
        $subject = $parts[1]
        $group = 'other'
        $cleanSubject = $subject

        if ($subject -match '^\[(?<group>[^\]]+)\]\s*(?<text>.+)$') {
            $candidate = $Matches['group'].ToLowerInvariant()
            $cleanSubject = $Matches['text']
            if ($groups.ContainsKey($candidate)) {
                $group = $candidate
            } else {
                $group = 'other'
                $cleanSubject = "[$($Matches['group'])] $cleanSubject"
            }
        }

        $groups[$group].Add([PSCustomObject]@{
            Sha = $sha
            ShortSha = $sha.Substring(0, 7)
            Subject = $cleanSubject
        })
    }

    return [PSCustomObject]@{
        Order = $groupOrder
        Groups = $groups
    }
}

function New-ReleaseNotes {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Tag,

        [Parameter(Mandatory = $true)]
        [string[]] $Assets
    )

    $previousTag = Get-PreviousReleaseTag -CurrentTag $Tag
    $commitGroups = Get-CommitGroups -CurrentTag $Tag -PreviousTag $previousTag
    $assetRows = Get-ReleaseAssetRows -Assets $Assets -Tag $Tag
    $repoUrl = "$($env:GITHUB_SERVER_URL)/$($env:GITHUB_REPOSITORY)"
    $compareUrl = if ([string]::IsNullOrWhiteSpace($previousTag)) {
        "$repoUrl/releases/tag/$Tag"
    } else {
        "$repoUrl/compare/$previousTag...$Tag"
    }

    $builder = New-Object System.Text.StringBuilder
    [void] $builder.AppendLine('## What''s Changed')
    [void] $builder.AppendLine()

    $hasCommits = $false
    foreach ($group in $commitGroups.Order) {
        $items = $commitGroups.Groups[$group]
        if ($items.Count -eq 0) {
            continue
        }

        $hasCommits = $true
        [void] $builder.AppendLine("[$group]:")
        [void] $builder.AppendLine()
        foreach ($item in $items) {
            [void] $builder.AppendLine("- [$($item.ShortSha)]($repoUrl/commit/$($item.Sha)) $($item.Subject)")
        }
        [void] $builder.AppendLine()
    }

    if (-not $hasCommits) {
        [void] $builder.AppendLine('- No changes recorded')
        [void] $builder.AppendLine()
    }

    $compareLabel = if ([string]::IsNullOrWhiteSpace($previousTag)) {
        $Tag
    } else {
        "$previousTag...$Tag"
    }
    [void] $builder.AppendLine("Full Changelog: [$compareLabel]($compareUrl)")
    [void] $builder.AppendLine()
    [void] $builder.AppendLine('## &#21457;&#34892;&#29256;')
    [void] $builder.AppendLine()
    [void] $builder.AppendLine('| &#24179;&#21488; | &#31867;&#22411; | &#25991;&#20214; | &#24555;&#36895;&#38142;&#25509; |')
    [void] $builder.AppendLine('| --- | --- | --- | --- |')
    foreach ($row in $assetRows) {
        [void] $builder.AppendLine("| $($row.Platform) | $($row.Type) | $($row.File) | [&#19979;&#36733;]($($row.Url)) |")
    }

    return $builder.ToString().TrimEnd()
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

    $releasesJson = & gh release list --limit 1000 --json tagName
    if (Get-NativeExitCode -ne 0) {
        throw "Failed to list GitHub releases."
    }

    $releaseExists = @(($releasesJson | ConvertFrom-Json) | Where-Object { $_.tagName -eq $Tag }).Count -gt 0
    $assets = Get-ReleaseAssets -ReleaseDir $ReleaseDir
    $title = "JKS Viewer v$AppVersion"
    $notes = New-ReleaseNotes -Tag $Tag -Assets $assets
    $notesFile = Join-Path ([IO.Path]::GetTempPath()) "release-notes-$Tag.md"
    $notes | Out-File -FilePath $notesFile -Encoding utf8

    try {
        if ($releaseExists) {
            $editArgs = @('release', 'edit', $Tag, '--title', $title, '--notes-file', $notesFile)
            Invoke-Gh -Arguments $editArgs

            $uploadArgs = @('release', 'upload', $Tag) + $assets + @('--clobber')
            Invoke-Gh -Arguments $uploadArgs
        } else {
            $createArgs = @('release', 'create', $Tag) + $assets + @('--title', $title, '--notes-file', $notesFile)
            Invoke-Gh -Arguments $createArgs
        }
    } finally {
        if (Test-Path $notesFile) {
            Remove-Item $notesFile -Force
        }
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
