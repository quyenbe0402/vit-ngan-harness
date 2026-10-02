<#
.SYNOPSIS
    Inspects a branch (local or remote) and reports its divergence, commits and changed files.
.DESCRIPTION
    Read-only inspection tool. Useful for reviewing a Claude handoff before validating it.
.EXAMPLE
    .\scripts\git-check-branch.ps1 -Branch claude/M0-001-audit
    .\scripts\git-check-branch.ps1 -Branch claude/M0-001-audit -CompareWith develop
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Branch,
    [string]$CompareWith = 'origin/develop'
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'lib\Common.ps1')

$root = Assert-Repository

# Resolve the branch, accepting 'foo', 'origin/foo' or full ref.
$resolved = $null
foreach ($candidate in @($Branch, "origin/$Branch")) {
    git -C $root rev-parse --verify --quiet $candidate *> $null
    if ($LASTEXITCODE -eq 0) { $resolved = $candidate; break }
}

if (-not $resolved) {
    Write-Status -Level FAIL -Message "Branch '$Branch' was not found locally or on the remote."
    Write-Host 'Run .\scripts\git-fetch-all.ps1 and try again.'
    exit 1
}

Write-Host ''
Write-Host "=== BRANCH: $resolved ===" -ForegroundColor Cyan
Write-Host ''

$tip = git -C $root rev-parse --short $resolved 2>$null
Write-Host "Tip commit    : $tip"
$lastCommitDate = git -C $root --no-pager log -1 --format='%ad' --date=iso $resolved 2>$null
$lastAuthor      = git -C $root --no-pager log -1 --format='%an' $resolved 2>$null
Write-Host "Last author   : $lastAuthor"
Write-Host "Last commit   : $lastCommitDate"

# Resolve comparison base
$base = $null
foreach ($candidate in @($CompareWith, "origin/$CompareWith", 'main', 'origin/main')) {
    git -C $root rev-parse --verify --quiet $candidate *> $null
    if ($LASTEXITCODE -eq 0) { $base = $candidate; break }
}

if (-not $base) {
    Write-Status -Level WARNING -Message "Comparison base '$CompareWith' not found. Skipping divergence analysis."
}
else {
    $ahead  = [int](git -C $root rev-list --count "$base..$resolved" 2>$null)
    $behind = [int](git -C $root rev-list --count "$resolved..$base" 2>$null)
    Write-Host ''
    Write-Host "Compared with : $base" -ForegroundColor Cyan
    Write-Host "  commits ahead  : $ahead"
    Write-Host "  commits behind : $behind"
    if ($ahead -eq 0) {
        Write-Status -Level WARNING -Message "Branch '$resolved' has no new commits relative to $base."
    }
    else {
        Write-Status -Level PASS -Message "Branch '$resolved' carries $ahead new commit(s)."
    }
}

Write-Host ''
Write-Host '--- Commits ---' -ForegroundColor Cyan
$range = if ($base) { "$base..$resolved" } else { $resolved }
git -C $root --no-pager log --oneline -20 $range 2>$null
if ($LASTEXITCODE -ne 0) { Write-Host '(no history)' }

Write-Host ''
Write-Host '--- Changed files ---' -ForegroundColor Cyan
if ($base) {
    git -C $root --no-pager diff --stat "$base...$resolved" 2>$null
    Write-Host ''
    Write-Host '--- Full diff ---' -ForegroundColor Cyan
    git -C $root --no-pager diff "$base...$resolved" 2>$null
}
else {
    git -C $root --no-pager show --stat $resolved 2>$null
}

Write-Host ''
