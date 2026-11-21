# Security policy

## Authorized use only

MODx Burp Android is intended for security testing of applications and systems that you own or have explicit permission to assess. Do not use it to access accounts, data, or systems without authorization.

## Privacy and capture behavior

- Capture must be opt-in and visibly active.
- Provide an immediate stop control for any future VPN-based capture service.
- Keep captured requests and responses on-device by default.
- Never upload traffic, credentials, cookies, or certificates without explicit user action.
- Redact secrets in diagnostic logs.
- Treat imported session files as untrusted input.

## TLS and certificates

Any future HTTPS inspection must use an explicit user-consent flow, clearly explain Android certificate trust implications, and provide instructions to remove the test CA. The app must not bypass certificate pinning or silently install certificates.

## Reporting

Please report security issues privately to the repository owner rather than publishing sensitive exploit details.
