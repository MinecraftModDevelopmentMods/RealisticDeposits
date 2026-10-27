param(
    [string] $CandidateManifest = '',
    [string] $RunDirectory = '',
    [ValidateSet('', 'os1-import', 'os3-import', 'custom-channel', 'fresh-standard')]
    [string] $Fixture = '',
    [switch] $Automated
)

$ErrorActionPreference = 'Stop'
$projectDir = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$workspaceDir = Split-Path (Split-Path $projectDir -Parent) -Parent
if (-not $CandidateManifest) {
    $CandidateManifest = Join-Path $projectDir 'run-editor-visual\candidate.json'
}
if (-not (Test-Path -LiteralPath $CandidateManifest -PathType Leaf)) {
    throw "The local editor candidate has not been prepared: $CandidateManifest"
}

$candidate = Get-Content -LiteralPath $CandidateManifest -Raw | ConvertFrom-Json
foreach ($artifact in @($candidate.orespawn, $candidate.realisticDeposits, $candidate.fixture)) {
    if (-not (Test-Path -LiteralPath $artifact.path -PathType Leaf)) {
        throw "Missing test artifact: $($artifact.path)"
    }
    if ((Get-FileHash -LiteralPath $artifact.path -Algorithm SHA256).Hash -ne $artifact.sha256) {
        throw "The prepared candidate has changed. Please prepare it again: $($artifact.path)"
    }
}

if (-not $RunDirectory) {
    $name = 'session-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff')
    $RunDirectory = Join-Path $projectDir "run-editor-visual\$name"
}
if (Test-Path -LiteralPath $RunDirectory) {
    throw "This disposable test folder already exists: $RunDirectory"
}

if ($Automated -and -not $Fixture) {
    New-Item -ItemType Directory -Path $RunDirectory | Out-Null
    foreach ($name in @('os1-import', 'os3-import', 'custom-channel', 'fresh-standard')) {
        & $PSCommandPath -CandidateManifest $CandidateManifest -Automated -Fixture $name `
            -RunDirectory (Join-Path $RunDirectory $name)
    }
    [IO.File]::WriteAllText((Join-Path $RunDirectory 'PASS.txt'),
        "All four editor profiles passed in separate disposable clients.`n")
    return
}

$installDir = Join-Path $env:USERPROFILE 'curseforge\minecraft\Install'
$versionDir = Join-Path $installDir 'versions\1.10.2'
$clientJar = Join-Path $versionDir '1.10.2.jar'
$forgeJar = Join-Path $installDir 'libraries\net\minecraftforge\forge\1.10.2-12.18.3.2511\forge-1.10.2-12.18.3.2511.jar'
$java = Join-Path $workspaceDir '.jdks\temurin-8.0.502+7\bin\java.exe'
$versionJson = Join-Path $versionDir '1.10.2.json'
foreach ($inputPath in @($java, $versionJson, $clientJar, $forgeJar)) {
    if (-not (Test-Path -LiteralPath $inputPath -PathType Leaf)) { throw "Missing input: $inputPath" }
}

$nativeDir = Join-Path $RunDirectory 'natives'
New-Item -ItemType Directory -Path $nativeDir -Force | Out-Null
$modsDir = New-Item -ItemType Directory -Path (Join-Path $RunDirectory 'mods')
foreach ($artifact in @($candidate.orespawn, $candidate.realisticDeposits, $candidate.fixture)) {
    Copy-Item -LiteralPath $artifact.path -Destination $modsDir.FullName
}
Copy-Item -LiteralPath $CandidateManifest -Destination (Join-Path $RunDirectory 'candidate.json')

$libraryRoots = @(
    (Join-Path $installDir 'libraries'),
    (Join-Path $env:APPDATA '.minecraft\libraries'),
    (Join-Path $workspaceDir '.gradle-fg7\caches\minecraftforge\forgegradle\mavenizer\caches\maven\mojang')
)
$version = Get-Content -LiteralPath $versionJson -Raw | ConvertFrom-Json
$classpath = New-Object System.Collections.Generic.List[string]
$seen = @{}

function Resolve-Library([string] $relativePath) {
    foreach ($libraryRoot in $libraryRoots) {
        $path = Join-Path $libraryRoot ($relativePath -replace '/', '\')
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
foreach ($library in $version.libraries) {
    if (-not (Test-WindowsRule $library)) { continue }
    if ($library.downloads.artifact.path) { Add-Classpath (Resolve-Library $library.downloads.artifact.path) }
    if ($library.natives.windows) {
        $classifier = $library.downloads.classifiers.([string] $library.natives.windows)
        if ($classifier.path) {
            $archive = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Library $classifier.path))
            try {
                foreach ($entry in $archive.Entries) {
                    if (-not $entry.Name -or $entry.FullName.StartsWith('META-INF/')) { continue }
                    [System.IO.Compression.ZipFileExtensions]::ExtractToFile(
                        $entry, (Join-Path $nativeDir $entry.Name), $true)
                }
            } finally { $archive.Dispose() }
        }
    }
}

# These are the official Forge libraries already used by the server checks.
$forgeLibraries = Join-Path $workspaceDir '_verification\os110-410-final-packaged-server-20260929\libraries'
Get-ChildItem -LiteralPath $forgeLibraries -Recurse -Filter '*.jar' | ForEach-Object { Add-Classpath $_.FullName }
Add-Classpath $forgeJar
Add-Classpath $clientJar

$arguments = @(
    '-Xms512m', '-Xmx3g',
    '-Dfml.ignorePatchDiscrepancies=true', '-Dfml.ignoreInvalidMinecraftCertificates=true',
    "-Deditorfixtures.automated=$($Automated.IsPresent.ToString().ToLowerInvariant())",
    "-Deditorfixtures.fixture=$Fixture",
    "-Dminecraft.client.jar=$clientJar", "-Djava.library.path=$nativeDir",
    "-Dorg.lwjgl.librarypath=$nativeDir", "-Dnet.java.games.input.librarypath=$nativeDir",
    '-cp', ($classpath -join [System.IO.Path]::PathSeparator),
    'net.minecraft.launchwrapper.Launch', '--username', 'OreSourcesTester',
    '--version', '1.10.2-forge-12.18.3.2511', '--gameDir', $RunDirectory,
    '--assetsDir', (Join-Path $installDir 'assets'), '--assetIndex', '1.10',
    '--uuid', '00000000000000000000000000000002', '--accessToken', '0',
    '--userType', 'mojang', '--versionType', 'Forge',
    '--width', '1100', '--height', '740',
    '--tweakClass', 'net.minecraftforge.fml.common.launcher.FMLTweaker'
)

Write-Host "Disposable editor test: $RunDirectory"
Push-Location -LiteralPath $RunDirectory
try {
    & $java @arguments *> (Join-Path $RunDirectory 'client.log')
    $code = $LASTEXITCODE
} finally { Pop-Location }

if ($code -ne 0) { throw "Client failed with exit code $code. See $RunDirectory\client.log" }
$failure = Join-Path $RunDirectory 'editor-results\FAIL.txt'
if (Test-Path -LiteralPath $failure) { throw (Get-Content -LiteralPath $failure -Raw) }
if ($Automated -and -not (Test-Path -LiteralPath (Join-Path $RunDirectory 'editor-results\PASS.txt'))) {
    throw 'The editor checks did not finish. The logs and test folder have been kept.'
}
