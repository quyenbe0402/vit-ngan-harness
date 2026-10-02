<#
.SYNOPSIS
    Runs the project's automated test tasks.
.DESCRIPTION
    Runs JVM unit tests by default. Android instrumentation tests require a
    connected device and are run via scripts\full-device-test.ps1.
    Reports NOT CONFIGURED when no Gradle test task exists yet.
.EXAMPLE
    .\scripts\test.ps1
    .\scripts\test.ps1 -Task testDebugUnitTest
#>

[CmdletBinding()]
param(
    [string]$Task = 'test',

    [ValidateSet('debug', 'release')]
    [string]$Variant = 'debug',

    [switch]$SkipLint
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'lib\Common.ps1')

$root = Get-ProjectRoot
$gradlew = Join-Path $root 'gradlew.bat'

Write-Host ''
Write-Host '=== TEST ===' -ForegroundColor Cyan
Write-Host "Project root : $root"
Write-Host "Test task    : $Task"
Write-Host ''

if (-not (Test-Path $gradlew)) {
    Write-Status -Level WARNING -Message 'TESTS NOT CONFIGURED: no gradlew.bat in the project root.'
    Write-Host 'No Android test source exists yet. This is expected at this stage.'
    Write-Host ''
    exit 3
}

$failed = 0

Push-Location $root
try {
    if (-not $SkipLint) {
        Write-Host '--- Lint ---' -ForegroundColor Cyan
        & $gradlew 'lint' 2>&1 | Select-Object -Last 40 | Write-Host
        if ($LASTEXITCODE -ne 0) {
            Write-Status -Level WARNING -Message "Lint reported problems (exit code $LASTEXITCODE)."
            $failed++
        }
        else { Write-Status -Level PASS -Message 'Lint passed.' }
        Write-Host ''
    }

    Write-Host "--- $Task ---" -ForegroundColor Cyan
    & $gradlew $Task 2>&1 | Select-Object -Last 60 | Write-Host
    $code = $LASTEXITCODE
}
finally { Pop-Location }

if ($code -eq 0) {
    Write-Status -Level PASS -Message "Tests passed ($Task)."
    if (-not $SkipLint) {
        $report = Join-Path $root "app\build\reports\tests\${Variant}UnitTest\index.html"
        if (Test-Path $report) { Write-Host "Report: $report" }
    }
}
else {
    Write-Status -Level FAIL -Message "Tests FAILED ($Task), exit code $code."
    $failed++
}
Write-Host ''
exit $(if ($failed -gt 0) { 1 } else { 0 })
