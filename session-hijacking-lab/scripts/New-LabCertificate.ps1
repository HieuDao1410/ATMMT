# Creates local TLS material only. Does not modify the Windows certificate store.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$tlsDirectory = Join-Path $projectDirectory '.local/tls'
$keyStorePath = Join-Path $tlsDirectory 'localhost.p12'
$certificatePath = Join-Path $tlsDirectory 'localhost.cer'
$runtimePath = Join-Path $tlsDirectory 'runtime.env'

foreach ($targetPath in @($keyStorePath, $certificatePath, $runtimePath)) {
    if (Test-Path -LiteralPath $targetPath) {
        throw "TLS material already exists: $targetPath. Existing keys will not be overwritten."
    }
}
$keytoolCommand = Get-Command keytool -ErrorAction Stop
New-Item -ItemType Directory -Path $tlsDirectory -Force | Out-Null
$passwordBytes = New-Object byte[] 32
$randomGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $randomGenerator.GetBytes($passwordBytes) } finally { $randomGenerator.Dispose() }
$previousPassword = $env:LAB_TLS_PASSWORD
try {
    $env:LAB_TLS_PASSWORD = [Convert]::ToBase64String($passwordBytes)
    & $keytoolCommand.Source -genkeypair -alias session-lab -keyalg RSA -keysize 3072 `
        -sigalg SHA256withRSA -storetype PKCS12 -keystore $keyStorePath `
        -storepass:env LAB_TLS_PASSWORD -validity 30 `
        -dname 'CN=localhost,OU=Session Lab,O=ATMMT,C=VN' `
        -ext 'SAN=DNS:localhost,IP:127.0.0.1' -ext 'BC=ca:false' `
        -ext 'KU=digitalSignature,keyEncipherment' -ext 'EKU=serverAuth'
    if ($LASTEXITCODE -ne 0) { throw 'keytool could not create the keystore.' }
    & $keytoolCommand.Source -exportcert -alias session-lab -keystore $keyStorePath `
        -storepass:env LAB_TLS_PASSWORD -file $certificatePath
    if ($LASTEXITCODE -ne 0) { throw 'keytool could not export the public certificate.' }
    # No password is printed or passed in a command-line argument.
    Set-Content -LiteralPath $runtimePath -Value "LAB_TLS_PASSWORD=$env:LAB_TLS_PASSWORD" -Encoding ascii
} finally {
    $env:LAB_TLS_PASSWORD = $previousPassword
}
$certificate = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($certificatePath)
Write-Output "Created local certificate: $certificatePath"
Write-Output "Thumbprint: $($certificate.Thumbprint)"
Write-Output "Expires: $($certificate.NotAfter.ToString('yyyy-MM-dd HH:mm:ss'))"
Write-Output 'Windows trust store unchanged. See README for scoped client trust and browser setup.'
