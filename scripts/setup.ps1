param(
    [string]$Python = "python",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot

function Invoke-Checked {
    param([string]$Executable, [string[]]$Arguments)
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Lệnh thất bại: $Executable $($Arguments -join ' ')" }
}

Push-Location -LiteralPath $ProjectRoot
try {
    if (-not (Test-Path -LiteralPath ".venv\Scripts\python.exe")) {
        Invoke-Checked $Python @("-m", "venv", ".venv")
    }
    $VenvPython = Join-Path $ProjectRoot ".venv\Scripts\python.exe"
    Invoke-Checked $VenvPython @("-m", "pip", "install", "-r", "backend/requirements-dev.txt")
    if (-not (Test-Path -LiteralPath ".env")) {
        Copy-Item -LiteralPath ".env.example" -Destination ".env"
    }
    Push-Location -LiteralPath "frontend"
    try {
        Invoke-Checked "npm.cmd" @("ci", "--no-audit", "--no-fund")
        Invoke-Checked "npm.cmd" @("run", "build")
        if (-not $SkipTests) { Invoke-Checked "npm.cmd" @("test") }
    } finally { Pop-Location }
    if (-not $SkipTests) { Invoke-Checked $VenvPython @("-m", "pytest") }
    Write-Host "Sẵn sàng. Chạy: .\scripts\start.ps1" -ForegroundColor Green
} finally { Pop-Location }
