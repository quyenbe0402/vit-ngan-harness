<#
.SYNOPSIS
    End-to-end physical device validation: build, install, launch, collect logs, summarize.
.DESCRIPTION
    Runs the full device loop documented in docs/ANDROID_DEVICE_WORKFLOW.md.
    The application ID is required as a parameter because the Android product
    project does not exist yet. Reports NOT CONFIGURED / NOT_CONNECTED instead
    of fabricating results.
.EXAMPLE
    .\scripts\full-device-test.ps1 -PackageName com.example.hermes
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$PackageName,

    [ValidateSet('debug', 'release')]
    [string]$Variant = 'debug',

    [string]$Serial = '',

    [int]$LogSeconds = 20,

    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'lib\Common.ps1')

$root = Get-ProjectRoot
$results = New-Object System.Collections.Generic.List[object]

function Step {
    param([string]$Name, [int]$ExitCode, [string]$Note = '')
    $level = if ($ExitCode -eq 0) { 'PASS' } elseif ($ExitCode -eq 3) { 'WARNING' } else { 'FAIL' }
    $script:results.Add([pscustomobject]@{ Step = $Name; Level = $level; Exit = $ExitCode; Note = $Note }) | Out-Null
    Write-Host ("[{0}] {1}" -f $level.PadRight(7), $Name) -ForegroundColor (switch ($level) { 'PASS' { 'Green' } 'WARNING' { 'Yellow' } default { 'Red' } })
    if ($Note) { Write-Host "        $Note" -ForegroundColor DarkGray }
}

Write-Host ''
Write-Host '=== FULL DEVICE TEST ===' -ForegroundColor Cyan
Write-Host "Project root : $root"
Write-Host "Package      : $PackageName"
Write-Host "Variant      : $Variant"
Write-Host ''

$logReport = Join-Path $root ("handoff\reports\device-test-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + ".txt")
$log = New-Object System.Collections.Generic.List[string]
$log.Add("FULL DEVICE TEST REPORT  $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
$log.Add("Package: $PackageName   Variant: $Variant")
$log.Add('')

# ---- 0. device presence ----
$adb = (Get-Command adb -ErrorAction SilentlyContinue)
if (-not $adb) { Step 'ADB available' 1 'adb not found on PATH'; }
else {
    $adbExe = $adb.Source
    $lines = @(& $adbExe devices) | Select-Object -Skip 1
    $ready = @()
    foreach ($d in $lines) { $c = @(($d -split '\s+') | Where-Object { $_ }); if ($c.Count -ge 2 -and $c[1] -eq 'device') { $ready += $c[0] } }
    if ($ready.Count -eq 0) { Step 'Device connected' 1 'NOT_CONNECTED' }
    else {
        $target = if ($Serial) { $Serial } else { $ready[0] }
        Step 'Device connected' 0 $target
        foreach ($prop in @('ro.product.manufacturer','ro.product.model','ro.build.version.release','ro.build.version.sdk','ro.product.cpu.abi')) {
            $v = (& $adbExe -s $target shell getprop $prop) 2>$null
            $log.Add("  $prop = $v")
            Write-Host "         $prop = $v" -ForegroundColor DarkGray
        }
    }
}

# ---- 1. build ----
if ($SkipBuild) { Step 'Build' 0 'skipped via -SkipBuild' }
else {
    & (Join-Path $PSScriptRoot 'build.ps1') -Variant $Variant
    Step 'Build' $LASTEXITCODE
}

# ---- 2. tests ----
& (Join-Path $PSScriptRoot 'test.ps1') -SkipLint
Step 'Unit tests' $LASTEXITCODE

# ---- 3. install ----
& (Join-Path $PSScriptRoot 'install-device.ps1') -PackageName $PackageName -Variant $Variant -UninstallFirst
$installCode = $LASTEXITCODE
Step 'Install' $installCode
if ($installCode -ne 0) {
    $log.Add('Install failed - aborting before launch.')
    $log | Out-File $logReport -Encoding utf8
    Write-Host ''
    Write-Host "Report written to: $logReport" -ForegroundColor Cyan
    exit 1
}

# ---- 4. launch ----
& (Join-Path $PSScriptRoot 'launch-device.ps1') -PackageName $PackageName -ClearLogcat -ForceStop
$launchCode = $LASTEXITCODE
Step 'Launch' $launchCode
$log.Add("Launch output: $launchCode")

# ---- 5. logcat ----
& (Join-Path $PSScriptRoot 'collect-logcat.ps1') -DurationSeconds $LogSeconds
$logCode = $LASTEXITCODE
Step 'Logcat collection' $logCode
$log.Add("Logcat exit: $logCode")

# ---- summary ----
$log.Add('')
$log.Add('SUMMARY')
foreach ($r in $results) { $log.Add("  [$($r.Level)] $($r.Step) (exit $($r.Exit)) $($r.Note)") }

$reportDir = Join-Path $root 'handoff\reports'
if (-not (Test-Path $reportDir)) { New-Item -ItemType Directory -Path $reportDir -Force | Out-Null }
$log | Out-File $logReport -Encoding utf8

Write-Host ''
Write-Host '--- FULL DEVICE TEST SUMMARY ---' -ForegroundColor Cyan
foreach ($r in $results) {
    $c = switch ($r.Level) { 'PASS' { 'Green' } 'WARNING' { 'Yellow' } default { 'Red' } }
    Write-Host ("  [{0}] {1} {2}" -f $r.Level.PadRight(7), $r.Step, $r.Note) -ForegroundColor $c
}
Write-Host ''
Write-Host "Report: $logReport"
Write-Host ''
Write-Host 'Note: script execution success is NOT proof the app behaves correctly.'
Write-Host 'A human must exercise the app on the device and record the result.'
Write-Host ''

if (@($results | Where-Object { $_.Level -eq 'FAIL' }).Count -gt 0) { exit 1 }
exit 0
