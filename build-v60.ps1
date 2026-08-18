$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$buildScript = Join-Path $scriptDir 'build-v54.ps1'
$nativeBuildScript = Join-Path $scriptDir 'native\bridge\build.ps1'

& $nativeBuildScript `
    -ExpectedLength 4312 `
    -ExpectedSha256 '5edb17eac4aacecf7c5034d3ddc048a700b9ba2973dda6fbc137b19e3e0d7519'

& $buildScript `
    -BuildCode 148 `
    -OutputVersion '1.3.12' `
    -OutputTag 'ExpertRAW-SDHMS-WebDAV-AudioEraser-NativeSignalTrace' `
    -BuildFlavor 'expert-raw-sdhms-v36-webdav-audio-eraser-native-signal-trace-7.4.12.3'

if ($LASTEXITCODE -ne 0) {
    throw "v1.3.12 build failed with exit code $LASTEXITCODE"
}
