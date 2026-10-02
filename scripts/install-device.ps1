<#
.SYNOPSIS
    Installs an APK onto a connected physical Android device.
.DESCRIPTION
    Package/application ID is NOT hardcoded; pass it with -PackageName.
    The APK path is resolved from the Gradle build output.
.EXAMPLE
    .\scripts\install-device.ps1 -PackageName com.example.hermes
    .\scripts\install-device.ps1 -PackageName com.example.hermes -ApkPath 'C:\build\app-debug.apk'
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$PackageName,

    [string]$ApkPath = '',

    [ValidateSet('debug', 'release')]
    [string]$Variant = 'debug',

    [string]$Serial = '',

    [switch]$UninstallFirst
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'lib\Common.ps1')

$root = Get-ProjectRoot
$adb = (Get-Command adb -ErrorAction SilentlyContinue)
if (-not $adb) { Write-Status -Level FAIL -Message 'adb not found on PATH.'; exit 1 }
$adbExe = $adb.Source

Write-Host ''
Write-Host '=== INSTALL ON DEVICE ===' -ForegroundColor Cyan
Write-Host "Package : $PackageName"
Write-Host ''

# Resolve device
$devices = @(& $adbExe devices) | Select-Object -Skip 1
$ready = @()
foreach ($d in $devices) {
    $c = @(($d -split '\s+') | Where-Object { $_ })
    if ($c.Count -ge 2 -and $c[1] -eq 'device') { $ready += $c[0] }
}
if ($ready.Count -eq 0) {
    Write-Status -Level FAIL -Message 'No authorized device connected (NOT_CONNECTED).'
    Write-Host 'Connect the phone via USB-C, enable USB debugging, and accept the RSA prompt.'
    exit 1
}
$target = if ($Serial) { $Serial } else { $ready[0] }
if ($Serial -and ($ready -notcontains $Serial)) {
    Write-Status -Level FAIL -Message "Requested device '$Serial' is not connected/authorized."
    exit 1
}
Write-Status -Level PASS -Message "Using device: $target"

# --- resolve apk ---
if (-not $ApkPath) {
    $dir = Join-Path $root "app\build\outputs\apk\$Variant"
    if (Test-Path $dir) {
        $apk = Get-ChildItem $dir -Recurse -Filter *.apk -ErrorAction SilentlyContinue |
               Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($apk) { $ApkPath = $apk.FullName }
    }
}
if (-not $ApkPath -or -not (Test-Path $ApkPath)) {
    Write-Status -Level FAIL -Message 'APK not found. Run .\scripts\build.ps1 first, or pass -ApkPath explicitly.'
    exit 1
}
Write-Status -Level PASS -Message "APK: $ApkPath"

# --- install ---
if ($UninstallFirst) {
    Write-Host "Uninstalling existing $PackageName ..." -ForegroundColor DarkGray
    & $adbExe -s $target uninstall $PackageName 2>&1 | Out-Null
}

Write-Host 'Installing ...' -ForegroundColor Cyan
$out = & $adbExe -s $target install -r $ApkPath 2>&1
$code = $LASTEXITCODE
$out | Write-Host

if ($code -eq 0) {
    Write-Status -Level PASS -Message "Installed $PackageName on $target."
    Write-Host ''
    Write-Host 'Next: .\scripts\launch-device.ps1 -PackageName ' $PackageName
    exit 0
}

Write-Status -Level FAIL -Message 'Install failed.'
Write-Host 'Common causes:'
Write-Host '  - INSTALL_FAILED_UPDATE_INCOMPATIBLE : uninstall the old build first (-UninstallFirst)'
Write-Host '  - INSTALL_FAILED_UPDATE_INCOMPATIBLE : signature mismatch, uninstall the old build'
Write-Host '  - INSTALL_FAILED_INSUFFICIENT_STORAGE'
Write-Host '  - INSTALL_FAILED_VERIFICATION_FAILURE  : device blocked the install source'
Write-Host '  - device shows "unauthorized"           : accept the USB debugging RSA prompt'
exit 1
