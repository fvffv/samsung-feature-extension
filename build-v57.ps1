$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$buildScript = Join-Path $scriptDir 'build-v54.ps1'

& $buildScript `
    -BuildCode 145 `
    -OutputVersion '1.3.9' `
    -OutputTag 'ExpertRAW-SDHMS-WebDAV-AudioEraser-S24MediaContext' `
    -BuildFlavor 'expert-raw-sdhms-v36-webdav-audio-eraser-s24-media-context-7.4.12.3'

if ($LASTEXITCODE -ne 0) {
    throw "v1.3.9 build failed with exit code $LASTEXITCODE"
}
