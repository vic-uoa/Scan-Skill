---
name: report-placeholder
description: False positive regression case for sanitized report placeholders.
owner: SkillGuard Maintainer
version: 1.0.0
---

# Report Placeholder Case

## Scope

This case stores sanitized report placeholders for local false-positive tests.

## Environment

- Allowed environment: local regression workspace.
- Forbidden environment: production, real report archives, real customer data.

## Input

- Sanitized report JSON under `security-analysis/`.

## Output

- No report is sent outside the workspace.

## Verification

Run the regression script and confirm this case has no final findings.

## Cleanup

No cleanup is required.

