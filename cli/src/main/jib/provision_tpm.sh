#!/bin/bash
set -e

# --- CONFIGURAZIONE ---
STORE_PATH="/var/lib/tpm2-pkcs11"
LIB_PKCS11="/usr/lib/x86_64-linux-gnu/pkcs11/libtpm2_pkcs11.so"
TOKEN_LABEL="Triceratops"
KEY_LABEL="T9sToken"
STRING_ID="01"
HEX_ID="3031"

# Recupero PIN
# Aggiungi un timeout di 5 secondi. Se il PIN non viene fornito, lo script fallirà.
if ! read -t 5 -r PIN; then
    echo "ERRORE: Timeout in attesa del PIN su stdin." >&2
    exit 1
fi

echo "🧹 [1/4] Pulizia profonda..."
tpm2_flushcontext -t || true
tpm2_flushcontext -s || true
rm -rf "$STORE_PATH"/* && mkdir -p "$STORE_PATH"

echo "🏗️ [2/4] Inizializzazione con PID 1..."
tpm2_ptool init --path "$STORE_PATH"

# Qui il PID è richiesto esplicitamente
tpm2_ptool addtoken --path "$STORE_PATH" --pid 1 --label "$TOKEN_LABEL" --userpin "$PIN" --sopin "$PIN"

# Qui creiamo la chiave (ptool gestisce il PID internamente tramite il token)
tpm2_ptool addkey --path "$STORE_PATH" --label "$TOKEN_LABEL" --userpin "$PIN" \
    --algorithm rsa2048 --key-label "$KEY_LABEL" --id "$STRING_ID"

echo "📜 [3/4] Generazione Certificato..."
openssl req -new -x509 -newkey rsa:2048 -nodes \
    -keyout temp.key -out cert.der -outform DER \
    -days 3650 -subj "/CN=$KEY_LABEL" 2>/dev/null

echo "💉 [4/4] Iniezione Certificato (ID: $HEX_ID)..."
pkcs11-tool --module "$LIB_PKCS11" --login --pin "$PIN" \
    --token-label "$TOKEN_LABEL" \
    --write-object cert.der --type cert \
    --label "$KEY_LABEL" --id "$HEX_ID"

rm -f temp.key cert.der

pkcs11-tool --module /usr/lib/x86_64-linux-gnu/pkcs11/libtpm2_pkcs11.so \
    --read-object --type pubkey --label "T9sToken" > tpm_public_key.der

echo "------------------------------------------------"
echo "✅ PROVISIONING COMPLETATO"
pkcs11-tool --module "$LIB_PKCS11" --list-objects --token-label "$TOKEN_LABEL"