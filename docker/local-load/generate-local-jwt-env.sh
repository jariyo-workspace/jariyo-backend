#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
KEY_DIR="$SCRIPT_DIR/.keys"
ENV_FILE="$SCRIPT_DIR/.env"

mkdir -p "$KEY_DIR"

openssl genpkey -algorithm RSA -out "$KEY_DIR/jwt-private.pem" -pkeyopt rsa_keygen_bits:2048 >/dev/null 2>&1
openssl rsa -pubout -in "$KEY_DIR/jwt-private.pem" -out "$KEY_DIR/jwt-public.pem" >/dev/null 2>&1

escape_pem() {
	awk 'BEGIN { ORS="\\n" } { gsub(/\r/, ""); print }' "$1"
}

PUBLIC_KEY=$(escape_pem "$KEY_DIR/jwt-public.pem")
PRIVATE_KEY=$(escape_pem "$KEY_DIR/jwt-private.pem")

cat > "$ENV_FILE" <<EOF
JWT_ISSUER=https://api.jariyo.local
JWT_AUDIENCE=jariyo-web
JWT_PUBLIC_KEY=$PUBLIC_KEY
JWT_PRIVATE_KEY=$PRIVATE_KEY
EOF

echo "생성 완료: $ENV_FILE"
echo "키 경로: $KEY_DIR"
