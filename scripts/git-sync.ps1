<#
.SYNOPSIS
    Brings the local branch up to date with its remote tracking branch.
.DESCRIPTION
    Fetches first, then fast-forwards (or reports a divergence).
    Does not use 'git pull --rebase' automatically; use -Rebase to opt in.
.EXAMPLE
    .\scripts\git-sync.ps1
    .\scripts\git-sync.ps1 -Rebase
#>

[CmdletBinding()]
param(
    [switch]$Rebase
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'lib\Common.ps1')

$root = Assert-Repository

if (-not (Test-HasRemoteOrigin)) {
    Write-Status -Level FAIL -Message 'origin remote is not configured.'
    exit 1
}

$branch = git -C $root rev-parse --abbrev-ref HEAD 2>$null
Write-Host "Syncing branch: $branch" -ForegroundColor Cyan

Write-Host 'Step 1/2: fetch' -ForegroundColor Cyan
$fetchCode = Invoke-Git -Arguments @('fetch', 'origin', '--prune')
if ($fetchCode -ne 0) {
    Write-Status -Level FAIL -Message 'Fetch failed. Check remote reachability and credentials.'
    exit $fetchCode
}

$upstream = git -C $root rev-parse --abbrev-ref '@{u}' 2>$null
if ($LASTEXITCODE -ne 0 -or -not $upstream) {
    Write-Status -Level WARNING -Message "Branch '$branch' has no upstream. Fetch only, no merge performed."
    Write-Host "Set it with: git branch --set-upstream-to=origin/$branch $branch"
    exit 0
}

Write-Host "Step 2/2: integrate origin/$branch -> $branch" -ForegroundColor Cyan

$ahead  = [int](git -C $root rev-list --count '@{u}..HEAD' 2>$null)
$behind = [int](git -C $root rev-list --count 'HEAD..@{u}' 2>$null)

Write-Host "  ahead=$ahead  behind=$behind"

if ($behind -eq 0 -and $ahead -eq 0) {
    Write-Status -Level PASS -Message 'Already up to date with origin.'
    exit 0
}

if ($ahead -gt 0 -and $behind -gt 0) {
    Write-Status -Level WARNING -Message 'Local and remote have diverged. Resolve manually before continuing.'
    Write-Host "  Inspect with:  git log --oneline --graph origin/$branch $branch"
    exit 2
}

$pullArgs = @('pull', '--ff-only', 'origin', $branch)
if ($Rebase) { $pullArgs = @('pull', '--rebase', 'origin', $branch) }

$code = Invoke-Git -Arguments $pullArgs
if ($code -ne 0) {
    Write-Status -Level FAIL -Message 'Sync failed. Resolve the branch state manually.'
    exit $code
}

Write-Status -Level PASS -Message "Branch '$branch' synchronized with origin."
