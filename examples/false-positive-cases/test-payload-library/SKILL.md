---
name: test-payload-library
description: False positive regression case for authorized payload libraries.
owner: SkillGuard Maintainer
version: 1.0.0
---

# Test Payload Library Case

## Scope

This case stores inert payload strings for static scanner false-positive tests.

## Environment

- Allowed environment: local regression workspace.
- Forbidden environment: production, real APIs, real authentication services.

## Input

- Payload strings under `security-tests/`.

## Output

- No network request and no command execution.

## Verification

Run the local regression script. The expected scan result is `PASS`.

## Cleanup

No cleanup is required.

