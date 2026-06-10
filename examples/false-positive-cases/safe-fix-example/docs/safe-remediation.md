# Safe Remediation Examples

The following snippets are documentation examples only.

## Password hashing

Use BCrypt, Argon2, PBKDF2, or a framework `PasswordEncoder` instead of weak
example passwords such as `123456`.

## Output encoding

When embedding JSON into HTML, escape `</script>` and call `escapeHtml` before
writing report content.

## SQL safety

Use parameterized SQL or prepared statements instead of string concatenation.

## Report placeholders

Use `Bearer <token>`, `SESSION=<masked>`, `accountNo=<masked>`, and
`request_body=<masked>` in examples.

