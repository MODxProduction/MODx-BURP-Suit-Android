# MODx BURP

A native Android HTTP inspection workbench inspired by Burp Suite workflows. Independent project; not affiliated with PortSwigger.

## Implemented in the current source
- Native Jetpack Compose Android application branded **MODx BURP**
- Manual HTTP request composer for GET, POST, PUT, PATCH, DELETE, HEAD and OPTIONS
- Custom request headers and body
- Response status, headers, body and elapsed time
- Session-only in-memory HTTP history with detail view and clear action
- Local URL, Base64 and HTML encode/decode utilities
- User-started Android VpnService DNS monitor for explicitly selected launcher apps
- DNS query forwarding to 1.1.1.1 with locally stored domain, query-type, response-code and timestamp metadata
- DNS capture JSONL export; the VPN routes only its synthetic DNS resolver address, not general HTTP/HTTPS traffic
- Passive response-header and cookie-attribute observations (heuristic checks; not a vulnerability verdict)

## Not yet implemented
- Device-wide HTTP proxy / VPN capture
- HTTPS interception
- WebSocket frame inspection
- Persistent encrypted history and session export/import
- Device-wide HTTP proxy / VPN capture

Do not treat a listed planned feature as available until it is implemented and verified. The VPN milestone is DNS-only: it forwards DNS queries for selected apps and records domain metadata. It does not inspect arbitrary packet payloads, intercept HTTP requests, decrypt HTTPS, or bypass certificate pinning. VPN use requires Android system consent and a persistent foreground notification.

## Build
Open in Android Studio with JDK 17 and Android SDK 35. CI workflow runs `gradle :app:assembleDebug`.

## Authorized use
Use request tools only with systems you own or have explicit permission to test. Captured traffic stays in app memory in this milestone. Do not use the app for unauthorized access or account testing.
