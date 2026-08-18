$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$buildScript = Join-Path $scriptDir 'build-v54.ps1'

& $buildScript `
    -BuildCode 143 `
    -OutputVersion '1.3.7' `
    -OutputTag 'ExpertRAW-SDHMS-WebDAV-AudioEraser-JniCwd-DeepDiag' `
    -BuildFlavor 'expert-raw-sdhms-v36-webdav-audio-eraser-jni-cwd-deep-diag-7.4.12.3'

if ($LASTEXITCODE -ne 0) {
    throw "v1.3.7 build failed with exit code $LASTEXITCODE"
}
