$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$buildScript = Join-Path $scriptDir 'build-v54.ps1'

& $buildScript `
    -BuildCode 147 `
    -OutputVersion '1.3.11' `
    -OutputTag 'ExpertRAW-SDHMS-WebDAV-AudioEraser-NativeExitTrace' `
    -BuildFlavor 'expert-raw-sdhms-v36-webdav-audio-eraser-native-exit-trace-7.4.12.3'

if ($LASTEXITCODE -ne 0) {
    throw "v1.3.11 build failed with exit code $LASTEXITCODE"
}
