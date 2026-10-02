<#
.SYNOPSIS
    Pushes a branch to origin, optionally setting upstream tracking.
.DESCRIPTION
    Never stores or prints credentials. Authentication is delegated to whatever
    mechanism the machine has configured (Git Credential Manager, SSH, gh, ...).
    See docs/GITHUB_AUTH_SETUP.md.
.EXAMPLE
    .\scripts\git-push-branch.ps1 -Branch cline/M0-001-fix-build
    .\scripts\git-push-branch.ps1 -Branch claude/M0-001-audit -CreatePullRequest
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Branch,
    [string]$Remote = 'origin',
    [switch]$SetUpstream,
    [switch]$CreatePullRequest
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'lib\Common.ps1')

$root = Assert-Repository

Write-Host ''
Write-Host '=== PUSH BRANCH ===' -ForegroundColor Cyan
Write-Host "Repository : $root"
Write-Host "Branch     : $Branch"
Write-Host "Remote     : $Remote"
Write-Host ''

git -C $root rev-parse --verify --quiet $Branch *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Status -Level FAIL -Message "Local branch '$Branch' does not exist."
    exit 1
}

$porcelain = git -C $root status --porcelain 2>$null
if ($porcelain) {
    Write-Status -Level WARNING -Message "Working tree has $(@($porcelain).Count uncommitted change(s). They will NOT be pushed."
    Write-Host 'Commit them first, or stash them.'
}

$upstream = git -C $root rev-parse --abbrev-ref '@{u}' 2>$null
$needUpstream = $SetUpstream -or ($LASTEXITCODE -ne 0 -or -not $upstream)

$pushArgs = @('push')
if ($needUpstream) { $pushArgs += '--set-upstream' }
$pushArgs += @($Remote, $Branch)

Write-Host "Running: git $($pushArgs -join ' ')" -ForegroundColor DarkGray
$code = Invoke-Git -Arguments $pushArgs

if ($code -ne 0) {
    Write-Status -Level FAIL -Message "Push failed (exit code $code)."
    Write-Host ''
    Write-Host 'Most common causes:' -ForegroundColor Yellow
    Write-Host '  - No GitHub credentials configured  -> docs/GITHUB_AUTH_SETUP.md'
    Write-Host '  - Remote repository does not exist  -> create it on GitHub, then:'
    Write-Host '        git remote add origin <url>'
    Write-Host '  - Branch protection blocking the push'
    Write-Host '  - No commits on the branch yet'
    exit $code
}

Write-Status -Level PASS -Message "Branch '$Branch' pushed to $Remote."

if ($CreatePullRequest) {
    $gh = Get-Command gh -ErrorAction SilentlyContinue
    if (-not $gh) {
        Write-Status -Level WARNING -Message 'GitHub CLI (gh) not installed. Create the pull request in the web UI.'
        Write-Host "  https://github.com/<owner>/<repo>/compare/$Branch"
        exit 0
    }
    $ghExe = $gh.Source
    $prTitle = $env:PR_TITLE
    if (-not $prTitle) { $prTitle = "$Branch" }
    Write-Host ''
    Write-Host '--- gh pr create ---' -ForegroundColor Cyan
    Push-Location $root
    try {
        & $ghExe pr create --base develop --head $Branch --title $prTitle --body "See DEVELOPMENT_STATUS.md and handoff/reports/ for details."
        if ($LASTEXITCODE -eq 0) {
            Write-Status -Level PASS -Message 'Pull request created.'
        }
        else {
            Write-Status -Level WARNING -Message 'Pull request creation failed. Create it manually.'
        }
    }
    finally { Pop-Location }
}

Write-Host ''
exit 0
