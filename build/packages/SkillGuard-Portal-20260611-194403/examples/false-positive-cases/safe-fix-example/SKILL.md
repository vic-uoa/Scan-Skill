---
name: safe-fix-example
description: False positive regression case for safe remediation examples.
owner: SkillGuard Maintainer
version: 1.0.0
---

# Safe Fix Example Case

## Scope

This case contains safe remediation examples. They are not production logic.

## Environment

- Allowed environment: local regression workspace.
- Forbidden environment: production, real credentials, real customer data.

## Input

- A short safe code example under `docs/`.

## Output

- No command execution, no file mutation, no network access.

## Verification

Run the local regression script. The expected scan result is `PASS`.

## Cleanup

No cleanup is required.

