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
        Runs git inside the project root.

        Git's own output is written straight to the host rather than to the
        success stream. This matters: if it went to the success stream, a
        caller doing `$code = Invoke-Git ...` would receive an array containing
        both git's messages AND the exit code, and the comparison `$code -ne 0`
        would then be true even on a fully successful command. Only the exit
        code is returned.
    #>
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )
    $git = Get-GitExe
    $root = Get-ProjectRoot
    Push-Location $root
    try {
        # Git writes progress ("Switched to a new branch", "Everything up-to-date")
        # to stderr. In PowerShell 5.1 that becomes an error record and, with
        # StrictMode active, prints an alarming red block for a *successful*
        # command. The temporary preference silences that without hiding real
        # failures: the exit code is still captured and checked by every caller.
        $prev = $ErrorActionPreference
        $ErrorActionPreference = 'SilentlyContinue'
        try {
            & $git @Arguments 2>&1 | Out-Host
            $code = [int]$LASTEXITCODE
        }
        finally {
            $ErrorActionPreference = $prev
        }
        return $code
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
