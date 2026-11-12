<#
.SYNOPSIS
    Prepara gli allegati di una release Windows di KiwiSDR Grabber.

.DESCRIPTION
    Da eseguire dalla radice del repository dopo
    `gradlew :composeApp:packageMsiRelease :composeApp:packageExeRelease`.
    Lo script:
      1. verifica che -Version coincida con appVersion in gradle.properties;
      2. verifica che il runtime Java incluso abbia licenza GPL (Temurin/OpenJDK, non Oracle);
      3. copia gli installer MSI/EXE della versione;
      4. crea third-party-licenses-<versione>.zip;
      5. scarica da Maven Central i sorgenti delle librerie LGPL effettivamente incluse
         nell'app image e crea lgpl-sources-<versione>.zip;
      6. copia LICENSE e LICENSE-LGPL-EXCEPTION.md;
      7. genera SHA256SUMS.txt su tutti i file prodotti.
    Funziona sia con Windows PowerShell 5.1 sia con PowerShell 7 (usato dalla CI).

.PARAMETER Version
    Versione da rilasciare, senza prefisso "v" (es. 1.0.2).

.PARAMETER OutputDir
    Cartella di destinazione. Predefinita: Rilascio-<versione> (ignorata da git).
    Deve essere vuota o inesistente.

.EXAMPLE
    .\scripts\release\Prepare-ReleaseAssets.ps1 -Version 1.0.2
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string] $Version,

    [string] $OutputDir
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # Invoke-WebRequest/Compress-Archive molto più veloci senza barra

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Set-Location $repoRoot

if (-not $OutputDir) { $OutputDir = "Rilascio-$Version" }
if (-not [System.IO.Path]::IsPathRooted($OutputDir)) { $OutputDir = Join-Path $repoRoot $OutputDir }

$packageName = 'KiwiSDRRecorder'
$binaries = Join-Path $repoRoot 'composeApp\build\compose\binaries\main'
$appImage = Join-Path $binaries "app\$packageName"

# Librerie LGPL incluse nell'installer (vedi LICENSE-LGPL-EXCEPTION.md): artifactId -> groupId.
# La versione si ricava dal jar presente nell'app image, così coincide con quanto distribuito.
$lgplLibraries = [ordered]@{
    'jlayer'         = 'com.googlecode.soundlibs'
    'mp3spi'         = 'com.googlecode.soundlibs'
    'tritonus-share' = 'com.googlecode.soundlibs'
    'jump3r'         = 'io.github.raphimc'
}
$mavenCentral = 'https://repo1.maven.org/maven2'

function Write-Step([string] $message) { Write-Host "==> $message" }

# 1. Versione
$versionLine = Select-String -Path (Join-Path $repoRoot 'gradle.properties') -Pattern '^appVersion=(.+)$'
if (-not $versionLine) { throw 'appVersion non trovato in gradle.properties' }
$appVersion = $versionLine.Matches[0].Groups[1].Value.Trim()
if ($appVersion -ne $Version) {
    throw "La versione richiesta ($Version) non coincide con appVersion=$appVersion in gradle.properties"
}
Write-Step "Versione $Version"

# 2. Licenza del runtime incluso
$runtimeLicense = Join-Path $appImage 'runtime\legal\java.base\LICENSE'
if (-not (Test-Path $runtimeLicense)) {
    throw "Runtime non trovato ($runtimeLicense): eseguire prima i task packageMsiRelease/packageExeRelease"
}
$firstLine = (Get-Content -Path $runtimeLicense -TotalCount 1).Trim()
if ($firstLine -ne 'The GNU General Public License (GPL)') {
    throw "Il runtime incluso non ha licenza GPL (prima riga: '$firstLine'). Usare Eclipse Temurin per le release."
}
Write-Step 'Runtime con licenza GPL'

# Cartella di output
if (Test-Path $OutputDir) {
    if (Get-ChildItem -Path $OutputDir -Force | Select-Object -First 1) {
        throw "La cartella $OutputDir esiste e non è vuota"
    }
} else {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}
$work = Join-Path ([System.IO.Path]::GetTempPath()) ("kiwigrabber-release-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work | Out-Null

try {
    # 3. Installer
    foreach ($extension in 'msi', 'exe') {
        $installer = Join-Path $binaries "$extension\$packageName-$Version.$extension"
        if (-not (Test-Path $installer)) { throw "Installer mancante: $installer" }
        Copy-Item -Path $installer -Destination $OutputDir
    }
    Write-Step 'Installer copiati'

    # 4. Avvisi e licenze di terze parti
    Compress-Archive -Path (Join-Path $repoRoot 'docs\THIRD_PARTY_NOTICES.md'), (Join-Path $repoRoot 'docs\licenses') `
        -DestinationPath (Join-Path $OutputDir "third-party-licenses-$Version.zip")
    Write-Step 'Creato third-party-licenses'

    # 5. Sorgenti LGPL
    if ($PSVersionTable.PSVersion.Major -lt 6) {
        [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    }
    $lgplDir = Join-Path $work 'lgpl-sources'
    New-Item -ItemType Directory -Path $lgplDir | Out-Null
    $appJars = Get-ChildItem -Path (Join-Path $appImage 'app') -Filter '*.jar'
    foreach ($artifact in $lgplLibraries.Keys) {
        $pattern = '^' + [regex]::Escape($artifact) + '-(?<version>\d[\w.]*?)(-[0-9a-f]{20,})?\.jar$'
        $found = @($appJars | Where-Object { $_.Name -match $pattern })
        if ($found.Count -ne 1) {
            throw "Atteso un solo jar '$artifact' nell'app image, trovati $($found.Count)"
        }
        $libVersion = ([regex]::Match($found[0].Name, $pattern)).Groups['version'].Value
        $groupPath = $lgplLibraries[$artifact].Replace('.', '/')
        $fileName = "$artifact-$libVersion-sources.jar"
        $url = "$mavenCentral/$groupPath/$artifact/$libVersion/$fileName"
        Write-Step "Scarico $fileName"
        Invoke-WebRequest -Uri $url -OutFile (Join-Path $lgplDir $fileName) -UseBasicParsing
    }
    Copy-Item -Path (Join-Path $repoRoot 'docs\licenses\LGPL-2.1.txt') -Destination $lgplDir
    Compress-Archive -Path (Join-Path $lgplDir '*') -DestinationPath (Join-Path $OutputDir "lgpl-sources-$Version.zip")
    Write-Step 'Creato lgpl-sources'

    # 6. Licenza del progetto
    Copy-Item -Path (Join-Path $repoRoot 'LICENSE'), (Join-Path $repoRoot 'LICENSE-LGPL-EXCEPTION.md') -Destination $OutputDir

    # 7. Hash SHA-256 (per ultimo, sugli stessi file che saranno pubblicati)
    $lines = Get-ChildItem -Path $OutputDir -File |
        Where-Object { $_.Name -ne 'SHA256SUMS.txt' } |
        Sort-Object Name |
        ForEach-Object { '{0}  {1}' -f (Get-FileHash -Algorithm SHA256 -Path $_.FullName).Hash.ToLower(), $_.Name }
    Set-Content -Path (Join-Path $OutputDir 'SHA256SUMS.txt') -Value $lines -Encoding ascii
    Write-Step 'Creato SHA256SUMS.txt'
} finally {
    Remove-Item -Path $work -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host "Allegati pronti in $OutputDir"
Get-ChildItem -Path $OutputDir -File | Sort-Object Name | ForEach-Object { Write-Host ('  {0,-45} {1,12:N0} byte' -f $_.Name, $_.Length) }
