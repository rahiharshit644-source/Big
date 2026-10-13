#!/usr/bin/env bash
# Prints the pin-sha256 (base64 SPKI hash) of every certificate a server sends, for a <pin-set> in
# res/xml/network_security_config.xml.   scripts/print_tls_pin.sh generativelanguage.googleapis.com
# Pin a CA/intermediate (not only the leaf) and always add a backup pin, or a certificate change will lock you out.
set -euo pipefail
HOST="${1:?usage: $0 <host>}"
DIR="$(mktemp -d)"
openssl s_client -servername "$HOST" -connect "$HOST:443" -showcerts </dev/null 2>/dev/null \
  | awk -v d="$DIR" '/BEGIN CERTIFICATE/{n++} n>0{print > (d "/c" n ".pem")} /END CERTIFICATE/{}'
for f in "$DIR"/c*.pem; do
  subj=$(openssl x509 -in "$f" -noout -subject 2>/dev/null | sed 's/^subject= *//')
  pin=$(openssl x509 -in "$f" -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64)
  echo "$pin   $subj"
done
rm -rf "$DIR"
