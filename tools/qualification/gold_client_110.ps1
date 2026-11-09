param(
    [Parameter(Mandatory = $true)] [string] $RunDirectory,
    [Parameter(Mandatory = $true)] [string] $JavaPath,
    [Parameter(Mandatory = $true)] [string] $GameInstall,
    [Parameter(Mandatory = $true)] [string] $ForgeRuntime,
    [Parameter(Mandatory = $true)] [string] $ModJar,
    [Parameter(Mandatory = $true)] [string] $OreSpawnJar,
    [Parameter(Mandatory = $true)] [string] $ProbeJar,
    [string] $LocalOreSpawnSha256
)

$ErrorActionPreference = 'Stop'

function Assert-ClientLog([string] $LogPath) {
    $errors = @(Get-Content -LiteralPath $LogPath | Where-Object {
        $_ -match '/(ERROR|FATAL)\]|Tried to assign a mutable BlockPos|\bGL ERROR\b' -and
        $_ -notlike '*FML appears to be missing any signature data*'
    })

    if ($errors.Count -gt 0) {
        throw "The packaged client logged an unexpected error. See ${LogPath}: $($errors[0])"
    }
}

function New-HostTestPack([string] $ClientPath, [string] $TargetDirectory) {
    $packs = Join-Path $TargetDirectory 'resourcepacks'
    New-Item -ItemType Directory -Path $packs | Out-Null
    $pack = [IO.Compression.ZipFile]::Open((Join-Path $packs 'gold-host-test.zip'),
        [IO.Compression.ZipArchiveMode]::Create)
    $client = [IO.Compression.ZipFile]::OpenRead($ClientPath)

    try {
        $writer = [IO.StreamWriter]::new($pack.CreateEntry('pack.mcmeta').Open())
        try {
            $writer.Write('{"pack":{"pack_format":2,"description":"Gold gravel host check"}}')
        }
        finally {
            $writer.Dispose()
        }

        # Reuse an unmodified vanilla texture so the comparison has exact expected pixels.
        $source = $client.GetEntry('assets/minecraft/textures/blocks/diamond_block.png').Open()
        $destination = $pack.CreateEntry('assets/minecraft/textures/blocks/gravel.png').Open()
        try {
            $source.CopyTo($destination)
        }
        finally {
            $destination.Dispose()
            $source.Dispose()
        }
    }
    finally {
        $client.Dispose()
        $pack.Dispose()
    }
}

$runDir = $RunDirectory
$installDir = $GameInstall
$versionDir = Join-Path $installDir 'versions\1.10.2'
$versionJson = Join-Path $versionDir '1.10.2.json'
$clientJar = Join-Path $versionDir '1.10.2.jar'
$forgeJar = Join-Path $ForgeRuntime 'forge-1.10.2-12.18.3.2511-universal.jar'
$java = $JavaPath
$orespawnJar = $OreSpawnJar
$realisticDepositsJar = $ModJar
$clientProbe = $ProbeJar
$nativeDir = Join-Path $runDir 'natives'

if (Test-Path -LiteralPath $runDir) { throw "Disposable run already exists: $runDir" }
foreach ($required in @($versionJson, $clientJar, $forgeJar, $java, $orespawnJar, $realisticDepositsJar, $clientProbe)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing input: $required" }
}
New-Item -ItemType Directory -Path (Join-Path $runDir 'mods') -Force | Out-Null
New-Item -ItemType Directory -Path $nativeDir -Force | Out-Null
Copy-Item -LiteralPath $orespawnJar -Destination (Join-Path $runDir 'mods')
Copy-Item -LiteralPath $realisticDepositsJar -Destination (Join-Path $runDir 'mods')
Copy-Item -LiteralPath $clientProbe -Destination (Join-Path $runDir 'mods')
$expectedOreSpawn = '05DB7CBFAADC815D6A713DACDFB9F0647BCED49CCFC357FB2FF010E95BD1379F'
if ($LocalOreSpawnSha256) {
    if ($LocalOreSpawnSha256 -cnotmatch '^[0-9A-Fa-f]{64}$') { throw 'Supply the complete candidate SHA-256' }
    $expectedOreSpawn = $LocalOreSpawnSha256.ToUpperInvariant()
}
if ((Get-FileHash -LiteralPath $orespawnJar -Algorithm SHA256).Hash -ne $expectedOreSpawn) {
    throw 'OreSpawn does not match the selected public or local dependency'
}
if ((Get-FileHash -LiteralPath $forgeJar -Algorithm SHA256).Hash -ne '809C2EA5B4D4F3E5A294D3A326744F20FEC412A1263E708297D00F512E377542') {
    throw 'Forge does not match the qualified runtime'
}

$libraryRoots = @(
    (Join-Path $installDir 'libraries'),
    (Join-Path $env:APPDATA '.minecraft\libraries'),
    (Join-Path $ForgeRuntime 'libraries')
)
$version = Get-Content -LiteralPath $versionJson -Raw | ConvertFrom-Json
if ((Get-FileHash -LiteralPath $clientJar -Algorithm SHA1).Hash -ne $version.downloads.client.sha1.ToUpperInvariant()) {
    throw 'The Minecraft client does not match its official version metadata'
}
# The server-side copy has a manifest pointing to an adjacent Minecraft server
# jar. Stage Forge alone so those classes cannot enter this client launch.
$stagedForge = Join-Path $runDir 'forge-client.jar'
Copy-Item -LiteralPath $forgeJar -Destination $stagedForge
$forgeJar = $stagedForge
$classpath = New-Object System.Collections.Generic.List[string]
$seen = @{}

function Resolve-Library([string] $relativePath) {
    foreach ($root in $libraryRoots) {
        $path = Join-Path $root ($relativePath -replace '/', '\')
        if (Test-Path -LiteralPath $path -PathType Leaf) { return $path }
    }
    throw "Missing Minecraft library: $relativePath"
}

function Add-Classpath([string] $path) {
    $resolved = (Resolve-Path -LiteralPath $path).Path
    $key = $resolved.ToLowerInvariant()
    if (-not $seen.ContainsKey($key)) {
        $seen[$key] = $true
        $classpath.Add($resolved)
    }
}

function Test-WindowsRule($library) {
    if (-not $library.rules) { return $true }
    $allowed = $false
    foreach ($rule in $library.rules) {
        if (-not $rule.os -or $rule.os.name -eq 'windows') { $allowed = $rule.action -eq 'allow' }
    }
    return $allowed
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
New-HostTestPack $clientJar $runDir
foreach ($library in $version.libraries) {
    if (-not (Test-WindowsRule $library)) { continue }
    if ($library.downloads.artifact.path) {
        Add-Classpath (Resolve-Library $library.downloads.artifact.path)
    }
    if ($library.natives.windows) {
        $classifier = $library.downloads.classifiers.([string] $library.natives.windows)
        if ($classifier.path) {
            $archive = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Library $classifier.path))
            try {
                foreach ($entry in $archive.Entries) {
                    if (-not $entry.Name -or $entry.FullName.StartsWith('META-INF/')) { continue }
                    $target = Join-Path $nativeDir $entry.Name
                    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
                }
            }
            finally { $archive.Dispose() }
        }
    }
}

Get-ChildItem (Join-Path $ForgeRuntime 'libraries') -Recurse -Filter '*.jar' | ForEach-Object {
    Add-Classpath $_.FullName
}
Add-Classpath $clientJar
Add-Classpath $forgeJar

$arguments = @(
    '-Xms512m', '-Xmx3g',
    '-Dclientprobe.enabled=true',
    "-Dminecraft.client.jar=$clientJar",
    "-Djava.library.path=$nativeDir",
    "-Dorg.lwjgl.librarypath=$nativeDir",
    "-Dnet.java.games.input.librarypath=$nativeDir",
    '-cp', ($classpath -join [System.IO.Path]::PathSeparator),
    'net.minecraft.launchwrapper.Launch',
    '--username', 'OreSpawnTester',
    '--version', '1.10.2-forge-12.18.3.2511',
    '--gameDir', $runDir,
    '--assetsDir', (Join-Path $installDir 'assets'),
    '--assetIndex', '1.10',
    '--uuid', '00000000000000000000000000000002',
    '--accessToken', '0',
    '--userType', 'mojang',
    '--versionType', 'Forge',
    '--tweakClass', 'net.minecraftforge.fml.common.launcher.FMLTweaker'
)

$start = [System.Diagnostics.ProcessStartInfo]::new()
$start.FileName = $java
$start.WorkingDirectory = $runDir
$start.UseShellExecute = $false
$start.CreateNoWindow = $true
$start.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
$start.RedirectStandardOutput = $true
$start.RedirectStandardError = $true
foreach ($argument in $arguments) { $start.ArgumentList.Add($argument) }

$process = [System.Diagnostics.Process]::Start($start)
$stdout = $process.StandardOutput.ReadToEndAsync()
$stderr = $process.StandardError.ReadToEndAsync()
if (-not $process.WaitForExit(360000)) {
    $process.Kill($true)
    $process.WaitForExit()
    [System.IO.File]::WriteAllText((Join-Path $runDir 'client.log'), $stdout.GetAwaiter().GetResult())
    [System.IO.File]::WriteAllText((Join-Path $runDir 'client-stderr.log'), $stderr.GetAwaiter().GetResult())
    throw 'Gold client timed out; its evidence is retained'
}
[System.IO.File]::WriteAllText((Join-Path $runDir 'client.log'), $stdout.GetAwaiter().GetResult())
[System.IO.File]::WriteAllText((Join-Path $runDir 'client-stderr.log'), $stderr.GetAwaiter().GetResult())
if ($process.ExitCode -ne 0) { throw "Packaged client exit code: $($process.ExitCode)" }
if (-not (Test-Path -LiteralPath (Join-Path $runDir 'gold-client-pass.properties'))) {
    throw 'The gold client checks did not complete'
}
if (Get-ChildItem -LiteralPath (Join-Path $runDir 'crash-reports') -File -ErrorAction SilentlyContinue) {
    throw 'The gold client produced a crash report'
}
Assert-ClientLog (Join-Path $runDir 'client.log')
Get-Content -LiteralPath (Join-Path $runDir 'gold-client-pass.properties')
