# MODx BURP

A native Android HTTP inspection workbench inspired by Burp Suite workflows. Independent project; not affiliated with PortSwigger.

## Implemented in the current source
- Native Jetpack Compose Android application branded **MODx BURP**
- Manual HTTP request composer for GET, POST, PUT, PATCH, DELETE, HEAD and OPTIONS
- Custom request headers and body
- Response status, headers, body and elapsed time
- Session-only in-memory HTTP history with detail view and clear action
- Local URL, Base64 and HTML encode/decode utilities
- Passive response-header and cookie-attribute observations (heuristic checks; not a vulnerability verdict)

## Not yet implemented
- Device-wide HTTP proxy / VPN capture
- HTTPS interception
- WebSocket frame inspection
- Persistent encrypted history and session export/import
- Device-wide HTTP proxy / VPN capture

Do not treat a listed planned feature as available until it is implemented and verified. Android VPN and TLS inspection have OS-level limitations; no silent traffic capture or certificate installation is intended.

## Build
Open in Android Studio with JDK 17 and Android SDK 35. CI workflow runs `gradle :app:assembleDebug`.

## Authorized use
Use request tools only with systems you own or have explicit permission to test. Captured traffic stays in app memory in this milestone. Do not use the app for unauthorized access or account testing.
