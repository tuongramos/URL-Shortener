param([switch]$SkipTests)
. (Join-Path $PSScriptRoot 'common.ps1')
$ProjectRoot = Initialize-KotlinEnvironment
if (-not (Test-Path -LiteralPath (Join-Path $ProjectRoot '.env'))) {
    Copy-Item -LiteralPath (Join-Path $ProjectRoot '.env.example') -Destination (Join-Path $ProjectRoot '.env')
}
$Tasks = @(':app:assembleDebug', ':server:installDist')
if (-not $SkipTests) { $Tasks += @(':shared:test', ':server:test', ':app:testDebugUnitTest') }
Invoke-Gradle $ProjectRoot $Tasks
Write-Host 'APK: app\build\outputs\apk\debug\app-debug.apk' -ForegroundColor Green
