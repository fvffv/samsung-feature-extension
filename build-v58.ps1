$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$buildScript = Join-Path $scriptDir 'build-v54.ps1'

& $buildScript `
    -BuildCode 146 `
    -OutputVersion '1.3.10' `
    -OutputTag 'ExpertRAW-SDHMS-WebDAV-AudioEraser-CompleteMSSModels' `
    -BuildFlavor 'expert-raw-sdhms-v36-webdav-audio-eraser-complete-mss-models-7.4.12.3'

if ($LASTEXITCODE -ne 0) {
    throw "v1.3.10 build failed with exit code $LASTEXITCODE"
}
