<#
.SYNOPSIS
    Reports the full Git state of the project: root, branch, commit, remotes,
    branches, working tree status and upstream tracking.
.DESCRIPTION
    Read-only. Makes no changes to the repository.
.EXAMPLE
    .\scripts\git-status.ps1
#>

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'lib\Common.ps1')

$root = Assert-Repository

Write-Host ''
Write-Host '=== GIT STATUS ===' -ForegroundColor Cyan
Write-Host ''

$gitVersion = (git --version) 2>$null
Write-Host "Git version      : $gitVersion"
Write-Host "Repository root  : $root"

$branch = git -C $root rev-parse --abbrev-ref HEAD 2>$null
Write-Host "Current branch   : $branch"

$commit = git -C $root rev-parse --short HEAD 2>$null
if ($LASTEXITCODE -ne 0 -or -not $commit) {
    $commit = '(no commits yet)'
}
$full = git -C $root rev-parse HEAD 2>$null
if ($full) { Write-Host "Full commit      : $full" }
Write-Host "Commit (short)   : $commit"

$upstream = git -C $root rev-parse --abbrev-ref '@{u}' 2>$null
if ($LASTEXITCODE -eq 0 -and $upstream) {
    Write-Host "Upstream         : $upstream"
    $ahead = git -C $root rev-list --count '@{u}..HEAD' 2>$null
    $behind = git -C $root rev-list --count 'HEAD..@{u}' 2>$null
    Write-Host "Ahead / Behind   : $ahead / $behind"
}
else {
    Write-Host "Upstream         : (none - no remote-tracking branch)"
}

Write-Host ''
Write-Host '--- Remotes ---' -ForegroundColor Cyan
$remotes = git -C $root remote -v 2>$null
if ($remotes) { $remotes } else { Write-Host '(none configured)' }

if (Test-HasRemoteOrigin) {
    Write-Status -Level PASS -Message 'origin remote is configured.'
}
else {
    Write-Status -Level WARNING -Message 'origin remote is NOT configured. See docs/GITHUB_AUTH_SETUP.md'
}

Write-Host ''
Write-Host '--- Local branches ---' -ForegroundColor Cyan
$local = git -C $root branch -vv 2>$null
if ($local) { $local } else { Write-Host '(none)' }

Write-Host ''
Write-Host '--- Remote branches (last known) ---' -ForegroundColor Cyan
$remoteBranches = git -C $root branch -r 2>$null
if ($remoteBranches) { $remoteBranches } else { Write-Host '(none - run scripts\git-fetch-all.ps1)' }

Write-Host ''
Write-Host '--- Working tree ---' -ForegroundColor Cyan
$porcelain = git -C $root status --porcelain 2>$null
if ($porcelain) {
    $porcelain
    $count = @($porcelain).Count
    Write-Status -Level WARNING -Message "$count uncommitted change(s) in working tree."
}
else {
    Write-Status -Level PASS -Message 'Working tree is clean.'
}

Write-Host ''
Write-Host '--- Last 5 commits ---' -ForegroundColor Cyan
git -C $root --no-pager log --oneline -5 2>$null
if ($LASTEXITCODE -ne 0) { Write-Host '(no commits yet)' }
Write-Host ''
