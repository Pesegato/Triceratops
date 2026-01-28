#!/bin/bash
set -e

KEY_NAME="tpm_t9s_pin"
TMP_PIN_FILE=$(mktemp /dev/shm/tpm_ctx_XXXXXX)

cleanup() {
    if [ -f "$TMP_PIN_FILE" ]; then
        rm -f "$TMP_PIN_FILE"
        echo "🛡️ Pulizia RAM-file completata."
    fi
}
trap cleanup EXIT INT TERM


# --- 1. TEST: PRIVILEGI DI ROOT ---
if [ "$EUID" -ne 0 ]; then
    echo "❌ ERRORE: Questo script deve essere eseguito con privilegi di ROOT (sudo)."
    echo "Motivo: Il provisioning del TPM e l'accesso a /dev/tpmrm0 richiedono permessi elevati."
    exit 1
fi

# --- 2. TEST: PRESENZA HARDWARE TPM ---
if [ ! -e /dev/tpmrm0 ]; then
    echo "❌ ERRORE: Modulo TPM non rilevato (/dev/tpmrm0)."
    echo "Verifiche suggerite:"
    echo "  1. Controlla nel BIOS che il TPM sia 'Enabled' e 'Visible'."
    echo "  2. Assicurati che il modulo kernel 'tpm_tis' o 'tpm_crb' sia caricato."
    echo "  3. Se sei in una macchina virtuale, abilita il passthrough del TPM."
    exit 1
fi

echo "✅ Hardware TPM rilevato e accessibile."


# --- 3. CONTROLLO PRESENZA KEYCTL
if command -v keyctl >/dev/null 2>&1; then
    echo "🔎 Keyctl rilevato. Utilizzo del Kernel Keyring..."
    KEY_ID=$(keyctl search @u user "$KEY_NAME" 2>/dev/null || true)

    if [ -z "$KEY_ID" ]; then
        echo "🔑 PIN non trovato. Generazione e deposito nel Keyring..."
        TPM_PIN=$(openssl rand -base64 16)
        echo -n "$TPM_PIN" | keyctl padd user "$KEY_NAME" @u
    else
        echo "🔑 PIN recuperato dal Kernel Keyring (ID: $KEY_ID)"
        TPM_PIN=$(keyctl pipe "$KEY_ID")
    fi
else
    # 2. FALLBACK: HARDWARE ID
    echo "⚠️  Keyctl non installato."
    echo "❓ Vuoi installarlo ora? (sudo apt install keyutils)"
    read -p "Schiaccia INVIO per generare un Hardware ID come alternativa, o CTRL+C per fermarti: " confirm

    # Generiamo un PIN basato sul Machine-ID o UUID della scheda madre
    # Questo garantisce che sulla STESSA macchina il PIN sia coerente
    HW_ID=$(cat /sys/class/dmi/id/product_uuid 2>/dev/null || cat /etc/machine-id 2>/dev/null || hostname)
    TPM_PIN=$(echo -n "$HW_ID" | openssl dgst -sha256 -binary | base64 | head -c 16)

    echo "🆔 Hardware ID generato."
fi

# 3. SCRITTURA NEL RAM-FILE E LANCIO
echo -n "$TPM_PIN" > "$TMP_PIN_FILE"
chmod 400 "$TMP_PIN_FILE"
mkdir -p ~/.Triceratops

echo "🚀 Avvio Docker..."

docker run --rm -it \
  --device /dev/tpmrm0:/dev/tpmrm0 \
  -v tpm_data:/var/lib/tpm2-pkcs11 \
  -v "$TMP_PIN_FILE":/run/secrets/tpm_pin:ro \
  -v ~/.Triceratops:/app/output \
  --user "$(id -u):$(id -g)" \
  -e HOME=/app/output \
  -e HOST_HOSTNAME=$(hostname) \
  triceratops-app "-docker"

# 4. Pulizia finale: Una volta che il container si ferma, rimuove il file RAM
rm -f "$TMP_PIN_FILE"
echo "🗑️ File temporaneo rimosso."
