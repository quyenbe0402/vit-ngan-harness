<#
.SYNOPSIS
    Builds the Android project using the Gradle wrapper.
.DESCRIPTION
    Safe and project-relative. The Android product source does not exist yet,
    so this script reports NOT CONFIGURED instead of failing when no Gradle
    project is present. Never installs anything.
.EXAMPLE
    .\scripts\build.ps1
    .\scripts\build.ps1 -Variant debug -Task assembleDebug
#>

[CmdletBinding()]
param(
    [ValidateSet('debug', 'release')]
    [string]$Variant = 'debug',

    [string]$Task = '',

    [switch]$Clean
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'lib\Common.ps1')

$root = Get-ProjectRoot
$gradlew = Join-Path $root 'gradlew.bat'

Write-Host ''
Write-Host '=== BUILD ===' -ForegroundColor Cyan
Write-Host "Project root : $root"
Write-Host "Variant      : $Variant"
Write-Host ''

if (-not (Test-Path $gradlew)) {
    Write-Status -Level WARNING -Message 'BUILD NOT CONFIGURED: no gradlew.bat in the project root.'
    Write-Host 'The Android product project has not been created yet (this repository currently'
    Write-Host 'contains development infrastructure only). This is expected at this stage.'
    Write-Host 'When the Android project exists, re-run this script.'
    Write-Host ''
    exit 3
}

$tasks = @()
if ($Clean) { $tasks += 'clean' }
if ($Task) { $tasks += $Task }
else { $tasks += "assemble$($Variant.Substring(0,1).ToUpper() + $Variant.Substring(1))" }

$gradleArgs = @($tasks)

if ($Clean) { & $gradlew clean 2>&1 | Write-Host }

Write-Host "Running: .\gradlew.bat $($gradleArgs -join ' ')" -ForegroundColor DarkGray
Push-Location $root
try {
    & $gradlew @gradleArgs
    $code = $LASTEXITCODE
}
finally { Pop-Location }

if ($code -eq 0) {
    Write-Status -Level PASS -Message 'Build succeeded.'
    $apkDir = Join-Path $root 'app\build\outputs\apk'
    if (Test-Path $apkDir) {
        Write-Host 'APK artifacts:' -ForegroundColor Cyan
        Get-ChildItem $apkDir -Recurse -Filter *.apk -ErrorAction SilentlyContinue |
            ForEach-Object { Write-Host "  $($_.FullName)" }
    }
}
else {
    Write-Status -Level FAIL -Message "Build failed (exit code $code)."
}
Write-Host ''
exit $code
