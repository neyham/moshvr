# Security

moshVR handles SSH credentials, terminal sessions and optional microphone
audio. Treat reports involving host-key verification, secret storage, input
routing or unintended audio transmission as security-sensitive.

The project is pre-release. There is no supported stable release or promised
security-response SLA yet.

## Report privately

Use GitHub's **Security → Report a vulnerability** when private vulnerability
reporting is enabled for this repository. Never post credentials, exploit
secrets, personal host details or raw terminal recordings in a public issue.

If that private-reporting option is unavailable, open a public issue asking
for a private reporting channel **without disclosing the vulnerability**.
There is no separate security email published at this stage.

Include the affected version, a redacted reproduction, expected behavior and
impact. Use disposable credentials and a test host. Do not test against other
people's servers or accounts.

See [PRIVACY.md](PRIVACY.md) for intended data flows and
[THIRD_PARTY.md](THIRD_PARTY.md) for dependency limitations.
