<#
.SYNOPSIS
    Launches the application on a connected physical Android device.
.DESCRIPTION
    The package name is supplied by the caller (-PackageName) because the
    application ID does not exist yet. Optionally starts Logcat capture first
    so startup crashes are always captured.
.EXAMPLE
    .\scripts\launch-device.ps1 -PackageName com.example.hermes
    .\scripts\launch-device.ps1 -PackageName com.example.hermes -ClearLogcat
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$PackageName,

    [string]$ActivityName = '',

    [string]$Serial = '',

    [switch]$ClearLogcat,

    [switch]$ForceStop
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'lib\Common.ps1')

$adb = (Get-Command adb -ErrorAction SilentlyContinue)
if (-not $adb) { Write-Status -Level FAIL -Message 'adb not found on PATH.'; exit 1 }
$adbExe = $adb.Source

Write-Host ''
Write-Host '=== LAUNCH ON DEVICE ===' -ForegroundColor Cyan
Write-Host "Package : $PackageName"
Write-Host ''

$devices = @(& $adbExe devices) | Select-Object -Skip 1
$ready = @()
foreach ($d in $devices) {
    $c = @(($d -split '\s+') | Where-Object { $_ })
    if ($c.Count -ge 2 -and $c[1] -eq 'device') { $ready += $c[0] }
}
if ($ready.Count -eq 0) { Write-Status -Level FAIL -Message 'No authorized device connected (NOT_CONNECTED).'; exit 1 }
$target = if ($Serial) { $Serial } else { $ready[0] }
Write-Status -Level PASS -Message "Using device: $target"

# Verify the package is actually installed before launching.
$pmPath = (& $adbExe -s $target shell pm path $PackageName 2>$null) -join ''
if ($pmPath -notmatch '^package:') {
    Write-Status -Level FAIL -Message "Package '$PackageName' is not installed on the device."
    Write-Host 'Run .\scripts\install-device.ps1 -PackageName ' $PackageName
    exit 1
}
Write-Status -Level PASS -Message 'Package is installed.'

if ($ClearLogcat) {
    & $adbExe -s $target logcat -c 2>&1 | Out-Null
    Write-Host 'Logcat buffer cleared.' -ForegroundColor DarkGray
}

if ($ForceStop) {
    & $adbExe -s $target shell am force-stop $PackageName 2>&1 | Out-Null
    Write-Host 'Force-stopped previous instance.' -ForegroundColor DarkGray
}

Write-Host 'Launching ...' -ForegroundColor Cyan
if ($ActivityName) {
    $component = if ($ActivityName -like '*/*') { $ActivityName } else { "$PackageName/$ActivityName" }
    $out = & $adbExe -s $target shell am start -W -n $component 2>&1
}
else {
    # Resolve the launcher activity from the installed package.
    $resolved = (& $adbExe -s $target shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER $PackageName 2>$null) |
                Where-Object { $_ -match '/' } | Select-Object -Last 1
    if ($resolved) { $resolved = $resolved.Trim() }
    if (-not $resolved) {
        Write-Status -Level FAIL -Message "Could not resolve a launcher activity for '$PackageName'."
        Write-Host 'Pass -ActivityName explicitly (e.g. -ActivityName .MainActivity).'
        exit 1
    }
    Write-Host "Resolved launcher activity: $resolved" -ForegroundColor DarkGray
    $out = & $adbExe -s $target shell am start -W -n $resolved 2>&1
}

$out | Write-Host
$code = $LASTEXITCODE

if ($code -eq 0) { Write-Status -Level PASS -Message 'Launch command issued.' }
else { Write-Status -Level FAIL -Message 'Launch command failed.' }

Write-Host ''
Write-Host 'The app may have crashed immediately. Always follow with:'
Write-Host '  .\scripts\collect-logcat.ps1'
Write-Host ''
exit $code
