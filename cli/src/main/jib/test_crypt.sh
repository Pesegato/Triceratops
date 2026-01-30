#!/bin/bash
# Utilizzo: ./test_crypt.sh "Tua stringa segreta"

if [ -z "$1" ]; then
    echo "Utilizzo: $0 \"stringa da cifrare\""
    exit 1
fi

INPUT_TEXT="$1"
LIB_PKCS11="/usr/lib/x86_64-linux-gnu/pkcs11/libtpm2_pkcs11.so"
export TPM2_PKCS11_STORE="/var/lib/tpm2-pkcs11"
export TPM2_PKCS11_TCTI="device:/dev/tpmrm0"

echo "📝 Testo originale: $INPUT_TEXT"

# 1. Cifratura con OpenSSL usando la chiave pubblica del TPM
echo -n "$INPUT_TEXT" | openssl pkeyutl -encrypt -pubin -keyform DER -inkey pubkey.der \
    -out cipher.bin -pkeyopt rsa_padding_mode:pkcs1

echo "🔒 Testo cifrato in 'cipher.bin'"

# 2. Pulizia sessione preventiva (per evitare errore 0x90)
tpm2_flushcontext -s || true

# 3. Decifratura tramite PKCS11
echo "🔓 Decifratura in corso..."
pkcs11-tool --module "$LIB_PKCS11" --login --pin "123456" \
    --decrypt --label "T9sToken" \
    --input-file cipher.bin --output-file decrypted.txt \
    --mechanism RSA-PKCS 2>/dev/null

echo -e "\n------------------------------------------------"
echo "🏁 RISULTATO DECIFRATO: $(cat decrypted.txt)"
echo "------------------------------------------------"

# Verifica integrità
if [ "$INPUT_TEXT" == "$(cat decrypted.txt)" ]; then
    echo "✨ SUCCESSO: I dati corrispondono perfettamente."
else
    echo "❌ ERRORE: I dati decifrati sono diversi dall'originale."
fi