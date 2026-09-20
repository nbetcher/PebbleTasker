[CmdletBinding()]
param(
    [ValidateSet('Plugin', 'BridgeCore', 'Both', 'WatchBridge', 'WatchJvm', 'WatchUi')]
    [string]$Suite = 'Both',
    [string]$NeonGridDir = $env:NEON_GRID_ANDROID_DIR,
    [string]$AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$Jdk = 'C:/Program Files/Android/Android Studio/jbr',
    [string]$Java17Toolchain = $env:PEBBLE_JAVA17_HOME,
    [string[]]$Tests = @()
)
$ErrorActionPreference = 'Stop'
$plugin = Split-Path -Parent $PSScriptRoot
$workspace = Split-Path -Parent $plugin
$watch = Join-Path $workspace 'Watch App'
$oldJava = $env:JAVA_HOME
$oldSdk = $env:ANDROID_HOME
$oldPath = $env:PATH

function Invoke-Verification([string]$Project, [string]$Wrapper, [string]$Task, [string[]]$Extra) {
    $gradleArgs = @('-p', $Project, $Task, '--no-daemon', '--console=plain', '--max-workers=2') + $Extra
    foreach ($filter in $Tests) { $gradleArgs += @('--tests', $filter) }
    & $Wrapper @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Verification failed ($LASTEXITCODE): $Project $Task" }
    if ($Task -match '(^|:)(testDebugUnitTest|jvmTest)$') {
        $taskParts = $Task.TrimStart(':').Split(':')
        $reportRoot = $Project
        if ($taskParts.Count -gt 1) { $reportRoot = Join-Path $Project $taskParts[0] }
        $reportRoot = Join-Path $reportRoot "build/test-results/$($taskParts[-1])"
        $xmlFiles = @(Get-ChildItem -LiteralPath $reportRoot -Filter 'TEST-*.xml')
        if (!$xmlFiles.Count) { throw "No JUnit XML results found: $reportRoot" }
        $total = 0; $failed = 0; $skipped = 0
        foreach ($xmlFile in $xmlFiles) {
            [xml]$result = Get-Content -LiteralPath $xmlFile.FullName -Raw
            $total += [int]$result.testsuite.tests
            $failed += [int]$result.testsuite.failures + [int]$result.testsuite.errors
            $skipped += [int]$result.testsuite.skipped
        }
        Write-Host "JUnit: $total tests, $failed failures/errors, $skipped skipped. Results: $reportRoot"
        if (!$total -or $failed) { throw 'Verification did not produce a successful nonempty test run.' }
    }
}

try {
    if (!(Test-Path -LiteralPath "$Jdk/bin/java.exe")) { throw "JDK not found: $Jdk" }
    if (!(Test-Path -LiteralPath "$AndroidSdk/platforms")) { throw "Android SDK not found: $AndroidSdk" }
    $env:JAVA_HOME = $Jdk
    $env:ANDROID_HOME = $AndroidSdk
    # Quoted CUDA entries in this workstation's inherited PATH become embedded quotes in AGP's
    # java.library.path argument and split the Gradle test worker command at Files\NVIDIA.
    # Change only this process, and use a fresh daemon so it cannot retain the old native path.
    $env:PATH = $oldPath.Replace('"', '')
    if ($Suite -in @('Plugin', 'Both')) {
        if (!$NeonGridDir) { $NeonGridDir = Join-Path $workspace '../NeonGrid/android' }
        if (!(Test-Path -LiteralPath "$NeonGridDir/theme/build.gradle.kts")) {
            throw 'Real NeonGrid theme checkout required. Clone https://github.com/nbetcher/neon_grid_theme and pass -NeonGridDir <checkout>/android.'
        }
        $NeonGridDir = (Resolve-Path -LiteralPath $NeonGridDir).Path
        Invoke-Verification $plugin "$plugin/gradlew.bat" ':app:testDebugUnitTest' @("-PneonGridDir=$NeonGridDir", '-PLOCAL_RELEASE_BUILD=true')
    }
    if ($Suite -in @('BridgeCore', 'Both')) {
        Invoke-Verification "$watch/tools/bridge-core-verification" "$plugin/gradlew.bat" 'testDebugUnitTest' @()
    }
    if ($Suite -in @('WatchBridge', 'WatchJvm', 'WatchUi')) {
        # The full root also configures composeApp, whose build script calls Unix `which`.
        if (Test-Path -LiteralPath 'C:/Program Files/Git/usr/bin/which.exe') {
            $env:PATH = 'C:/Program Files/Git/usr/bin;' + $env:PATH
        }
        $watchArgs = @('--no-configuration-cache')
        if ($Java17Toolchain) { $watchArgs += "-Porg.gradle.java.installations.paths=$Java17Toolchain" }
        $watchTask = switch ($Suite) {
            'WatchBridge' { ':tasker-bridge:testDebugUnitTest' }
            'WatchJvm' { ':libpebble3:jvmTest' }
            'WatchUi' { ':composeApp:compileAndroidMain' }
        }
        if ($Suite -eq 'WatchUi' -and $Tests.Count) { throw '-Tests is only valid for test suites.' }
        Invoke-Verification $watch "$watch/gradlew.bat" $watchTask $watchArgs
    }
}
finally {
    $env:JAVA_HOME = $oldJava
    $env:ANDROID_HOME = $oldSdk
    $env:PATH = $oldPath
}
