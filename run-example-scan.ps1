param(
    [string]$OutputDir = "build"
)

$ErrorActionPreference = "Stop"
$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$CurrentRoot = (Get-Location).Path
$Root = if (Test-Path -LiteralPath (Join-Path $CurrentRoot "dist\skillguard.jar")) {
    $CurrentRoot
} else {
    $ScriptRoot
}
$Jar = Join-Path $Root "dist\skillguard.jar"
$ExampleSkills = Join-Path $Root "examples\skills"
$OutDir = Join-Path $Root $OutputDir
$Html = Join-Path $OutDir "example-security-skills-scan.html"
$Json = Join-Path $OutDir "example-security-skills-scan.json"

if (-not (Test-Path -LiteralPath $Jar)) {
    throw "Cannot find $Jar. Please run .\build.ps1 first."
}

if (-not (Test-Path -LiteralPath $ExampleSkills)) {
    throw "Cannot find $ExampleSkills."
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

java -jar $Jar scan $ExampleSkills --format json --output $Json
java -jar $Jar scan $ExampleSkills --format html --output $Html

Write-Host ""
Write-Host "SkillGuard example scan completed."
Write-Host "HTML report: $Html"
Write-Host "JSON report: $Json"
Write-Host ""
Write-Host "Open the HTML file in your browser to view the report."
