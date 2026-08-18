param(
    [string]$ExpectedSha256 = '',
    [long]$ExpectedLength = 0
)

$ErrorActionPreference = 'Stop'

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments
    )
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code $LASTEXITCODE`: $FilePath $($Arguments -join ' ')"
    }
}

$bridgeRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$workspaceRoot = (Resolve-Path (Join-Path $bridgeRoot '..\..')).Path
$outputRoot = Join-Path $bridgeRoot 'build'
$assetRoot = Join-Path $workspaceRoot 'assets\audio_eraser\s24u-oneui8\arm64-v8a'

$androidPackRoot = Join-Path $env:ProgramFiles 'dotnet\packs\Microsoft.Android.Sdk.Windows'
$binutils = Get-ChildItem $androidPackRoot -Recurse -Filter 'aarch64-linux-android-as.cmd' -File |
    Sort-Object FullName -Descending |
    Select-Object -First 1
if ($null -eq $binutils) {
    throw 'Microsoft.Android ARM64 binutils were not found.'
}
$binRoot = Split-Path -Parent $binutils.FullName
$assembler = Join-Path $binRoot 'aarch64-linux-android-as.cmd'
$linker = Join-Path $binRoot 'aarch64-linux-android-ld.cmd'
$stripper = Join-Path $binRoot 'llvm-strip.exe'

New-Item -ItemType Directory -Force -Path $outputRoot, $assetRoot | Out-Null
$stubObject = Join-Path $outputRoot 'android_libc_link_stub_arm64.o'
$stubLibrary = Join-Path $outputRoot 'libc.so'
$bridgeObject = Join-Path $outputRoot 'sfe_audio_eraser_bridge_arm64.o'
$bridgeLibrary = Join-Path $outputRoot 'libsfe_audio_eraser_bridge.so'

Invoke-Checked -FilePath $assembler -Arguments @(
    '-o', $stubObject, (Join-Path $bridgeRoot 'android_libc_link_stub_arm64.s'))
Invoke-Checked -FilePath $linker -Arguments @(
    '-shared', '-soname', 'libc.so', '-z', 'max-page-size=16384',
    '-o', $stubLibrary, $stubObject)
Invoke-Checked -FilePath $assembler -Arguments @(
    '-o', $bridgeObject, (Join-Path $bridgeRoot 'sfe_audio_eraser_bridge_arm64.s'))
Invoke-Checked -FilePath $linker -Arguments @(
    '-shared', '-soname', 'libsfe_audio_eraser_bridge.so',
    '-z', 'max-page-size=16384',
    '-L', $outputRoot, '-l:libc.so',
    '-o', $bridgeLibrary, $bridgeObject)
Invoke-Checked -FilePath $stripper -Arguments @('--strip-unneeded', $bridgeLibrary)

$item = Get-Item -LiteralPath $bridgeLibrary
$hash = (Get-FileHash -LiteralPath $bridgeLibrary -Algorithm SHA256).Hash.ToLowerInvariant()
if ($ExpectedLength -gt 0 -and $item.Length -ne $ExpectedLength) {
    throw "Native bridge length mismatch: expected=$ExpectedLength actual=$($item.Length)"
}
if ($ExpectedSha256 -and $hash -ne $ExpectedSha256.ToLowerInvariant()) {
    throw "Native bridge SHA-256 mismatch: expected=$ExpectedSha256 actual=$hash"
}

Copy-Item -LiteralPath $bridgeLibrary `
    -Destination (Join-Path $assetRoot 'libsfe_audio_eraser_bridge.so') -Force

[pscustomobject]@{
    Path = $bridgeLibrary
    Length = $item.Length
    Sha256 = $hash
    Asset = Join-Path $assetRoot 'libsfe_audio_eraser_bridge.so'
}
