$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$buildScript = Join-Path $scriptDir 'build-v54.ps1'

& $buildScript `
    -BuildCode 144 `
    -OutputVersion '1.3.8' `
    -OutputTag 'ExpertRAW-SDHMS-WebDAV-AudioEraser-S24MainFramework' `
    -BuildFlavor 'expert-raw-sdhms-v36-webdav-audio-eraser-s24-main-framework-7.4.12.3'

if ($LASTEXITCODE -ne 0) {
    throw "v1.3.8 build failed with exit code $LASTEXITCODE"
}
