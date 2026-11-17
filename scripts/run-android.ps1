param([string]$Serial)
. (Join-Path $PSScriptRoot 'common.ps1')
$ProjectRoot = Initialize-KotlinEnvironment
$AdbPath = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $AdbPath)) { throw 'Khong tim thay adb. Cai Android SDK Platform-Tools.' }
$AdbArguments = @()
if ($Serial) { $AdbArguments = @('-s', $Serial) }
Invoke-Gradle $ProjectRoot @(':app:assembleDebug')
$DeviceDeadline = (Get-Date).AddSeconds(120)
Write-Host 'Dang cho Android khoi dong...'
do {
    $BootState = (& $AdbPath @AdbArguments shell getprop sys.boot_completed 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Chua ket noi thiet bi. Mo emulator hoac ket noi dien thoai qua USB.' }
    if ($BootState -eq '1') { break }
    if ((Get-Date) -ge $DeviceDeadline) { throw 'Android chua khoi dong xong sau 120 giay. Hay thu lai khi thay man hinh chinh.' }
    Start-Sleep -Seconds 2
} while ($true)
& $AdbPath @AdbArguments reverse tcp:8000 tcp:8000
if ($LASTEXITCODE -ne 0) { throw 'Chua ket noi thiet bi. Mo emulator hoac ket noi dien thoai qua USB.' }
& $AdbPath @AdbArguments install -r (Join-Path $ProjectRoot 'app\build\outputs\apk\debug\app-debug.apk')
if ($LASTEXITCODE -ne 0) { throw 'Khong cai duoc APK.' }
& $AdbPath @AdbArguments shell am start -n 'vn.lienket.app/.MainActivity'
if ($LASTEXITCODE -ne 0) { throw 'Khong mo duoc app.' }
