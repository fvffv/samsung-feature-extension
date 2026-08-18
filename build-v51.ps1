$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$resolvedScriptDir = (Resolve-Path $scriptDir).Path
$substDrive = $null

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

function Remove-BuildTree {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$AllowedRoot
    )
    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }
    $resolvedPath = (Resolve-Path -LiteralPath $Path).Path
    $resolvedAllowedRoot = (Resolve-Path -LiteralPath $AllowedRoot).Path
    if (-not $resolvedPath.StartsWith($resolvedAllowedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clean unexpected path: $resolvedPath"
    }
    Remove-Item -LiteralPath $resolvedPath -Recurse -Force
}

try {
    # Android's Windows aapt2 still mishandles non-ASCII working paths. Build through
    # a temporary ASCII-only subst drive while keeping every file in this workspace.
    $usedDrives = @(Get-PSDrive -PSProvider FileSystem | ForEach-Object { $_.Name.ToUpperInvariant() })
    foreach ($candidate in @('S', 'R', 'Q', 'P')) {
        if ($usedDrives -notcontains $candidate) {
            $substDrive = "$candidate`:"
            break
        }
    }
    if ($null -eq $substDrive) {
        throw 'No free drive letter is available for the ASCII build path.'
    }
    Invoke-Checked -FilePath 'subst.exe' -Arguments @($substDrive, $resolvedScriptDir)

    $root = "$substDrive\"
    $out = Join-Path $root 'build\v139-expert-raw-sdhms-v36-webdav-audio-eraser-7.4.12.3'
    $apkProject = Join-Path $out 'apktool-project'
    $signedDir = Join-Path $out 'signed'
    Remove-BuildTree -Path $out -AllowedRoot $root
    New-Item -ItemType Directory -Force -Path `
        $out, `
        (Join-Path $out 'classes'), `
        (Join-Path $out 'dex'), `
        (Join-Path $out 'gen\com\samsung\feature\extension'), `
        (Join-Path $out 'stub-classes'), `
        $signedDir | Out-Null

    $jdkDir = Get-ChildItem (Join-Path $root '.tools') -Directory |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } |
        Select-Object -First 1
    if ($null -eq $jdkDir) {
        throw 'Portable JDK was not found under .tools.'
    }
    $java = Join-Path $jdkDir.FullName 'bin\java.exe'
    $javac = Join-Path $jdkDir.FullName 'bin\javac.exe'
    $jar = Join-Path $jdkDir.FullName 'bin\jar.exe'
    $apktool = Join-Path $root '.tools\apktool.jar'
    $signer = Join-Path $root '.tools\uber-apk-signer.jar'
    $androidJar = Join-Path $root '.tools\android-all-api35.jar'
    $javaBaseStubs = Join-Path $root '.tools\java-base-stubs.jar'
    $baseApk = Join-Path $root '.tools\module-base.apk'

    foreach ($required in @($java, $javac, $jar, $apktool, $signer, $androidJar, $javaBaseStubs, $baseApk)) {
        if (-not (Test-Path -LiteralPath $required)) {
            throw "Missing build dependency: $required"
        }
    }

    $androidPackRoot = Join-Path $env:ProgramFiles 'dotnet\packs\Microsoft.Android.Sdk.Windows'
    $r8 = Get-ChildItem $androidPackRoot -Recurse -Filter r8.jar -File |
        Sort-Object FullName -Descending |
        Select-Object -First 1
    $aapt2 = Get-ChildItem $androidPackRoot -Recurse -Filter aapt2.exe -File |
        Sort-Object FullName -Descending |
        Select-Object -First 1
    if ($null -eq $r8 -or $null -eq $aapt2) {
        throw 'Microsoft Android workload tools (r8/aapt2) were not found.'
    }

    Invoke-Checked -FilePath $java -Arguments @('-jar', $apktool, 'd', '-f', '-s', '-o', $apkProject, $baseApk)

    Remove-BuildTree -Path (Join-Path $apkProject 'res') -AllowedRoot $out
    Remove-BuildTree -Path (Join-Path $apkProject 'assets') -AllowedRoot $out
    Copy-Item -LiteralPath (Join-Path $root 'res') -Destination $apkProject -Recurse -Force
    Copy-Item -LiteralPath (Join-Path $root 'assets') -Destination $apkProject -Recurse -Force
    Copy-Item -LiteralPath (Join-Path $root 'AndroidManifest.xml') -Destination (Join-Path $apkProject 'AndroidManifest.xml') -Force
    $unknownXposed = Join-Path $apkProject 'unknown\META-INF\xposed'
    New-Item -ItemType Directory -Force -Path $unknownXposed | Out-Null
    Copy-Item -LiteralPath (Join-Path $root 'META-INF\xposed\scope.list') -Destination (Join-Path $unknownXposed 'scope.list') -Force

    # First resource pass establishes the exact generated IDs used by R.java.
    $resourceApk = Join-Path $out 'resource-pass.apk'
    Invoke-Checked -FilePath $java -Arguments @('-jar', $apktool, 'b', $apkProject, '-o', $resourceApk)
    $resourceDump = & $aapt2.FullName dump resources $resourceApk
    if ($LASTEXITCODE -ne 0) {
        throw 'aapt2 failed to inspect the resource pass APK.'
    }
    $rawResources = @{}
    foreach ($line in $resourceDump) {
        if ($line -match 'resource (0x[0-9a-fA-F]+) raw/([A-Za-z0-9_]+)') {
            $rawResources[$Matches[2]] = $Matches[1].ToLowerInvariant()
        }
    }
    if ($rawResources.Count -eq 0) {
        throw 'No raw resource IDs were found while generating R.java.'
    }
    $rLines = @(
        'package com.samsung.feature.extension;',
        'public final class R {',
        '  public static final class raw {'
    )
    foreach ($name in ($rawResources.Keys | Sort-Object)) {
        $rLines += "    public static final int $name = $($rawResources[$name]);"
    }
    $rLines += @('  }', '}')
    $rJava = Join-Path $out 'gen\com\samsung\feature\extension\R.java'
    $rLines | Set-Content -LiteralPath $rJava -Encoding ASCII

    Get-ChildItem (Join-Path $root 'stubs') -Recurse -Filter *.java |
        ForEach-Object FullName |
        Set-Content -Encoding ASCII (Join-Path $out 'stub-sources.txt')
    Invoke-Checked -FilePath $javac -Arguments @(
        '-source', '8', '-target', '8', '-encoding', 'UTF-8',
        '-cp', $androidJar,
        '-d', (Join-Path $out 'stub-classes'),
        "@$(Join-Path $out 'stub-sources.txt')"
    )
    $xposedStubs = Join-Path $out 'xposed-stubs.jar'
    Invoke-Checked -FilePath $jar -Arguments @('cf', $xposedStubs, '-C', (Join-Path $out 'stub-classes'), '.')

    $libJars = @(Get-ChildItem (Join-Path $root 'libs') -Filter *.jar -File | ForEach-Object FullName)
    Get-ChildItem (Join-Path $root 'src\main\java'), (Join-Path $out 'gen') -Recurse -Filter *.java |
        ForEach-Object FullName |
        Set-Content -Encoding ASCII (Join-Path $out 'sources.txt')
    $classpath = [string]::Join(';', (@($androidJar, $xposedStubs) + $libJars))
    Invoke-Checked -FilePath $javac -Arguments @(
        '-source', '8', '-target', '8', '-encoding', 'UTF-8',
        '-cp', $classpath,
        '-d', (Join-Path $out 'classes'),
        "@$(Join-Path $out 'sources.txt')"
    )

    $classesZip = Join-Path $out 'classes-compat.zip'
    Invoke-Checked -FilePath $jar -Arguments @('cf', $classesZip, '-C', (Join-Path $out 'classes'), '.')
    $d8Inputs = @($classesZip) + $libJars
    Invoke-Checked -FilePath $java -Arguments (@(
        '-cp', $r8.FullName, 'com.android.tools.r8.D8',
        '--min-api', '23',
        '--lib', $androidJar,
        '--lib', $javaBaseStubs,
        '--lib', $xposedStubs,
        '--output', (Join-Path $out 'dex')
    ) + $d8Inputs)

    Copy-Item -LiteralPath (Join-Path $out 'dex\classes.dex') -Destination (Join-Path $apkProject 'classes.dex') -Force
    $unsignedApk = Join-Path $out 'SamsungFeatureExtension-v1.3.3-ExpertRAW-SDHMS-WebDAV-AudioEraser-unsigned.apk'
    Invoke-Checked -FilePath $java -Arguments @('-jar', $apktool, 'b', $apkProject, '-o', $unsignedApk)
    Invoke-Checked -FilePath $java -Arguments @('-jar', $signer, '-a', $unsignedApk, '-o', $signedDir, '--verbose')

    $signedApk = Get-ChildItem $signedDir -Filter '*-aligned-debugSigned.apk' -File | Select-Object -First 1
    if ($null -eq $signedApk) {
        throw 'Signed APK was not produced.'
    }
    $finalApk = Join-Path $out 'SamsungFeatureExtension-v1.3.3-ExpertRAW-SDHMS-WebDAV-AudioEraser.apk'
    Copy-Item -LiteralPath $signedApk.FullName -Destination $finalApk -Force
    Invoke-Checked -FilePath $java -Arguments @('-jar', $signer, '-a', $finalApk, '-y', '--verbose')

    $rootOutput = Join-Path $root 'SamsungFeatureExtension-v1.3.3-ExpertRAW-SDHMS-WebDAV-AudioEraser.apk'
    Copy-Item -LiteralPath $finalApk -Destination $rootOutput -Force
    & $aapt2.FullName dump badging $finalApk | Select-Object -First 6
    Get-FileHash $finalApk -Algorithm SHA256
    Get-Item $finalApk, $rootOutput | Select-Object FullName, Length, LastWriteTime
}
finally {
    if ($null -ne $substDrive) {
        & subst.exe $substDrive /d | Out-Null
    }
}
