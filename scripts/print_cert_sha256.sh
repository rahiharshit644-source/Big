#!/usr/bin/env bash
# Prints the SHA-256 fingerprint of a keystore's certificate, ready to paste as MYRA_RELEASE_CERT_SHA256.
#   scripts/print_cert_sha256.sh path/to/my-upload-key.jks upload
# (you will be asked for the keystore password)
set -euo pipefail
KEYSTORE="${1:?usage: $0 <keystore> [alias]}"
ALIAS="${2:-upload}"
keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" 2>/dev/null \
  | awk -F'SHA256: ' '/SHA256:/ {print $2; exit}' | tr -d ': ' | tr 'a-f' 'A-F'
