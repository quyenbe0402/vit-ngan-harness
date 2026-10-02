<#
.SYNOPSIS
    Collects Logcat from a connected physical Android device and saves it to handoff/reports.
.DESCRIPTION
    Can capture either a timed dump (-DurationSeconds) or a live stream (-Live, Ctrl+C to stop).
    Errors, warnings and crashes are highlighted in the console summary.
.EXAMPLE
    .\scripts\collect-logcat.ps1 -DurationSeconds 30
    .\scripts\collect-logcat.ps1 -Filter 'AndroidRuntime:E Hermes:*' -DurationSeconds 20
    .\scripts\collect-logcat.ps1 -Live
#>

[CmdletBinding()]
param(
    [string]$Serial = '',
    [int]$DurationSeconds = 20,
    [string]$Filter = '',
    [int]$MinLevel = 3,          # 2=Verbose 3=Debug 4=Info 5=Warn 6=Error
    [switch]$Live,
    [string]$OutputPath = '',
    [switch]$ShowCrashesOnly
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'lib\Common.ps1')

$root = Get-ProjectRoot
$adb = (Get-Command adb -ErrorAction SilentlyContinue)
if (-not $adb) { Write-Status -Level FAIL -Message 'adb not found on PATH.'; exit 1 }
$adbExe = $adb.Source

Write-Host ''
Write-Host '=== COLLECT LOGCAT ===' -ForegroundColor Cyan

$devices = @(& $adbExe devices) | Select-Object -Skip 1
$ready = @()
foreach ($d in $devices) {
    $c = @(($d -split '\s+') | Where-Object { $_ })
    if ($c.Count -ge 2 -and $c[1] -eq 'device') { $ready += $c[0] }
}
if ($ready.Count -eq 0) {
    Write-Status -Level FAIL -Message 'No authorized device connected (NOT_CONNECTED).'
    exit 1
}
$target = if ($Serial) { $Serial } else { $ready[0] }
Write-Status -Level PASS -Message "Using device: $target"

if (-not $OutputPath) {
    $reportDir = Join-Path $root 'handoff\reports'
    if (-not (Test-Path $reportDir)) { New-Item -ItemType Directory -Path $reportDir -Force | Out-Null }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $OutputPath = Join-Path $reportDir "logcat-$target-$stamp.txt"
}
Write-Host "Output file: $OutputPath" -ForegroundColor DarkGray
Write-Host ''

$levelChar = @('V','D','I','W','E','F')[[Math]::Min([Math]::Max($MinLevel,2),6)-2]

if ($Filter) {
    $args = @('-s', $target, 'logcat', "-v", "time", "-s", $Filter)
}
elseif ($ShowCrashesOnly) {
    $args = @('-s', $target, 'logcat', '-v', 'time', "-b", 'crash', "$levelChar`:*")
}
else {
    $args = @('-s', $target, 'logcat', '-v', 'time', "$levelChar`:*")
}

if ($Live) {
    Write-Host 'Live capture. Press Ctrl+C to stop.' -ForegroundColor Yellow
    & $adbExe @args 2>&1 | Tee-Object -FilePath $OutputPath
    Write-Host "Saved to $OutputPath"
    exit 0
}

Write-Host "Capturing for $DurationSeconds second(s)..." -ForegroundColor Cyan
$job = Start-Job -ScriptBlock {
    param($exe, $a)
    & $exe @a 2>&1
} -ArgumentList $adbExe, $args

Start-Sleep -Seconds $DurationSeconds
Stop-Job $job -ErrorAction SilentlyContinue | Out-Null
$content = Receive-Job $job -ErrorAction SilentlyContinue
Remove-Job $job -Force -ErrorAction SilentlyContinue

if (-not $content) { $content = @() }
$content | Out-File -FilePath $OutputPath -Encoding utf8

Write-Host ''
Write-Status -Level PASS -Message "Captured $($content.Count) line(s) -> $OutputPath"

# ---- summary ----
$errs   = @($content | Where-Object { $_ -match '\sE\s' -or $_ -match '^\s*E/' })
$crash  = @($content | Where-Object { $_ -match 'FATAL EXCEPTION|AndroidRuntime.*Process.*died|ANR in' })

Write-Host ''
Write-Host '--- Summary ---' -ForegroundColor Cyan
Write-Host ("  Total lines : {0}" -f $content.Count)
Write-Host ("  Error lines : {0}" -f $errs.Count) -ForegroundColor $(if ($errs.Count) { 'Yellow' } else { 'Gray' })
Write-Host ("  Crashes/ANR : {0}" -f $crash.Count) -ForegroundColor $(if ($crash.Count) { 'Red' } else { 'Gray' })

if ($crash.Count -gt 0) {
    Write-Host ''
    Write-Host '--- Crashes / ANR (first 20) ---' -ForegroundColor Red
    $crash | Select-Object -First 20 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
}
if ($errs.Count -gt 0) {
    Write-Host ''
    Write-Host '--- Error lines (first 30) ---' -ForegroundColor Yellow
    $errs | Select-Object -First 30 | ForEach-Object { Write-Host "  $_" -ForegroundColor Yellow }
}
Write-Host ''
exit 0
