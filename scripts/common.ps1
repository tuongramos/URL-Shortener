$ErrorActionPreference = 'Stop'

function Initialize-KotlinEnvironment {
    $ProjectRoot = Split-Path -Parent $PSScriptRoot
    $StudioJdk = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $StudioJdk 'bin\java.exe'))) {
        $env:JAVA_HOME = $StudioJdk
    }
    if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        throw 'Can JDK 17 tro len. Dat JAVA_HOME hoac cai Android Studio co kem JDK.'
    }
    $SdkPath = $env:ANDROID_HOME
    if (-not $SdkPath) { $SdkPath = $env:ANDROID_SDK_ROOT }
    if (-not $SdkPath) { $SdkPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    if (Test-Path -LiteralPath $SdkPath) {
        $env:ANDROID_HOME = $SdkPath
        $LocalProperties = Join-Path $ProjectRoot 'local.properties'
        if (-not (Test-Path -LiteralPath $LocalProperties)) {
            $SdkProperty = $SdkPath.Replace('\', '/').Replace(':', '\:')
            [System.IO.File]::WriteAllText($LocalProperties, "sdk.dir=$SdkProperty`n", [System.Text.UTF8Encoding]::new($false))
        }
    }
    return $ProjectRoot
}

function Invoke-Gradle {
    param([string]$ProjectRoot, [string[]]$GradleArguments)
    Push-Location -LiteralPath $ProjectRoot
    try {
        & (Join-Path $ProjectRoot 'gradlew.bat') @GradleArguments --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Gradle that bai voi ma $LASTEXITCODE" }
    } finally { Pop-Location }
}
