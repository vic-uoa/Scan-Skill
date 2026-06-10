# Contextual Code Noise

This fixture captures common static-scan false positives that look risky by
keyword, but do not contain a concrete leak or unsafe behavior.

Owner: SkillGuard regression suite
Version: 2026.06
Environment scope: local regression only; no production, network, or real account use.
Permissions: read the fixture files only.
Input: static source text in this example directory.
Output: scanner regression report.
Verification: scan this fixture and assert there are no final security findings.
Cleanup and rollback: no runtime state is created; remove generated build reports if needed.

The scanner should filter these cases:

- building an Authorization header from a runtime response token
- checking whether a token exists before use
- reading credentials from environment variables
- parsing Kubernetes/config key names
- using placeholder connection-string templates
- documenting a generic business label such as transaction flow
