$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$buildScript = Join-Path $scriptDir 'build-v54.ps1'
$nativeBuildScript = Join-Path $scriptDir 'native\bridge\build.ps1'

& $nativeBuildScript `
    -ExpectedLength 2296 `
    -ExpectedSha256 '297982096254a97703c63f1f3c8b20e39c4701961a3ae0e95097ac35383f4047'

& $buildScript `
    -BuildCode 159 `
    -OutputVersion '1.3.23' `
    -OutputTag 'ExpertRAW-SDHMS-WebDAV-AudioEraser-OneUI85-SystemUI-Camera' `
    -BuildFlavor 'expert-raw-sdhms-v36-webdav-web-icon-audio-eraser-oneui85-systemui16-camera16-5-recording-8k60-bitrate-slow-motion-state'

if ($LASTEXITCODE -ne 0) {
    throw "v1.3.23 build failed with exit code $LASTEXITCODE"
}
