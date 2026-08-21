# Contraparte de install-apk-tuneling.ps1: quita el tunel USB (adb reverse)
# levantado para probar el modo REMOTO en el dispositivo fisico. No
# desinstala el APK ni detiene el backend (para eso, scripts/stop-windows.ps1)
# - solo libera el recurso de red de la sesion de prueba.

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

Write-Output "Quitando tunel USB (adb reverse --remove tcp:8000)..."
try {
    & $adb reverse --remove tcp:8000 2>$null
} catch {
    # No fatal: puede que el dispositivo ya se haya desconectado, con lo
    # cual el tunel ya no existe de todas formas.
}

Write-Output "Tuneles activos restantes:"
& $adb reverse --list

Write-Output "Listo."
