# Security Requirements

This document intentionally contains security policy language. These lines are
requirements, not evidence that the Skill implements vulnerable behavior.

| ID | Requirement |
|:---|:---|
| R1 | Passwords must not use `123456`, `admin`, `password`, or default credentials. |
| R2 | Logs must not record `token`, `cookie`, `session`, password, account number, card number, or ID card values. |
| R3 | File upload must reject `shell.php`, SVG XSS, `../` path traversal, and executable files. |
| R4 | Sensitive data must not be stored with MD5, SHA1, DES, Base64, or plaintext transport. |
| R5 | Business flows must not skip approval, bypass authorization, directly publish to production, or repeat payment. |

