<#
.SYNOPSIS
    Detects and characterises the connected physical Android device.
.DESCRIPTION
    Read-only. Verifies USB-C connection, ADB authorisation, device ABI and
    Android API level. Never changes a device setting, never installs
    anything, never touches security settings.

    This is the physical-phone gate for the whole workflow. An emulator is
    NOT the primary test environment and an emulator-only result must not be
    reported as device validation.
.EXAMPLE
    .\scripts\device-check.ps1
.EXAMPLE
    .\scripts\device-check.ps1 -Json
#>

[CmdletBinding()]
param(
    [string]$Serial = '',
    [switch]$Json
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'lib\Common.ps1')

$results = New-Object System.Collections.Generic.List[object]

function Add-Result {
    param([string]$Name, [string]$Level, [string]$Value, [string]$Detail = '')
    $script:results.Add([pscustomobject]@{ Name = $Name; Level = $Level; Value = $Value; Detail = $Detail }) | Out-Null
    if (-not $Json) {
        $color = switch ($Level) { 'PASS' { 'Green' } 'FAIL' { 'Red' } default { 'Yellow' } }
        Write-Host ("[{0}] {1,-22} {2}" -f $Level.PadRight(4), $Name.PadRight(22), $Value) -ForegroundColor $color
        if ($Detail) { Write-Host "       $Detail" -ForegroundColor DarkGray }
    }
}

if (-not $Json) {
    Write-Host ''
    Write-Host '=== PHYSICAL DEVICE CHECK ===' -ForegroundColor Cyan
    Write-Host ''
}

# ---------- adb ----------
$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) {
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $null }
    $cand = if ($sdk) { Join-Path $sdk 'platform-tools\adb.exe' } else { $null }
    if ($cand -and (Test-Path $cand)) { $adbExe = $cand } else { $adbExe = $null }
}
else { $adbExe = $adb.Source }

if (-not $adbExe) {
    Add-Result -Name 'adb' -Level 'FAIL' -Value 'not found' -Detail 'Install SDK Platform-Tools.'
    if ($Json) { $results | ConvertTo-Json -Depth 3 }
    Write-Host 'Physical device validation is BLOCKED: adb unavailable.' -ForegroundColor Red
    exit 1
}
Add-Result -Name 'adb' -Level 'PASS' -Value $adbExe

# ---------- device list ----------
$lines = @(& $adbExe devices) 2>$null | Select-Object -Skip 1
$ready = @(); $unauthorized = @(); $offline = @()
foreach ($line in $lines) {
    $c = @(($line -split '\s+') | Where-Object { $_ })
    if ($c.Count -ge 2) {
        switch ($c[1]) {
            'device'          { $ready += $c[0] }
            'unauthorized'    { $unauthorized += $c[0] }
            'offline'         { $offline += $c[0] }
        }
    }
}

if ($ready.Count -eq 0) {
    if ($unauthorized.Count -gt 0) {
        Add-Result -Name 'Device authorised' -Level 'FAIL' -Value 'unauthorized' -Detail 'Tap "Always allow" then "Allow" in the USB debugging prompt on the phone.'
    }
    elseif ($offline.Count -gt 0) {
        Add-Result -Name 'Device authorised' -Level 'FAIL' -Value 'offline' -Detail 'Run: adb kill-server, then reconnect the cable.'
    }
    else {
        Add-Result -Name 'Device authorised' -Level 'FAIL' -Value 'NOT_CONNECTED' -Detail 'Connect the phone by USB-C (data-capable cable) and enable USB debugging.'
    }
    if ($Json) { $results | ConvertTo-Json -Depth 3 }
    Write-Host ''
    Write-Host 'Device validation is BLOCKED. Report NOT CONNECTED - never PASS.' -ForegroundColor Red
    exit 1
}

$target = if ($Serial) { $Serial } else { $ready[0] }
if ($Serial -and ($ready -notcontains $Serial)) {
    Add-Result -Name 'Requested serial' -Level 'FAIL' -Value $Serial -Detail ("Connected: " + ($ready -join ', '))
    if ($Json) { $results | ConvertTo-Json -Depth 3 }
    exit 1
}
Add-Result -Name 'Device authorised' -Level 'PASS' -Value $target
if ($ready.Count -gt 1) { Add-Result -Name 'Multiple devices' -Level 'WARN' -Value ($ready -join ', ') -Detail 'Use -Serial to disambiguate.' }

# ---------- properties ----------
$props = [ordered]@{
    'manufacturer' = 'ro.product.manufacturer'
    'brand'        = 'ro.product.brand'
    'model'        = 'ro.product.model'
    'android'      = 'ro.build.version.release'
    'api'          = 'ro.build.version.sdk'
    'abi'          = 'ro.product.cpu.abi'
    'abis'         = 'ro.product.cpu.abilist'
}
$values = [ordered]@{}
foreach ($k in $props.Keys) {
    $v = @(& $adbExe -s $target shell getprop $props[$k]) 2>$null |
         Where-Object { $_ -and $_.Trim() } | Select-Object -First 1
    if ($v) { $v = $v.Trim() } else { $v = '(unavailable)' }
    $values[$k] = $v
    $level = if ($v -eq '(unavailable)') { 'WARN' } else { 'PASS' }
    Add-Result -Name $k -Level $level -Value $v
}

# ---------- ABI / API sanity ----------
$api = 0
if ($values['api'] -match '^\d+$') { $api = [int]$values['api'] }
if ($api -gt 0 -and $api -lt 21) {
    Add-Result -Name 'API level' -Level 'WARN' -Value $api -Detail 'Below 21; most modern Android libraries require 21+.'
}
if ($values['abi'] -match 'arm64') {
    Add-Result -Name 'ABI note' -Level 'PASS' -Value '64-bit ARM' -Detail 'Any native library in the APK must include arm64-v8a.'
}
elseif ($values['abi'] -match 'armeabi-v7a|^armeabi$') {
    Add-Result -Name 'ABI note' -Level 'WARN' -Value '32-bit ARM only' -Detail 'arm64-v8a libraries will not load on this device.'
}

if ($Json) {
    $results | ConvertTo-Json -Depth 3
    exit 0
}

Write-Host ''
$fail = @($results | Where-Object { $_.Level -eq 'FAIL' }).Count
Write-Host ("Device check: {0} FAIL - physical device is READY for validation." -f $fail) -ForegroundColor $(if ($fail) { 'Red' } else { 'Green' })
Write-Host 'Connectivity confirmed. This is NOT the same as device validation:' -ForegroundColor DarkGray
Write-Host 'validation happens only once an APK exists to install and a human exercises it.' -ForegroundColor DarkGray
Write-Host ''
exit $fail
