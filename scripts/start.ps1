param([string]$BindAddress = '127.0.0.1', [ValidateRange(1,65535)][int]$Port = 8000)
. (Join-Path $PSScriptRoot 'common.ps1')
$ProjectRoot = Initialize-KotlinEnvironment
$env:SERVER_HOST = $BindAddress
$env:SERVER_PORT = "$Port"
Invoke-Gradle $ProjectRoot @(':server:run')
