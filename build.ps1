#requires -Version 5.1
<#
.SYNOPSIS
  Ephemeral builder for the Flows fluid-flow agent. Downloads a JDK into a
  project-local .build\ folder, compiles the agent, and produces flows.jar.
  Nothing is installed on the host (no PATH / Program Files / registry changes).

.PARAMETER Purge
  Delete the project-local .build\ folder and exit. Reclaims the ~300 MB JDK;
  it will be re-downloaded on the next build.

.PARAMETER Water
  Tick delay used only in the printed deploy command (default 2). The compiled
  default is also 2; either is overridable at runtime via the -javaagent flag.

.PARAMETER Lava
  Tick delay used only in the printed deploy command (default 5).

.EXAMPLE
  .\build.ps1            # download JDK (first time), compile, produce flows.jar
  .\build.ps1 -Purge     # remove the local .build\ folder
#>
[CmdletBinding()]
param(
    [switch]$Purge,
    [int]$Water = 2,
    [int]$Lava = 4
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # speeds up Invoke-WebRequest on PS 5.1
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$root     = $PSScriptRoot
$build    = Join-Path $root '.build'
$jdkDir   = Join-Path $build 'jdk25'
$zip      = Join-Path $build 'jdk25.zip'
$out      = Join-Path $build 'out'
$srcDir   = Join-Path $root 'src'
$jarOut   = Join-Path $root 'flows.jar'
$manifest = Join-Path $root 'manifest.txt'
$resDir   = Join-Path $root 'resources'

if ($Purge) {
    if (Test-Path $build) {
        Remove-Item $build -Recurse -Force
        Write-Host "[build] Purged $build"
    } else {
        Write-Host "[build] Nothing to purge ($build does not exist)."
    }
    return
}

New-Item -ItemType Directory -Force -Path $build | Out-Null

# 1. Acquire JDK 25 into the project-local .build\ folder (transient, reused on re-runs).
$javac = Get-ChildItem -Path $jdkDir -Recurse -Filter javac.exe -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $javac) {
    $url = 'https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse'
    Write-Host "[build] Downloading Temurin JDK 25 (one-time, into .build\) ..."
    Invoke-WebRequest -Uri $url -OutFile $zip
    Write-Host "[build] Extracting JDK ..."
    if (Test-Path $jdkDir) { Remove-Item $jdkDir -Recurse -Force }
    Expand-Archive -Path $zip -DestinationPath $jdkDir -Force
    Remove-Item $zip -Force -ErrorAction SilentlyContinue
    $javac = Get-ChildItem -Path $jdkDir -Recurse -Filter javac.exe -ErrorAction SilentlyContinue | Select-Object -First 1
}
if (-not $javac) { throw "javac.exe not found under $jdkDir after extraction." }
$jar = Get-ChildItem -Path $jdkDir -Recurse -Filter jar.exe -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jar) { throw "jar.exe not found under $jdkDir." }
Write-Host "[build] Using JDK: $($javac.Directory.Parent.FullName)"

# 2. Compile (targets Java 25; the Class-File API is part of the JDK 24+ platform).
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force -Path $out | Out-Null
$srcFiles = @(Get-ChildItem -Path $srcDir -Recurse -Filter *.java | ForEach-Object FullName)
if ($srcFiles.Count -eq 0) { throw "No .java sources found under $srcDir" }
Write-Host "[build] Compiling $($srcFiles.Count) source file(s) ..."
& $javac.FullName --release 25 -d $out @srcFiles
if ($LASTEXITCODE -ne 0) { throw "javac failed (exit $LASTEXITCODE)" }

# 2b. Bundle the default config (Primer/users generate flows.conf from it on first run).
if (Test-Path $resDir) {
    Copy-Item -Path (Join-Path $resDir '*') -Destination $out -Recurse -Force
}

# 3. Package the agent jar (merges Premain-Class etc. from manifest.txt).
Write-Host "[build] Packaging flows.jar ..."
& $jar.FullName --create --file $jarOut --manifest $manifest -C $out .
if ($LASTEXITCODE -ne 0) { throw "jar failed (exit $LASTEXITCODE)" }

Write-Host ""
Write-Host "[build] Done -> $jarOut"
Write-Host "[build] With Primer (recommended): drop flows.jar into your server's .\agents\ folder;"
Write-Host "          edit agents\flows.conf to tune values (water=$Water, lava=$Lava)."
Write-Host "[build] Standalone (no Primer): add to your start command before -jar, on Java 25:"
Write-Host "          -javaagent:`"$jarOut`"=water=$Water,lava=$Lava"
Write-Host "[build] Reclaim space anytime with:  .\build.ps1 -Purge"
