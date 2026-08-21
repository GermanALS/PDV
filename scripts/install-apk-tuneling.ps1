# Instala el APK de debug en el dispositivo Android conectado por USB y
# levanta el tunel (adb reverse) que el modo REMOTO de la app necesita para
# llegar al backend que corre en esta PC (ver CLAUDE.md sec. 3 y "Notas de
# entorno"). Requiere el backend ya levantado por separado
# (scripts/start-windows.ps1) y un dispositivo con depuracion USB habilitada.

$ErrorActionPreference = "Stop"

function Get-AdbPath {
    $fromPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($fromPath) { return $fromPath.Source }

    $fromSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $fromSdk) { return $fromSdk }

    return $null
}

$adb = Get-AdbPath
if (-not $adb) {
    Write-Error "No se encontro adb (ni en PATH ni en Android\Sdk\platform-tools). Instala Android SDK Platform-Tools o agrega adb al PATH."
    exit 1
}

$dispositivos = & $adb devices | Select-String -Pattern "\tdevice$"
if (-not $dispositivos) {
    Write-Error "No hay ningun dispositivo Android en estado 'device'. Conecta el cable USB y habilita la depuracion USB (adb devices para verificar)."
    exit 1
}

Set-Location (Join-Path $PSScriptRoot "..\android")

Write-Output "Instalando APK de debug (gradlew installDebug)..."
& .\gradlew.bat installDebug
if ($LASTEXITCODE -ne 0) {
    Write-Error "gradlew installDebug fallo (exit code $LASTEXITCODE)."
    exit $LASTEXITCODE
}

Write-Output "Levantando tunel USB para el backend (adb reverse tcp:8000 tcp:8000)..."
& $adb reverse tcp:8000 tcp:8000

Write-Output "Tuneles activos:"
& $adb reverse --list

Write-Output "Listo. El modo REMOTO funcionara mientras el cable USB siga conectado y el backend este levantado."
