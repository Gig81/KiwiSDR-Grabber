<#
.SYNOPSIS
    Compila la versione Android dentro un container Docker (nessun SDK sul PC).

.EXAMPLE
    .\scripts\android\build.ps1                       # :androidApp:assembleDebug
    .\scripts\android\build.ps1 :shared:testAndroidHostTest :androidApp:assembleDebug
    .\scripts\android\build.ps1 -Rebuild              # ricostruisce l'immagine

    # Release firmata: monta in sola lettura la cartella del keystore e chiede la password
    # (oppure la legge da $env:KIWI_SIGNING_STORE_PASSWORD). Al termine verifica la firma degli APK.
    .\scripts\android\build.ps1 -Keystore "$env:USERPROFILE\KiwiGrabberKeys\kiwigrabber-release.jks" :androidApp:assembleRelease
    .\scripts\android\build.ps1 -Keystore "$env:USERPROFILE\KiwiGrabberKeys\kiwigrabber-upload.jks" -KeyAlias upload :androidApp:bundleRelease

    Output (APK, AAB, report dei test) in build\android-out\.
#>
[CmdletBinding(PositionalBinding = $false)]   # gli argomenti senza nome sono tutti task Gradle
param(
    [switch]$Rebuild,
    [string]$Keystore,
    [string]$KeyAlias = 'kiwigrabber',
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Tasks
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$image = 'kiwigrabber-android-build:1'
if (-not $Tasks -or $Tasks.Count -eq 0) { $Tasks = @(':androidApp:assembleDebug') }

docker image inspect $image *> $null
if ($Rebuild -or $LASTEXITCODE -ne 0) {
    docker build -t $image (Join-Path $repo 'docker\android-build')
    if ($LASTEXITCODE -ne 0) { throw 'Build dell''immagine Docker fallita' }
}

$out = Join-Path $repo 'build\android-out'
New-Item -ItemType Directory -Force $out | Out-Null

$signingArgs = @()
$setHere = @()   # variabili d'ambiente impostate da questo script, rimosse alla fine
if ($Keystore) {
    $keystorePath = (Resolve-Path $Keystore).Path
    if ($keystorePath.StartsWith($repo, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Il keystore deve stare fuori dal repository.'
    }
    if (-not $env:KIWI_SIGNING_STORE_PASSWORD) {
        $secure = Read-Host "Password del keystore $(Split-Path -Leaf $keystorePath)" -AsSecureString
        $env:KIWI_SIGNING_STORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR(
            [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
        $setHere += 'KIWI_SIGNING_STORE_PASSWORD'
    }
    # PKCS12: la password della chiave coincide con quella del keystore.
    if (-not $env:KIWI_SIGNING_KEY_PASSWORD) {
        $env:KIWI_SIGNING_KEY_PASSWORD = $env:KIWI_SIGNING_STORE_PASSWORD
        $setHere += 'KIWI_SIGNING_KEY_PASSWORD'
    }
    $env:KIWI_SIGNING_STORE_FILE = '/keys/' + (Split-Path -Leaf $keystorePath)
    $env:KIWI_SIGNING_KEY_ALIAS = $KeyAlias
    $setHere += 'KIWI_SIGNING_STORE_FILE', 'KIWI_SIGNING_KEY_ALIAS'
    # "-e NOME" senza valore: Docker legge il valore dall'ambiente, che non compare nella riga di comando.
    $signingArgs = @(
        '-v', "$(Split-Path -Parent $keystorePath):/keys:ro",
        '-e', 'KIWI_SIGNING_STORE_FILE', '-e', 'KIWI_SIGNING_KEY_ALIAS',
        '-e', 'KIWI_SIGNING_STORE_PASSWORD', '-e', 'KIWI_SIGNING_KEY_PASSWORD'
    )
}

try {
    docker run --rm `
        -v "${repo}:/src:ro" `
        -v "${out}:/out" `
        -v kiwigrabber-android-work:/work `
        -v kiwigrabber-android-gradle:/gradle-home `
        @signingArgs `
        $image @Tasks
    $status = $LASTEXITCODE
} finally {
    foreach ($name in $setHere) { Remove-Item "Env:$name" -ErrorAction SilentlyContinue }
}

# Verifica della firma degli APK di release prodotti (apksigner dei Build-Tools nel container).
$releaseApks = Get-ChildItem (Join-Path $out 'apk\release') -Filter '*.apk' -ErrorAction SilentlyContinue
if ($status -eq 0 -and $Keystore -and $releaseApks) {
    foreach ($apk in $releaseApks) {
        Write-Host "`nFirma di $($apk.Name):"
        docker run --rm -v "${out}:/out:ro" --entrypoint bash $image -c `
            "`$(ls -d `$ANDROID_HOME/build-tools/*/ | tail -1)apksigner verify --verbose --print-certs /out/apk/release/$($apk.Name)"
        if ($LASTEXITCODE -ne 0) { $status = $LASTEXITCODE }
    }
}
exit $status
