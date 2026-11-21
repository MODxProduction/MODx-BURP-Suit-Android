# MODx Burp Suite Android

A native Android security-testing workbench inspired by the workflow of Burp Suite. This is an independent project and is not affiliated with PortSwigger.

## Project status

**Phase 1 — foundation in progress.** The first milestone focuses on a native request workbench: composing HTTP requests, sending them to systems you own or are explicitly authorized to test, viewing response status/headers/body, and keeping a local request history.

A full desktop Burp Suite feature-for-feature clone is not realistic on Android. Android's VPN, TLS, certificate, and background-execution constraints require dedicated engineering. Features will be marked as implemented only after they work and are tested.

## Planned milestones

- [x] Repository and Android project foundation
- [ ] Native request composer and response viewer
- [ ] Repeater and local history
- [ ] URL / Base64 / HTML decoder utilities
- [ ] Export and import of test sessions
- [ ] WebSocket inspection
- [ ] Opt-in local VPN capture prototype
- [ ] HTTPS inspection research and explicit certificate-consent flow
- [ ] Passive security checks for authorized targets
- [ ] Instrumented tests and release build

## Safety and privacy

Use only against applications, APIs, and devices you own or have explicit permission to test. The app should not silently collect traffic. Capture features must be user-initiated, visibly active, and provide a clear stop control. Sensitive headers and bodies should remain local unless the user explicitly exports them.

## Build

Open this project in the latest stable Android Studio with a compatible Android SDK. The Gradle wrapper will be added with the build configuration in the next commit.

## License

License to be selected by the repository owner before public distribution.
