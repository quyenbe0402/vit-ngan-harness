<#
.SYNOPSIS
    Fetches from every remote and prunes deleted remote-tracking branches.
.DESCRIPTION
    Read-only with respect to the local working tree and local branches.
    Requires a configured origin remote and working credentials.
.EXAMPLE
    .\scripts\git-fetch-all.ps1
#>

[CmdletBinding()]
param(
    [switch]$Prune
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'lib\Common.ps1')

$root = Assert-Repository

if (-not (Test-HasRemoteOrigin)) {
    Write-Status -Level FAIL -Message 'origin remote is not configured. Nothing to fetch.'
    Write-Host 'Configure it with: git remote add origin <url>   (see docs/GITHUB_AUTH_SETUP.md)'
    exit 1
}

Write-Host 'Fetching from all remotes...' -ForegroundColor Cyan
$args = @('fetch', '--all')
if ($Prune) { $args += '--prune' }

$code = Invoke-Git -Arguments $args

if ($code -ne 0) {
    Write-Status -Level FAIL -Message "git fetch failed with exit code $code."
    Write-Host 'This usually means the remote is unreachable or credentials are missing.'
    Write-Host 'See docs/GITHUB_AUTH_SETUP.md'
    exit $code
}

Write-Status -Level PASS -Message 'Fetch complete.'
Write-Host ''
Write-Host 'Remote branches now known:' -ForegroundColor Cyan
git -C $root branch -r 2>$null | ForEach-Object { Write-Host "  $_" }
Write-Host ''
