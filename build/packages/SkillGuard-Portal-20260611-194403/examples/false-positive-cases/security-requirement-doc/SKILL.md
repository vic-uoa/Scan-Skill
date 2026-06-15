---
name: security-requirement-doc
description: False positive regression case for security requirement documents.
owner: SkillGuard Maintainer
version: 1.0.0
---

# Security Requirement Doc Case

## Scope

This case is used only by the local SkillGuard maintainer regression suite.

## Environment

- Allowed environment: local regression workspace.
- Forbidden environment: production, core banking systems, real customer data.

## Input

- Static security requirement text under `references/`.

## Output

- No runtime action. The expected scan result is `PASS`.

## Verification

Run `scripts/regression-check.ps1` and confirm this case has no final findings.

## Cleanup

No generated files are created.

