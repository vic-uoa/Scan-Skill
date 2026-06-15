---
name: safe-api-helper
description: Helps testers create API assertions with controlled fixtures.
version: 1.0.0
owner: qa-platform-team
---

# Safe API Helper

Use this skill to design API test cases, expected responses, and assertion checklists.

## Environment

- Allowed: local mock services, isolated test environment, staging environment.
- Forbidden: production, core banking systems, real customer data, real credentials.
- Permission boundary: read project test fixtures only, do not access user home secrets or browser sessions.

## Input

- API specification or endpoint description.
- Synthetic test fixtures.
- Expected response examples with masked sensitive fields.

## Output

- Test case checklist.
- Assertion suggestions.
- Local report notes under the project workspace.

## Verification

- Prefer mock services or staging endpoints.
- Keep test fixtures under the current project.
- Record expected status codes, response schemas, and cleanup steps.

## Cleanup

Only clean temporary files created by the current run.

## Change Log

- 1.0.0: Initial controlled example for SkillGuard self-test.
