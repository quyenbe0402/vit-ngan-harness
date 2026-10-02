<#
.SYNOPSIS
    Verifies the Android build toolchain: JDK, Gradle, Android SDK,
    platform-tools, build-tools and adb.
.DESCRIPTION
    Diagnostics only. Installs nothing and changes no Android SDK version.
    Exits 0 when every REQUIRED component is present, 1 otherwise.
    Emits PASS / WARN / FAIL lines only - never secrets.
.EXAMPLE
    .\scripts\android-check.ps1
.EXAMPLE
    .\scripts\android-check.ps1 -Json
#>

[CmdletBinding()]
param(
    [switch]$Json,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'lib\Common.ps1')

$root = Get-ProjectRoot
$results = New-Object System.Collections.Generic.List[object]

function Add-Result {
    param([string]$Name, [string]$Level, [string]$Value, [string]$Detail = '')
    $script:results.Add([pscustomobject]@{ Name = $Name; Level = $Level; Value = $Value; Detail = $Detail }) | Out-Null
    if (-not $Json -and -not $Quiet) {
        $color = switch ($Level) { 'PASS' { 'Green' } 'FAIL' { 'Red' } default { 'Yellow' } }
        Write-Host ("[{0}] {1,-20} {2}" -f $Level.PadRight(4), $Name.PadRight(20), $Value) -ForegroundColor $color
        if ($Detail) { Write-Host "       $Detail" -ForegroundColor DarkGray }
    }
}

if (-not $Json -and -not $Quiet) {
    Write-Host ''
    Write-Host '=== ANDROID ENVIRONMENT CHECK ===' -ForegroundColor Cyan
    Write-Host ''
}

# ---------- JDK ----------
$javaVersion = $null
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if ($javaCmd) { $javaVersion = (java -version) 2>&1 | Select-Object -First 1 }
elseif ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $javaVersion = (& (Join-Path $env:JAVA_HOME 'bin\java.exe') -version) 2>&1 | Select-Object -First 1
}
if ($javaVersion) {
    $onPath = if ($javaCmd) { 'on PATH' } else { 'via JAVA_HOME only' }
    Add-Result -Name 'JDK' -Level 'PASS' -Value "$javaVersion  ($onPath)"
    if (-not $javaCmd) {
        Add-Result -Name 'JAVA_HOME' -Level 'WARN' -Value $env:JAVA_HOME -Detail 'java is not on PATH; scripts fall back to JAVA_HOME. Optional to fix.'
    }
}
else {
    Add-Result -Name 'JDK' -Level 'FAIL' -Value 'not found' -Detail 'A JDK (17 or newer) is required. Set JAVA_HOME.'
}

# ---------- Android SDK ----------
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if ($sdk -and (Test-Path $sdk)) {
    Add-Result -Name 'Android SDK' -Level 'PASS' -Value $sdk
}
else {
    Add-Result -Name 'Android SDK' -Level 'FAIL' -Value 'not found' -Detail 'Set ANDROID_HOME or install Android Studio.'
    $sdk = $null
}


# ---------- platform-tools ----------
$adbExe = $null
if ($sdk) {
    $pt = Join-Path $sdk 'platform-tools\adb.exe'
    if (Test-Path $pt) { $adbExe = $pt }
}
if (-not $adbExe) {
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { $adbExe = $onPath.Source }
}
if ($adbExe) {
    $ptPresent = ($sdk) -and (Test-Path (Join-Path $sdk 'platform-tools'))
    Add-Result -Name 'platform-tools' -Level $(if ($ptPresent) { 'PASS' } else { 'WARN' }) -Value (Split-Path -Parent $adbExe) `
        -Detail $(if ($ptPresent) { '' } else { 'adb found outside ANDROID_HOME; set ANDROID_HOME for consistency.' })
}
else {
    Add-Result -Name 'platform-tools' -Level 'FAIL' -Value 'not found' -Detail 'Install Android SDK Platform-Tools.'
}

# ---------- adb ----------
if ($adbExe) {
    $av = @(& $adbExe version) 2>$null
    Add-Result -Name 'adb' -Level 'PASS' -Value (($av | Where-Object { $_ }) -join ' | ')
}
else {
    Add-Result -Name 'adb' -Level 'FAIL' -Value 'not found'
}

# ---------- build-tools ----------
$buildTools = @()
if ($sdk) {
    $btDir = Join-Path $sdk 'build-tools'
    if (Test-Path $btDir) {
        $buildTools = @(Get-ChildItem $btDir -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name)
    }
}
if ($buildTools.Count -gt 0) {
    Add-Result -Name 'build-tools' -Level 'PASS' -Value ($buildTools -join ', ')
}
else {
    Add-Result -Name 'build-tools' -Level 'WARN' -Value 'none found' -Detail 'Installed by Android Studio / sdkmanager. Needed once the project builds.'
}

# ---------- platforms ----------
$platforms = @()
if ($sdk) {
    $pDir = Join-Path $sdk 'platforms'
    if (Test-Path $pDir) {
        $platforms = @(Get-ChildItem $pDir -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name)
    }
}
if ($platforms.Count -gt 0) {
    Add-Result -Name 'platforms' -Level 'PASS' -Value ($platforms -join ', ')
}
else {
    Add-Result -Name 'platforms' -Level 'WARN' -Value 'none found' -Detail 'The project will pin its own compileSdk.'
}

# ---------- Gradle ----------
$gradlew = Join-Path $root 'gradlew.bat'
$gradle = Get-Command gradle -ErrorAction SilentlyContinue
if (Test-Path $gradlew) {
    Add-Result -Name 'Gradle' -Level 'PASS' -Value 'gradlew.bat wrapper in project'
}
elseif ($gradle) {
    Add-Result -Name 'Gradle' -Level 'PASS' -Value ((gradle --version) 2>$null | Select-Object -First 1)
}
else {
    Add-Result -Name 'Gradle' -Level 'WARN' -Value 'no wrapper, no standalone' -Detail 'The Android project will ship gradlew. Not a blocker.'
}

# ---------- Android Studio ----------
$studio = @(
    'C:\Program Files\Android\Android Studio\bin\studio64.exe',
    "$env:LOCALAPPDATA\Programs\Android Studio\bin\studio64.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if ($studio) {
    Add-Result -Name 'Android Studio' -Level 'PASS' -Value (Split-Path -Parent (Split-Path -Parent $studio))
}
else {
    Add-Result -Name 'Android Studio' -Level 'WARN' -Value 'not detected' -Detail 'Not required for command-line builds.'
}

# ---------- summary ----------
if ($Json) {
    $results | ConvertTo-Json -Depth 3
}
elseif (-not $Quiet) {
    $f = @($results | Where-Object { $_.Level -eq 'FAIL' }).Count
    $w = @($results | Where-Object { $_.Level -eq 'WARN' }).Count
    Write-Host ''
    Write-Host ("Android environment: {0} FAIL, {1} WARN" -f $f, $w) -ForegroundColor $(if ($f) { 'Red' } elseif ($w) { 'Yellow' } else { 'Green' })
    Write-Host 'Diagnostic only - nothing was installed or changed.' -ForegroundColor DarkGray
    Write-Host ''
}

exit $(if (@($results | Where-Object { $_.Level -eq 'FAIL' }).Count -gt 0) { 1 } else { 0 })
