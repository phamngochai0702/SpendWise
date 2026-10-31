$ErrorActionPreference = 'Stop'

if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    throw 'This packaging script must be run on Windows.'
}

$projectRoot = $PSScriptRoot
$distPath = Join-Path $projectRoot 'dist'
$appImagePath = Join-Path $distPath 'SpendWise'
$iconPath = Join-Path $projectRoot 'src\main\resources\com\spendwise\app-icon.ico'

if (Test-Path -LiteralPath $appImagePath) {
    throw "Output already exists: $appImagePath. Move or remove it before packaging again."
}
if (-not (Test-Path -LiteralPath $iconPath)) {
    throw "Windows application icon was not found: $iconPath"
}

$java = Get-Command java -ErrorAction Stop
$maven = Get-Command mvn -ErrorAction Stop
$jpackage = Get-Command jpackage -ErrorAction Stop

$javaVersionOutput = (& $java.Source --version | Out-String)
$javaVersionMatch = [regex]::Match($javaVersionOutput, '(?:version\s+"|openjdk\s+)(?:1\.)?(\d+)')
if (-not $javaVersionMatch.Success -or [int]$javaVersionMatch.Groups[1].Value -lt 21) {
    throw 'JDK 21 or newer is required to build the application image.'
}

$jpackageVersionOutput = (& $jpackage.Source --version | Out-String)
$jpackageExitCode = $LASTEXITCODE
$jpackageVersionMatch = [regex]::Match($jpackageVersionOutput, '^\s*(\d+)')
if ($jpackageExitCode -ne 0 -or -not $jpackageVersionMatch.Success -or
    [int]$jpackageVersionMatch.Groups[1].Value -lt 21) {
    throw 'jpackage from JDK 21 or newer is required.'
}

$pom = [xml](Get-Content -LiteralPath (Join-Path $projectRoot 'pom.xml') -Raw)
$versionNode = $pom.SelectSingleNode("/*[local-name()='project']/*[local-name()='version']")
if ($null -eq $versionNode) {
    throw 'Could not read the application version from pom.xml.'
}

$workingPath = Join-Path ([IO.Path]::GetTempPath()) "SpendWise-package-$([guid]::NewGuid().ToString('N'))"
$buildPath = Join-Path $workingPath 'build'
$stagingPath = Join-Path $workingPath 'input'
New-Item -ItemType Directory -Path $workingPath | Out-Null

try {
    Push-Location $projectRoot
    try {
        & $maven.Source "-Dspendwise.build.directory=$buildPath" package
        if ($LASTEXITCODE -ne 0) {
            throw "Maven packaging failed with exit code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }

    $jarPath = Join-Path $buildPath 'spendwise.jar'
    if (-not (Test-Path -LiteralPath $jarPath)) {
        throw "Runnable JAR was not created: $jarPath"
    }

    New-Item -ItemType Directory -Path $stagingPath | Out-Null
    Copy-Item -LiteralPath $jarPath -Destination (Join-Path $stagingPath 'spendwise.jar')
    New-Item -ItemType Directory -Path $distPath -Force | Out-Null

    & $jpackage.Source `
        --type app-image `
        --dest $distPath `
        --name SpendWise `
        --input $stagingPath `
        --main-jar spendwise.jar `
        --main-class com.spendwise.Main `
        --app-version $versionNode.InnerText `
        --vendor SpendWise `
        --icon $iconPath

    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE."
    }
}
finally {
    Remove-Item -LiteralPath $workingPath -Recurse -Force
}

$launcherPath = Join-Path $appImagePath 'SpendWise.exe'
if (-not (Test-Path -LiteralPath $launcherPath)) {
    throw "Application launcher was not created: $launcherPath"
}

Write-Host "Standalone application created: $appImagePath"
Write-Host 'Share the entire SpendWise folder. The receiving Windows computer does not need Java or Maven.'
