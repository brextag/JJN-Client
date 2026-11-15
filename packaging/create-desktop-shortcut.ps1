$ErrorActionPreference = "Stop"
$projectDir = Split-Path -Parent $PSScriptRoot
$builtAppDir = Join-Path $projectDir "build\exe\JJN Client"
$installDir = Join-Path $env:LOCALAPPDATA "JJN Client"
$exePath = Join-Path $installDir "JJN Client.exe"

if (Test-Path (Join-Path $builtAppDir "JJN Client.exe")) {
    if (Test-Path $installDir) {
        Remove-Item $installDir -Recurse -Force
    }
    Copy-Item $builtAppDir $installDir -Recurse -Force
}

if (-not (Test-Path $exePath)) {
    throw "JJN Client.exe not found at $exePath. Run .\gradlew.bat packageExe first."
}

$desktop = [Environment]::GetFolderPath("Desktop")
$shortcutPath = Join-Path $desktop "JJN Client.lnk"

$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $exePath
$shortcut.WorkingDirectory = $installDir
$shortcut.IconLocation = "$exePath,0"
$shortcut.Description = "JJN Client Minecraft Launcher"
$shortcut.Save()

Write-Host "Installed EXE: $exePath"
Write-Host "Desktop shortcut updated: $shortcutPath"
