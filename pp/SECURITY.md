# Myra — build & security notes (Phase 5)

## 1. Sign your release build
Put these in `~/.gradle/gradle.properties` (never commit them) or pass them as environment variables:

```
MYRA_RELEASE_STORE_FILE=/path/to/my-upload-key.jks
MYRA_RELEASE_STORE_PASSWORD=...
MYRA_RELEASE_KEY_ALIAS=upload
MYRA_RELEASE_KEY_PASSWORD=...
```
The old CI names `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` still work. If nothing is configured,
`assembleRelease` now prints a warning and produces an UNSIGNED apk instead of failing mid-build.

## 2. Pin your official certificate (tamper check)
1. `scripts/print_cert_sha256.sh path/to/my-upload-key.jks upload` -> prints the SHA-256.
2. Add `MYRA_RELEASE_CERT_SHA256=<that value>` (several values may be separated by commas, e.g. Play App Signing
   certificate + your upload certificate). Optional: `MYRA_DEVELOPER_NAME`, `MYRA_COPYRIGHT_OWNER`.
3. Build release. A copy that is re-signed with another key now shows an "integrity check failed" dialog and the
   voice service refuses to start.

Modes: debug builds are never blocked. Release with **no** pin configured runs, but Settings > App Identity
shows "Unverified". Add `MYRA_REQUIRE_PIN=true` to make such a build refuse to run (fail closed).
Settings > App Identity & Security shows the signer SHA-256, installer, debuggable flag and status, with a Copy button.

## 3. What is encrypted at rest now
- Gemini and Mem0 API keys (AES-256-GCM key in the Android Keystore). Existing plaintext values are encrypted
  automatically the first time they are read.
- Website usernames/passwords saved in the browser vault (same key; old entries migrate on first read).
- Already encrypted before: voiceprints, MQTT/Wi-Fi credentials, memory backups (passphrase).
- NOT encrypted: the Memory 2.0 database and conversation archive (plain SQLite in the app's private storage).

## 4. Network & backup hardening
- `network_security_config.xml`: no clear-text http://, only system CAs trusted (user-installed CAs are ignored in
  release; debug builds still trust them for traffic inspection).
- **Your Browser-Use bridge must use https://** (or add its host to the commented `domain-config`).
- `allowBackup=false`: Android cloud/adb backup no longer copies the app's data. Use Myra's own encrypted
  backup (Settings > Memory) to move data to a new phone.
- `BootReceiver` and `IncomingCallReceiver` are no longer exported (they only need system broadcasts). If boot
  auto-start or call detection ever stops on some phone, set them back to `exported="true"` in the manifest.
- TLS pinning for Google endpoints is deliberately not enabled (stale pins would break every Gemini call).
  `scripts/print_tls_pin.sh <host>` prints current pin values if you want to add a `<pin-set>` yourself.

## 5. Optional shrinking
`-PMYRA_MINIFY=true` turns on R8 shrinking + resource shrinking for release (names are never obfuscated).
Off by default because it could not be tested on a device; test a minified build before shipping it.

## 6. Known limits (please read)
- The Gemini key bundled at build time (secrets plugin, `BuildConfig.GEMINI_API_KEY`) can be extracted from any
  APK. Restrict that key in Google Cloud Console to package `com.soltini.app` + your signing certificate SHA-1,
  or leave it empty and let users enter their own key.
- `debug.keystore` is committed in the repo: debug builds are signed with a public key. Never distribute a debug build.
- Myra holds powerful permissions (Accessibility, all-files access, overlay, SMS, calls). Only install builds you signed.
