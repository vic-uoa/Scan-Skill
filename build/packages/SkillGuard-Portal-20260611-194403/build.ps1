$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$classes = Join-Path $root "build\classes"
$dist = Join-Path $root "dist"

New-Item -ItemType Directory -Force -Path $classes | Out-Null
New-Item -ItemType Directory -Force -Path $dist | Out-Null

$sources = Get-ChildItem -Path (Join-Path $root "src\main\java") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
if (-not $sources) {
    throw "No Java sources found."
}

$javacVersion = (& javac -version 2>&1 | Out-String).Trim()
if ($javacVersion -match "1\.8\.") {
    javac -encoding UTF-8 -source 8 -target 8 -d $classes $sources
} else {
    javac -encoding UTF-8 --release 8 -d $classes $sources
}
jar cfe (Join-Path $dist "skillguard.jar") com.skillguard.SkillGuardCli -C $classes .

$skillBin = Join-Path $root "skill-security-scanner\bin"
New-Item -ItemType Directory -Force -Path $skillBin | Out-Null
Copy-Item -Force -Path (Join-Path $dist "skillguard.jar") -Destination (Join-Path $skillBin "skillguard.jar")

Write-Host "Built dist\skillguard.jar"
Write-Host "Updated skill-security-scanner\bin\skillguard.jar"
