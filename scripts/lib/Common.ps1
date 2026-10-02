<#
.SYNOPSIS
    Shared helpers for all repository scripts.
.DESCRIPTION
    Resolves the project root from the script location (never hardcodes user paths),
    locates the git executable, and provides consistent PASS/FAIL/WARNING output.
#>

Set-StrictMode -Version Latest

# Windows PowerShell 5.1 promotes native-command stderr (for example `git` writing
# "fatal: ..." to its error stream) into a terminating error record when scripts run
# in this environment. Most of the git probes in these scripts are *expected* to
# fail harmlessly (no remote yet, no commits yet, no upstream yet), so native
# stderr must not abort the script.
$ErrorActionPreference = 'Continue'

function Get-ProjectRoot {
    <#
        Walks upward from the calling script's directory until a directory
        containing a ".git" folder is found. Falls back to the current directory.
    #>
    $dir = Split-Path -Parent $PSCommandPath
    while ($dir) {
        if (Test-Path (Join-Path $dir '.git')) { return $dir }
        $parent = Split-Path -Parent $dir
        if ($parent -eq $dir) { break }
        $dir = $parent
    }
    if (Get-Location) { return (Get-Location).Path }
}

function Get-GitExe {
    $git = Get-Command git -ErrorAction SilentlyContinue
    if (-not $git) {
        Write-Status -Level FAIL -Message 'git executable not found on PATH.'
        exit 1
    }
    return $git.Source
}

function Write-Status {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('PASS', 'FAIL', 'WARNING', 'INFO')]
        [string]$Level,
        [Parameter(Mandatory = $true)][string]$Message
    )
    $color = switch ($Level) {
        'PASS'    { 'Green' }
        'FAIL'    { 'Red' }
        'WARNING' { 'Yellow' }
        default   { 'Gray' }
    }
    Write-Host ("[{0}] {1}" -f $Level.PadRight(7), $Message) -ForegroundColor $color
}

function Invoke-Git {
    <#
        Runs git inside the project root and streams output.
        Returns the git exit code via -PassThru handling below.
    #>
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )
    $git = Get-GitExe
    $root = Get-ProjectRoot
    Push-Location $root
    try {
        & $git @Arguments
        return $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
}

function Assert-Repository {
    $root = Get-ProjectRoot
    if (-not (Test-Path (Join-Path $root '.git'))) {
        Write-Status -Level FAIL -Message "Not inside a Git repository (looked in: $root)"
        exit 1
    }
    return $root
}

function Test-HasRemoteOrigin {
    $root = Get-ProjectRoot
    $remote = git -C $root remote get-url origin 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $remote) { return $false }
    return $true
}
