<#
.SYNOPSIS
    Captures Logcat from the physical device into handoff/reports, with a
    filtered summary suitable for a validation report.
.DESCRIPTION
    Read-only with respect to the device. Never changes a device setting.
    Output is written to handoff\reports\ and is gitignored by default,
    because raw device logs can contain fragments of user data. The summary
    lines are what belong in a committed validation report.
.EXAMPLE
    .\scripts\device-logcat.ps1 -Seconds 20
    .\scripts\device-logcat.ps1 -Seconds 30 -MinLevel Error -PackageName com.example.hermes
.EXAMPLE
    .\scripts\device-logcat.ps1 -Live
#>

[CmdletBinding()]
param(
    [int]$Seconds = 20,
    [ValidateSet('Verbose','Debug','Info','Warn','Error','Fatal')]
    [string]$MinLevel = 'Info',
    [string]$PackageName = '',
    [string]$Serial = '',
    [string]$OutputPath = '',
    [switch]$Live,
    [switch]$CrashesOnly
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'lib\Common.ps1')

$root = Get-ProjectRoot

$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) { Write-Status -Level FAIL -Message 'adb not found on PATH.'; exit 1 }
$adbExe = $adb.Source

$lines = @(& $adbExe devices) 2>$null | Select-Object -Skip 1
$ready = @()
foreach ($l in $lines) {
    $c = @(($l -split '\s+') | Where-Object { $_ })
    if ($c.Count -ge 2 -and $c[1] -eq 'device') { $ready += $c[0] }
}
if ($ready.Count -eq 0) {
    Write-Status -Level FAIL -Message 'No authorised device (NOT_CONNECTED). Cannot capture logcat.'
    exit 1
}
$target = if ($Serial) { $Serial } else { $ready[0] }
Write-Status -Level PASS -Message "Using device: $target"

$levelChar = @{ Verbose='V'; Debug='D'; Info='I'; Warn='W'; Error='E'; Fatal='F' }[$MinLevel]

$args = @('-s', $target, 'logcat', '-v', 'time')
if ($CrashesOnly) {
    $args += @('-b', 'crash')
}
else {
    if ($PackageName) {
        # pid-scoped capture keeps unrelated system noise out of the report
        $pidLine = @(& $adbExe -s $target shell pidof $PackageName) 2>$null |
                   Where-Object { $_ -and $_.Trim() } | Select-Object -First 1
        if ($pidLine) {
            $appPid = ($pidLine.Trim() -split '\s+')[0]
            Write-Host "Scoping capture to $PackageName (pid $appPid)" -ForegroundColor DarkGray
            $args += '--pid=' + $appPid
        }
        else {
            Write-Host "Process for '$PackageName' is not running; capturing at $levelChar and above." -ForegroundColor DarkGray
        }
    }
    $args += "$levelChar`:*"
}

if (-not $OutputPath) {
    $dir = Join-Path $root 'handoff\reports'
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $OutputPath = Join-Path $dir ("logcat-$target-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.txt')
}

Write-Host ''
Write-Host '=== DEVICE LOGCAT ===' -ForegroundColor Cyan
Write-Host "Output  : $OutputPath"
Write-Host ''

if ($Live) {
    Write-Host 'Live capture. Press Ctrl+C to stop.' -ForegroundColor Yellow
    & $adbExe @args 2>&1 | Tee-Object -FilePath $OutputPath
    exit 0
}

Write-Host "Capturing for $Seconds second(s)..." -ForegroundColor Cyan
$job = Start-Job -ScriptBlock { param($e, $a) & $e @a 2>&1 } -ArgumentList $adbExe, $args
Start-Sleep -Seconds $Seconds
Stop-Job $job -ErrorAction SilentlyContinue | Out-Null
$content = @(Receive-Job $job -ErrorAction SilentlyContinue)
Remove-Job $job -Force -ErrorAction SilentlyContinue

$content | Out-File -FilePath $OutputPath -Encoding utf8

# `adb logcat -v time` renders levels as "I/Tag", "E/Tag" - a letter followed
# by a slash, NOT a letter between spaces. Matching the wrong form silently
# reports every error as zero, which defeats the purpose of this summary.
$errs  = @($content | Where-Object { $_ -match '\sE/' -or $_ -match '\sF/' })
$crash = @($content | Where-Object { $_ -match 'FATAL EXCEPTION|ANR in |Force finishing activity.*has died' })

Write-Host ''
Write-Status -Level PASS -Message "Captured $($content.Count) line(s)"
Write-Host ''
Write-Host '--- SUMMARY (copy these lines into the validation report) ---' -ForegroundColor Cyan
Write-Host ("  total lines : {0}" -f $content.Count)
Write-Host ("  error lines : {0}" -f $errs.Count)
Write-Host ("  crashes/ANR : {0}" -f $crash.Count)

if ($crash.Count -gt 0) {
    Write-Host ''
    Write-Host '--- CRASHES / ANR (first 20) ---' -ForegroundColor Red
    $crash | Select-Object -First 20 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
}
if ($errs.Count -gt 0) {
    Write-Host ''
    Write-Host '--- ERRORS (first 25) ---' -ForegroundColor Yellow
    $errs | Select-Object -First 25 | ForEach-Object { Write-Host "  $_" -ForegroundColor Yellow }
}
Write-Host ''
Write-Host "Raw log: $OutputPath (gitignored - review before committing anything from it)" -ForegroundColor DarkGray
Write-Host ''
exit 0
