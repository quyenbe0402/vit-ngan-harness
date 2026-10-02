<#
.SYNOPSIS
    Reports the state of every tool required by the development workflow.
.DESCRIPTION
    Diagnostic only. Does NOT install, upgrade or modify anything.
    Use -Json to emit machine-readable output.
.EXAMPLE
    .\scripts\doctor.ps1
#>

[CmdletBinding()]
param(
    [switch]$Json
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'lib\Common.ps1')

$results = New-Object System.Collections.Generic.List[object]

function Add-Check {
    param(
        [string]$Name,
        [string]$Level,     # PASS / FAIL / WARNING / INFO
        [string]$Value,
        [string]$Detail = ''
    )
    $script:results.Add([pscustomobject]@{
        Name = $Name; Level = $Level; Value = $Value; Detail = $Detail
    }) | Out-Null
    if (-not $Json) {
        $color = switch ($Level) { 'PASS' { 'Green' } 'FAIL' { 'Red' } default { 'Yellow' } }
        $line = ("[{0}] {1,-22} {2}" -f $Level.PadRight(7), $Name.PadRight(22), $Value)
        Write-Host $line -ForegroundColor $color
        if ($Detail) { Write-Host "        $Detail" -ForegroundColor DarkGray }
    }
}

if (-not $Json) {
    Write-Host ''
    Write-Host '=== DEVELOPMENT ENVIRONMENT DOCTOR ===' -ForegroundColor Cyan
    Write-Host ("Host: {0}   {1}" -f $env:COMPUTERNAME, $env:OS) -ForegroundColor DarkGray
    Write-Host ("PSVersion: {0}" -f $PSVersionTable.PSVersion) -ForegroundColor DarkGray
    Write-Host ''
}

# ---------- Git ----------
$git = Get-Command git -ErrorAction SilentlyContinue
if ($git) {
    Add-Check -Name 'Git' -Level 'PASS' -Value (git --version)
}
else {
    Add-Check -Name 'Git' -Level 'FAIL' -Value 'not found' -Detail 'Install Git for Windows (https://git-scm.com/download/win)'
}

# ---------- GitHub CLI ----------
$gh = Get-Command gh -ErrorAction SilentlyContinue
if ($gh) {
    Add-Check -Name 'GitHub CLI (gh)' -Level 'PASS' -Value ((gh --version) 2>$null | Select-Object -First 1)
}
else {
    Add-Check -Name 'GitHub CLI (gh)' -Level 'WARNING' -Value 'not installed' -Detail 'Optional. Needed for gh pr create. See docs/GITHUB_AUTH_SETUP.md'
}

# ---------- SSH ----------
$ssh = Get-Command ssh -ErrorAction SilentlyContinue
if ($ssh) {
    Add-Check -Name 'SSH client' -Level 'PASS' -Value ((ssh -V) 2>&1 | Select-Object -First 1)
}
else {
    Add-Check -Name 'SSH client' -Level 'WARNING' -Value 'not found' -Detail 'Needed only for SSH-based GitHub auth.'
}

# ---------- Java / JDK ----------
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
$javaVersion = $null
if ($javaCmd) {
    $javaVersion = (java -version) 2>&1 | Select-Object -First 1
}
elseif ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $javaVersion = (& (Join-Path $env:JAVA_HOME 'bin\java.exe') -version) 2>&1 | Select-Object -First 1
}
if ($javaVersion) {
    Add-Check -Name 'Java' -Level 'PASS' -Value ("$javaVersion  JAVA_HOME=$env:JAVA_HOME")
}
else {
    Add-Check -Name 'Java' -Level 'FAIL' -Value 'not found' -Detail 'A JDK (17 or newer) is required to build Android projects.'
}

# ---------- Gradle ----------
$gradle = Get-Command gradle -ErrorAction SilentlyContinue
$gradlew = Join-Path (Get-ProjectRoot) 'gradlew.bat'
if ($gradle) {
    Add-Check -Name 'Gradle' -Level 'PASS' -Value ((gradle --version) 2>$null | Select-Object -First 1)
}
elseif (Test-Path $gradlew) {
    Add-Check -Name 'Gradle' -Level 'PASS' -Value 'gradlew.bat wrapper present in project'
}
else {
    Add-Check -Name 'Gradle' -Level 'WARNING' -Value 'no standalone Gradle and no wrapper yet' -Detail 'The Android project will ship its own gradlew wrapper; standalone Gradle is not required.'
}
# ---------- Node / npm / Python ----------
$node = Get-Command node -ErrorAction SilentlyContinue
if ($node) { Add-Check -Name 'Node.js' -Level 'PASS' -Value (node --version) }
else { Add-Check -Name 'Node.js' -Level 'WARNING' -Value 'not found' -Detail 'Required for tooling/CI; not for the Android app itself.' }

$npm = Get-Command npm -ErrorAction SilentlyContinue
if ($npm) { Add-Check -Name 'npm' -Level 'PASS' -Value (npm --version) }
else { Add-Check -Name 'npm' -Level 'WARNING' -Value 'not found' -Detail 'Required for tooling/CI.' }

$py = Get-Command python -ErrorAction SilentlyContinue
if ($py) { Add-Check -Name 'Python' -Level 'PASS' -Value (python --version 2>&1) }
else { Add-Check -Name 'Python' -Level 'WARNING' -Value 'not found' -Detail 'Required for tooling/CI.' }

# ---------- ADB ----------
$haveAdb = $false
$adbPath = $null
$adb = Get-Command adb -ErrorAction SilentlyContinue
if ($adb) { $adbPath = $adb.Source; $haveAdb = $true }
elseif ($env:ANDROID_HOME) {
    $candidate = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
    if (Test-Path $candidate) { $adbPath = $candidate; $haveAdb = $true }
}
if ($haveAdb) {
    $adbVersion = (& $adbPath version) 2>$null | Select-Object -First 2
    Add-Check -Name 'ADB' -Level 'PASS' -Value ($adbVersion -join ' | ')
}
else {
    Add-Check -Name 'ADB' -Level 'FAIL' -Value 'not found' -Detail 'Install Android SDK Platform-Tools and add to PATH (or set ANDROID_HOME).'
}

# ---------- Android SDK ----------
if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) {
    $platforms = @(); $platDir = Join-Path $env:ANDROID_HOME 'platforms'
    if (Test-Path $platDir) { $platforms = Get-ChildItem $platDir -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name }
    $bt = @(); $btDir = Join-Path $env:ANDROID_HOME 'build-tools'
    if (Test-Path $btDir) { $bt = Get-ChildItem $btDir -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name }
    Add-Check -Name 'Android SDK' -Level 'PASS' -Value $env:ANDROID_HOME -Detail ("platforms: " + ($platforms -join ', ') + " | build-tools: " + ($bt -join ', '))
}
else {
    Add-Check -Name 'Android SDK' -Level 'FAIL' -Value 'ANDROID_HOME not set or missing' -Detail 'Install Android Studio or the command-line tools.'
}

# ---------- Android Studio ----------
$studioCandidates = @(
    'C:\Program Files\Android\Android Studio\bin\studio64.exe',
    "$env:LOCALAPPDATA\Programs\Android Studio\bin\studio64.exe"
)
$studio = $studioCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if ($studio) {
    Add-Check -Name 'Android Studio' -Level 'PASS' -Value ("$((Get-Item $studio).VersionInfo.ProductVersion)  ($studio)")
}
else {
    Add-Check -Name 'Android Studio' -Level 'WARNING' -Value 'not detected' -Detail 'Not required for command-line builds.'
}

# ---------- Connected devices ----------
if ($haveAdb) {
    $serials = @()
    $lines = @(& $adbPath devices) | Select-Object -Skip 1
    foreach ($line in $lines) {
        $cols = @(($line -split '\s+') | Where-Object { $_ })
        if ($cols.Count -ge 2 -and $cols[1] -eq 'device') { $serials += $cols[0] }
    }
    if ($serials.Count -gt 0) {
        Add-Check -Name 'Android device(s)' -Level 'PASS' -Value ($serials -join ', ')
    }
    else {
        Add-Check -Name 'Android device(s)' -Level 'WARNING' -Value 'NOT_CONNECTED' -Detail 'Connect by USB-C and enable USB debugging. See docs/ANDROID_DEVICE_WORKFLOW.md'
    }
}

# ---------- Git repository state ----------
$root = Get-ProjectRoot
if (Test-Path (Join-Path $root '.git')) {
    $branch = git -C $root rev-parse --abbrev-ref HEAD 2>$null
    $origin = git -C $root remote get-url origin 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $origin) {
        Add-Check -Name 'Git remote origin' -Level 'WARNING' -Value 'not configured' -Detail 'See docs/GITHUB_AUTH_SETUP.md'
    }
    else {
        Add-Check -Name 'Git remote origin' -Level 'PASS' -Value $origin
    }
    $userName = git -C $root config user.name 2>$null
    $userMail = git -C $root config user.email 2>$null
    if ($userName -and $userMail) {
        Add-Check -Name 'Git identity' -Level 'PASS' -Value "$userName <$userMail>"
    }
    else {
        Add-Check -Name 'Git identity' -Level 'FAIL' -Value 'user.name / user.email not set' -Detail 'Set with: git config user.name "Your Name" and git config user.email "you@example.com"'
    }
    Add-Check -Name 'Current branch' -Level 'INFO' -Value $branch
}

# ---------- Summary ----------
if ($Json) {
    $results | ConvertTo-Json -Depth 3
    exit 0
}

Write-Host ''
$fails = @($results | Where-Object { $_.Level -eq 'FAIL' }).Count
$warns = @($results | Where-Object { $_.Level -eq 'WARNING' }).Count
$sumColor = if ($fails -gt 0) { 'Red' } elseif ($warns -gt 0) { 'Yellow' } else { 'Green' }
Write-Host ("Summary: {0} FAIL, {1} WARNING" -f $fails, $warns) -ForegroundColor $sumColor
Write-Host 'This script installs nothing. Resolve each FAIL / WARNING manually.' -ForegroundColor DarkGray
Write-Host ''
exit 0

