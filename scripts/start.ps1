param(
    [string]$BindAddress = "127.0.0.1",
    [ValidateRange(1, 65535)][int]$Port = 8000,
    [switch]$Reload
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$VenvPython = Join-Path $ProjectRoot ".venv\Scripts\python.exe"
if (-not (Test-Path -LiteralPath $VenvPython)) { throw "Chưa có môi trường Python. Chạy .\scripts\setup.ps1 trước." }
if (-not (Test-Path -LiteralPath (Join-Path $ProjectRoot "frontend\dist\index.html"))) {
    throw "Chưa build giao diện. Chạy npm.cmd run build trong frontend."
}
$ServerArguments = @("-m", "uvicorn", "app.main:app", "--app-dir", (Join-Path $ProjectRoot "backend"), "--host", $BindAddress, "--port", "$Port")
if ($Reload) { $ServerArguments += "--reload" }
Push-Location -LiteralPath $ProjectRoot
try {
    & $VenvPython @ServerArguments
    if ($LASTEXITCODE -ne 0) { throw "Server đã dừng với mã lỗi $LASTEXITCODE" }
} finally { Pop-Location }
