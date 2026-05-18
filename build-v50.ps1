$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = $scriptDir
$out = Join-Path $root 'build/v85-release-1.0-version-header-fix'
$resolvedRoot = (Resolve-Path $root).Path
$workspaceDir = Split-Path -Parent $scriptDir

if (Test-Path $out) {
    $resolvedOut = (Resolve-Path $out).Path
    if (-not $resolvedOut.StartsWith($resolvedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clean unexpected path: $resolvedOut"
    }
    Remove-Item -LiteralPath $out -Recurse -Force
}

New-Item -ItemType Directory -Force -Path `
    $out, `
    (Join-Path $out 'classes'), `
    (Join-Path $out 'dex'), `
    (Join-Path $out 'gen'), `
    (Join-Path $out 'stub-classes') | Out-Null

$androidJar = Join-Path $workspaceDir '.tools/android-sdk/platforms/android-35/android.jar'
$bt = Join-Path $workspaceDir '.tools/android-sdk/build-tools/35.0.0'
$javac = 'C:/Program Files/Java/jdk-21.0.10/bin/javac.exe'
$jar = 'C:/Program Files/Java/jdk-21.0.10/bin/jar.exe'

& "$bt/aapt2.exe" compile --dir "$root/res" -o "$out/compiled-res.zip"
& "$bt/aapt2.exe" link -I $androidJar --manifest "$root/AndroidManifest.xml" --java "$out/gen" -o "$out/unsigned.apk" "$out/compiled-res.zip"

Get-ChildItem "$root/stubs" -Recurse -Filter *.java |
    ForEach-Object FullName |
    Set-Content -Encoding ASCII "$out/stub-sources.txt"
& $javac -source 8 -target 8 -encoding UTF-8 -cp $androidJar -d "$out/stub-classes" "@$out/stub-sources.txt"
& $jar cf "$out/xposed-stubs.jar" -C "$out/stub-classes" .

Get-ChildItem "$root/src/main/java", "$out/gen" -Recurse -Filter *.java |
    ForEach-Object FullName |
    Set-Content -Encoding ASCII "$out/sources.txt"
$classpath = "$androidJar;$out/xposed-stubs.jar"
& $javac -source 8 -target 8 -encoding UTF-8 -cp $classpath -d "$out/classes" "@$out/sources.txt"

Push-Location "$out/classes"
& $jar cf "$out/classes-compat.zip" .
Pop-Location

& "$bt/d8.bat" --min-api 23 --lib $androidJar --output "$out/dex" "$out/classes-compat.zip"

Copy-Item "$out/unsigned.apk" "$out/apk-work.apk"
Push-Location "$out/dex"
& "$bt/aapt.exe" add "$out/apk-work.apk" classes.dex | Out-Null
Pop-Location

Push-Location $root
& "$bt/aapt.exe" add "$out/apk-work.apk" assets/xposed_init assets/xposed_scope assets/scope.list META-INF/xposed/scope.list | Out-Null
Pop-Location

& "$bt/zipalign.exe" -f 4 "$out/apk-work.apk" "$out/aligned.apk"
& "$bt/apksigner.bat" sign `
    --ks "$root/build/debug.keystore" `
    --ks-key-alias androiddebugkey `
    --ks-pass pass:android `
    --key-pass pass:android `
    --out "$out/SamsungFeatureExt-v1.0.apk" `
    "$out/aligned.apk"
& "$bt/apksigner.bat" verify --verbose --print-certs "$out/SamsungFeatureExt-v1.0.apk"

Copy-Item -Force "$out/SamsungFeatureExt-v1.0.apk" (Join-Path $workspaceDir 'SamsungWebDavRawMergedLsp.apk')
Copy-Item -Force "$out/SamsungFeatureExt-v1.0.apk" (Join-Path $workspaceDir 'MyFilesWebDavPopupLsp.apk')

$outputs = @(
    "$out/SamsungFeatureExt-v1.0.apk",
    (Join-Path $workspaceDir 'SamsungWebDavRawMergedLsp.apk'),
    (Join-Path $workspaceDir 'MyFilesWebDavPopupLsp.apk')
)
Get-Item $outputs |
    Select-Object FullName, Length, LastWriteTime
