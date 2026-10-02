<#
.SYNOPSIS
    Creates a new working branch following the project's naming convention.
.DESCRIPTION
    Naming convention:  <agent>/<task-id>-<short-name>
    Agents:  claude | cline
    Example: .\scripts\git-create-branch.ps1 -Agent claude -TaskId M0-001 -ShortName audit
    Optionally branches from a specified base and can check out a fetched remote branch.
.EXAMPLE
    .\scripts\git-create-branch.ps1 -Agent cline -TaskId M0-001 -ShortName fix-build -Base develop
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidateSet('claude', 'cline')]
    [string]$Agent,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^(M[0-9]+-[0-9]{3})$')]
    [string]$TaskId,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-z0-9]+(-[a-z0-9]+)*$')]
    [string]$ShortName,

    [string]$Base = 'develop',

    [switch]$NoCheckout
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'lib\Common.ps1')

$root = Assert-Repository
$branchName = "$Agent/$TaskId-$ShortName"

Write-Host ''
Write-Host '=== CREATE BRANCH ===' -ForegroundColor Cyan
Write-Host "Repository : $root"
Write-Host "New branch : $branchName"
Write-Host ''

git -C $root rev-parse --verify --quiet $branchName *> $null
if ($LASTEXITCODE -eq 0) {
    Write-Status -Level WARNING -Message "Local branch '$branchName' already exists."
    if ($NoCheckout) { exit 0 }
    $code = Invoke-Git -Arguments @('checkout', $branchName)
    exit $code
}

git -C $root rev-parse --verify --quiet "origin/$branchName" *> $null
if ($LASTEXITCODE -eq 0) {
    Write-Status -Level WARNING -Message "Remote branch 'origin/$branchName' already exists. Checking it out."
    if ($NoCheckout) { exit 0 }
    $code = Invoke-Git -Arguments @('checkout', '--track', "origin/$branchName")
    exit $code
}

# Resolve the base branch
$baseRef = $null
foreach ($candidate in @($Base, "origin/$Base")) {
    git -C $root rev-parse --verify --quiet $candidate *> $null
    if ($LASTEXITCODE -eq 0) { $baseRef = $candidate; break }
}

if (-not $baseRef) {
    Write-Status -Level FAIL -Message "Base branch '$Base' not found (locally or on origin)."
    Write-Host "Available remote branches:"
    git -C $root branch -r 2>$null | ForEach-Object { Write-Host "  $_" }
    exit 1
}

Write-Host "Base branch : $baseRef" -ForegroundColor Cyan

$code = Invoke-Git -Arguments @('checkout', '-b', $branchName, $baseRef)
if ($code -ne 0) {
    Write-Status -Level FAIL -Message 'Branch creation failed.'
    exit $code
}

if ($NoCheckout) {
    # undo the checkout side-effect by returning to the base
    Invoke-Git -Arguments @('checkout', $baseRef) | Out-Null
    Write-Status -Level PASS -Message "Branch '$branchName' created (not checked out)."
}
else {
    Write-Status -Level PASS -Message "Branch '$branchName' created and checked out."
}

Write-Host ''
Write-Host 'Next steps:'
Write-Host "  implement changes"
Write-Host "  .\scripts\git-push-branch.ps1 -Branch $branchName"
Write-Host ''
exit 0
