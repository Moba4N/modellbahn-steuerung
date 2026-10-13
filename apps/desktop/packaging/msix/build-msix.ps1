# Baut aus dem fertigen App-Ordner (gradlew :apps:desktop:createDistributable) ein MSIX-Paket.
#
#   -AppDir     Ordner mit Modellbahn-Steuerung.exe
#   -OutDir     Zielordner
#   -Version    z. B. 0.1.0 (wird zu 0.1.0.0)
#   -TestSign   zusaetzlich ein selbst signiertes Paket plus Zertifikat (.cer) zum Ausprobieren
#
# Fuer den Microsoft Store wird das unsignierte Paket hochgeladen; Microsoft signiert es selbst.
param(
    [Parameter(Mandatory)] [string] $AppDir,
    [Parameter(Mandatory)] [string] $OutDir,
    [Parameter(Mandatory)] [string] $Version,
    [string] $IdentityName = "Moba4N.ModellbahnSteuerung",
    [string] $Publisher = "CN=Moba4N",
    [string] $PublisherDisplayName = "Moba4N",
    [switch] $TestSign
)
$ErrorActionPreference = "Stop"

function Find-SdkTool($name) {
    $tool = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin" -Recurse -Filter $name -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match "\\x64\\" } | Sort-Object FullName -Descending | Select-Object -First 1
    if (-not $tool) { throw "$name nicht gefunden (Windows SDK fehlt)" }
    return $tool.FullName
}

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$staging = Join-Path $OutDir "msix-staging"
if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
New-Item -ItemType Directory -Force $staging | Out-Null
Copy-Item (Join-Path $AppDir "*") $staging -Recurse
Copy-Item (Join-Path $here "Assets") $staging -Recurse

$parts = $Version.Split(".")
while ($parts.Count -lt 4) { $parts += "0" }
$fullVersion = ($parts[0..3]) -join "."

$manifest = Get-Content (Join-Path $here "AppxManifest.xml") -Raw -Encoding UTF8
$manifest = $manifest.Replace('$IDENTITY_NAME$', $IdentityName)
$manifest = $manifest.Replace('$PUBLISHER_DISPLAY_NAME$', $PublisherDisplayName)
$manifest = $manifest.Replace('$PUBLISHER$', $Publisher)
$manifest = $manifest.Replace('$VERSION$', $fullVersion)
[System.IO.File]::WriteAllText((Join-Path $staging "AppxManifest.xml"), $manifest, (New-Object System.Text.UTF8Encoding $false))

$makeappx = Find-SdkTool "makeappx.exe"
$msix = Join-Path $OutDir "Modellbahn-Steuerung-$fullVersion.msix"
& $makeappx pack /o /d $staging /p $msix
if ($LASTEXITCODE -ne 0) { throw "makeappx fehlgeschlagen" }
Write-Host "Store-Paket: $msix"

if ($TestSign) {
    $signtool = Find-SdkTool "signtool.exe"
    $password = [guid]::NewGuid().ToString()
    $cert = New-SelfSignedCertificate -Type Custom -Subject $Publisher -KeyUsage DigitalSignature `
        -FriendlyName "Modellbahn-Steuerung Test" -CertStoreLocation "Cert:\CurrentUser\My" `
        -TextExtension @("2.5.29.37={text}1.3.6.1.5.5.7.3.3", "2.5.29.19={text}")
    $pfx = Join-Path $OutDir "test.pfx"
    Export-PfxCertificate -Cert $cert -FilePath $pfx -Password (ConvertTo-SecureString $password -AsPlainText -Force) | Out-Null
    Export-Certificate -Cert $cert -FilePath (Join-Path $OutDir "Modellbahn-Steuerung-Test.cer") | Out-Null
    $testMsix = Join-Path $OutDir "Modellbahn-Steuerung-$fullVersion-test.msix"
    Copy-Item $msix $testMsix
    & $signtool sign /fd SHA256 /f $pfx /p $password $testMsix
    if ($LASTEXITCODE -ne 0) { throw "signtool fehlgeschlagen" }
    Remove-Item $pfx
    Write-Host "Testpaket: $testMsix"
}
Remove-Item $staging -Recurse -Force
