param(
    [string]$JavaJar = ".\dist\skillguard.jar"
)

$ErrorActionPreference = "Stop"

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )
    if (-not $Condition) {
        throw $Message
    }
}

.\build.ps1 | Out-Host

New-Item -ItemType Directory -Force -Path .\build | Out-Null

java -jar $JavaJar scan .\skills --format json --output .\build\current-skills-scan.json | Out-Host
java -jar $JavaJar scan .\examples\skills --format json --output .\build\example-security-skills-scan.json | Out-Host
java -jar $JavaJar scan .\examples\skills --format html --output .\build\example-security-skills-scan.html | Out-Host
java -jar $JavaJar scan .\examples\false-positive-cases --format json --output .\build\false-positive-cases-scan.json | Out-Host
java -jar $JavaJar scan .\examples\false-positive-cases --format html --output .\build\false-positive-cases-scan.html | Out-Host

$current = Get-Content -Raw -Encoding UTF8 .\build\current-skills-scan.json | ConvertFrom-Json
$examples = Get-Content -Raw -Encoding UTF8 .\build\example-security-skills-scan.json | ConvertFrom-Json
$falsePositiveCases = Get-Content -Raw -Encoding UTF8 .\build\false-positive-cases-scan.json | ConvertFrom-Json

Assert-True ($null -ne $current.admission) "current report missing admission"
Assert-True ($null -ne $examples.admission) "examples report missing admission"
Assert-True ($null -ne $falsePositiveCases.admission) "false-positive report missing admission"
Assert-True ($current.skills_scanned -ge 1) "current skills scan did not find skills"
Assert-True ($examples.skills_scanned -ge 6) "examples scan should include at least six skills"
Assert-True ($falsePositiveCases.skills_scanned -ge 4) "false-positive cases scan should include at least four skills"

$docSkill = $current.skills | Where-Object { $_.name -eq "doc-security-mapper" } | Select-Object -First 1
Assert-True ($null -ne $docSkill) "doc-security-mapper was not recognized as a skill"

$docNormFindings = @($docSkill.findings | Where-Object { $_.rule_id -like "NORM-*" })
Assert-True ($docNormFindings.Count -eq 0) "doc-security-mapper should not produce NORM documentation false positives"

$safeNames = @("safe-api-helper", "dast-scan-planner", "report-sanitizer", "security-threat-model-helper")
foreach ($name in $safeNames) {
    $skill = $examples.skills | Where-Object { $_.name -eq $name } | Select-Object -First 1
    Assert-True ($null -ne $skill) "$name missing from examples scan"
    Assert-True ($skill.risk_level -eq "info") "$name should remain INFO"
}

$risky = $examples.skills | Where-Object { $_.name -eq "risky-ui-helper" } | Select-Object -First 1
Assert-True ($null -ne $risky) "risky-ui-helper missing from examples scan"
Assert-True ($risky.risk_level -eq "critical") "risky-ui-helper should remain CRITICAL"
Assert-True ($risky.raw_findings_count -ge $risky.final_findings_count) "raw findings should be >= final findings"
Assert-True ($risky.filtered_findings_count -ge 0) "filtered findings count should be present"

$requiredRules = @("FILE001", "EXFIL001", "BANK001", "BANK002", "CMD002")
foreach ($rule in $requiredRules) {
    $match = @($risky.findings | Where-Object { $_.rule_id -eq $rule })
    Assert-True ($match.Count -gt 0) "risky-ui-helper should keep $rule"
}

$expectedFalsePositiveSkills = @(
    "security-requirement-doc",
    "test-payload-library",
    "report-placeholder",
    "safe-fix-example",
    "contextual-code-noise"
)
foreach ($name in $expectedFalsePositiveSkills) {
    $skill = $falsePositiveCases.skills | Where-Object { $_.name -eq $name } | Select-Object -First 1
    Assert-True ($null -ne $skill) "$name missing from false-positive cases scan"
    Assert-True ($skill.final_findings_count -eq 0) "$name should not produce final findings"
    Assert-True ($skill.admission.decision -eq "PASS") "$name should remain PASS"
}

$ledger = Import-Csv .\examples\review\false-positive-ledger.csv
Assert-True (($ledger | Measure-Object).Count -ge 4) "false-positive ledger should contain local maintainer samples"

Write-Host "SkillGuard regression checks passed."
