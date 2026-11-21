# Engineering roadmap

## Milestone 1 — request workbench (current)
- Native Jetpack Compose shell
- Manually composed HTTP methods, URL, headers, and body
- Response status, headers, body, and elapsed time
- In-memory request history

## Milestone 2 — editing and session workflow
- Editable raw request view
- Editable response annotations / local copy
- Repeater tabs and duplicate request
- Persistent encrypted local history
- Import/export with secret-redaction options

## Milestone 3 — protocol utilities
- URL, Base64, and HTML encode/decode
- JSON pretty-print and syntax validation
- WebSocket frame viewer for explicitly configured endpoints

## Milestone 4 — proxy research
- User-started Android VpnService prototype
- Visible foreground notification and stop action
- Explicit per-app / target scope controls
- HTTP parsing and safe forwarding tests
- HTTPS inspection feasibility and user-consented CA setup

## Milestone 5 — quality
- Unit and instrumented tests
- Network failure / timeout / large body handling
- Accessibility and small-screen layout checks
- Release build and reproducible build instructions

## Non-goals
- No stealth capture
- No credential harvesting
- No automatic login bypass
- No silent certificate installation
- No claims of full desktop Burp parity unless a feature is implemented and verified
