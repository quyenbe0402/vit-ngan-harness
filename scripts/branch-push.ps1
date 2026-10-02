<#
.SYNOPSIS
    Pushes a Cline or Claude branch to origin, with safety guarantees.
.DESCRIPTION
    SAFETY RULES ENFORCED IN CODE (not just convention):
      * Refuses to push to 'main' or 'develop' - those are controlled by the
        user, not by an agent.
      * Never uses --force or --force-with-lease under any flag combination.
      * Never runs reset --hard, checkout -f, or any destructive command.
      * Never writes or reads a credential; authentication is delegated to
        whatever the machine has configured.

    Creates the branch on the remote if it does not exist there yet.
.EXAMPLE
    .\scripts\branch-push.ps1 -Branch cline/M0-001-fix-build
    .\scripts\branch-push.ps1 -Branch cline/M0-001-fix-build -DryRun
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Branch,
    [string]$Remote = 'origin',
    [switch]$DryRun
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'lib\Common.ps1')

$root = Assert-Repository

# ---- hard safety guards ----
if ($Branch -notmatch '^(claude|cline)/[A-Za-z0-9._-]+$') {
    Write-Status -Level FAIL -Message "Branch '$Branch' does not match an agent branch pattern."
    Write-Host "Expected: claude/<task-id>-<short-name>  or  cline/<task-id>-<short-name>"
    exit 2
}
if ($Branch -match '^(main|master|develop)$') {
    Write-Status -Level FAIL -Message "Refusing to push to protected branch '$Branch'."
    Write-Host 'Agents do not push to main or develop. Integration into those branches'
    Write-Host 'is a controlled operation performed by the user.'
    exit 2
}

git -C $root rev-parse --verify --quiet $Branch *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Status -Level FAIL -Message "Local branch '$Branch' does not exist. Create it first."
    exit 1
}

if (-not (Test-HasRemoteOrigin)) {
    Write-Status -Level FAIL -Message "No '$Remote' remote configured. Cannot push."
    Write-Host 'Owner action: create the GitHub repository, then'
    Write-Host '    git remote add origin https://github.com/<owner>/<repo>.git'
    Write-Host 'See docs/GITHUB_AUTH_SETUP.md'
    exit 1
}

$porcelain = @(git -C $root status --porcelain 2>$null)
if ($porcelain.Count -gt 0) {
    Write-Status -Level WARNING -Message "Working tree has $($porcelain.Count) uncommitted change(s); they will NOT be pushed."
}

# Detect whether the remote branch already exists.
$remoteExists = $false
git -C $root ls-remote --heads $Remote $Branch 2>$null | Out-String | ForEach-Object {
    if ($_.Trim()) { $remoteExists = $true }
}

$pushArgs = @('push')
if (-not $remoteExists) { $pushArgs += '--set-upstream' }
$pushArgs += @($Remote, $Branch)
if ($DryRun) { $pushArgs += '--dry-run' }

Write-Host ''
Write-Host '=== BRANCH PUSH ===' -ForegroundColor Cyan
Write-Host "Branch       : $Branch"
Write-Host "Remote       : $Remote"
Write-Host "Remote branch: $(if ($remoteExists) { 'exists' } else { 'new (will be created)' })"
Write-Host "Mode         : $(if ($DryRun) { 'DRY RUN - nothing is transferred' } else { 'live' })"
Write-Host "Force        : never (guaranteed by this script)"
Write-Host ''

Write-Host "git $($pushArgs -join ' ')" -ForegroundColor DarkGray
$code = Invoke-Git -Arguments $pushArgs

if ($code -ne 0) {
    Write-Status -Level FAIL -Message "Push failed (exit code $code)."
    Write-Host ''
    Write-Host 'Likely causes:' -ForegroundColor Yellow
    Write-Host '  - Remote repository does not exist, or the URL is wrong'
    Write-Host '  - No GitHub credentials for this account  -> docs/GITHUB_AUTH_SETUP.md'
    Write-Host '  - Branch protection rules on the remote'
    Write-Host '  - No credentials available in this shell (see GITHUB_AUTH_SETUP.md on the'
    Write-Host '    credential-helper override, which depends on an environment variable)'
    exit $code
}

if ($DryRun) {
    Write-Status -Level PASS -Message "Dry run OK - push authentication works for '$Branch'. Nothing was transferred."
}
else {
    Write-Status -Level PASS -Message "Pushed '$Branch' to $Remote."
    Write-Host ''
    Write-Host 'Next: open a pull request to integrate this branch.'
    Write-Host "  https://github.com/<owner>/<repo>/compare/$Branch"
}
Write-Host ''
exit 0
