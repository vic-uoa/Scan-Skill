param(
    [string]$Version = "llm-config-polished-no-console-20260603",
    [string]$OutputDir = "build\releases"
)

$ErrorActionPreference = "Stop"
$Root = (Get-Location).Path
$ReleaseDir = Join-Path $Root $OutputDir
$Stage = Join-Path ([System.IO.Path]::GetTempPath()) "skill-scan-security-$Version"
$Zip = Join-Path $ReleaseDir "skill-scan-security-$Version.zip"

if (-not (Test-Path -LiteralPath (Join-Path $Root "dist\skillguard.jar"))) {
    throw "Cannot find dist\skillguard.jar. Please run .\build.ps1 first."
}

New-Item -ItemType Directory -Force -Path $ReleaseDir | Out-Null
$ReleaseFull = (Resolve-Path -LiteralPath $ReleaseDir).Path
$StageFull = [System.IO.Path]::GetFullPath($Stage)
$ZipFull = [System.IO.Path]::GetFullPath($Zip)
$TempFull = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
if (-not $StageFull.StartsWith($TempFull, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to clean stage outside temp directory: $StageFull"
}
if (-not $ZipFull.StartsWith($ReleaseFull, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to overwrite zip outside release directory: $ZipFull"
}
if (Test-Path -LiteralPath $Stage) {
    Remove-Item -LiteralPath $Stage -Recurse -Force
}
if (Test-Path -LiteralPath $Zip) {
    Remove-Item -LiteralPath $Zip -Force
}

New-Item -ItemType Directory -Force -Path $Stage | Out-Null

$FileItems = @(
    ".gitignore",
    "build.ps1",
    "package-portable.ps1",
    "README.md",
    "README_PORTABLE.md",
    "run-example-scan.ps1",
    "scan-skills.ps1",
    "scan-skills-llm-gui.vbs"
)

foreach ($item in $FileItems) {
    $source = Join-Path $Root $item
    if (Test-Path -LiteralPath $source) {
        Copy-Item -LiteralPath $source -Destination (Join-Path $Stage $item) -Force
    }
}

$DirItems = @(
    "dist",
    "examples",
    "scripts",
    "skill-security-scanner",
    "skills",
    "src",
    "tools"
)

foreach ($dir in $DirItems) {
    $source = Join-Path $Root $dir
    $target = Join-Path $Stage $dir
    if (Test-Path -LiteralPath $source) {
        New-Item -ItemType Directory -Force -Path $target | Out-Null
        $robocopyArgs = @(
            $source,
            $target,
            "/E",
            "/XD", ".git", ".idea", ".vscode", "build", "checkpoints", "node_modules", "target", ".venv", "venv", "__pycache__",
            "/XF", "*.zip", "*.class", "llm-config.json"
        )
        robocopy @robocopyArgs | Out-Null
        if ($LASTEXITCODE -gt 7) {
            throw "Robocopy failed with exit code $LASTEXITCODE while copying $dir"
        }
    }
}

Compress-Archive -LiteralPath $Stage -DestinationPath $Zip -Force
Remove-Item -LiteralPath $Stage -Recurse -Force

Write-Host "Portable package created:"
Write-Host $Zip
