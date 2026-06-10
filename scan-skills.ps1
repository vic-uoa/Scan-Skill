param(
    [Parameter(Mandatory = $true)]
    [string]$Path,

    [string]$HtmlOutput = "build\scan-report.html",
    [string]$JsonOutput = "build\scan-report.json"
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

if (-not (Test-Path -LiteralPath $Jar)) {
    throw "Cannot find $Jar. Please run .\build.ps1 first."
}

$TargetPath = Resolve-Path -LiteralPath $Path
$HtmlPath = Join-Path $Root $HtmlOutput
$JsonPath = Join-Path $Root $JsonOutput

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $HtmlPath) | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $JsonPath) | Out-Null

java -jar $Jar scan $TargetPath --format json --output $JsonPath
java -jar $Jar scan $TargetPath --format html --output $HtmlPath

Write-Host ""
Write-Host "SkillGuard scan completed."
Write-Host "Target: $TargetPath"
Write-Host "HTML report: $HtmlPath"
Write-Host "JSON report: $JsonPath"
