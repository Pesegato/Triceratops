#!/bin/bash
set -e

REAL_USER=${SUDO_USER:-$USER}
REAL_HOME=$(getent passwd "$REAL_USER" | cut -d: -f6)

# 2. Definisce il percorso della cartella dati
HOST_DIR="$REAL_HOME/.Triceratops"
CONF_FILE="$HOST_DIR/t9s.properties"
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

if [ ! -d "$HOST_DIR" ]; then
    mkdir -p "$HOST_DIR"
    chown "$REAL_USER:$REAL_USER" "$HOST_DIR"
fi

# --- FUNZIONE RESET ---
reset_tpm() {
    echo "⚠️  AVVISO: Il reset del TPM cancellerà TUTTE le chiavi gestite da Triceratops."
    read -p "Sei sicuro di voler procedere? (s/N): " confirm
    if [[ $confirm == [sS] ]]; then
        echo "🐳 Lancio mini-container per pulizia hardware..."
        # Usiamo l'immagine dell'app (che ha i tpm2-tools) per resettare il chip
        docker run --rm --privileged \
          --entrypoint "/bin/bash" \
          --device /dev/tpmrm0:/dev/tpmrm0 \
          -v tpm_data:/var/lib/tpm2-pkcs11 \
          ghcr.io/pesegato/triceratops-app "/clean_tpm.sh" "--factory"

        echo "🧹 Rimozione database PKCS11 (Docker Volume)..."
        # Rimuoviamo il volume docker se esiste, o la cartella locale
        docker volume rm tpm_data 2>/dev/null || true

        echo "📂 Rimozione configurazione locale..."
        rm -f "$CONF_FILE"

        if command -v keyctl >/dev/null 2>&1; then
           echo "🔑 Rimozione PIN dal Kernel Keyring..."
           KEY_ID=$(keyctl search @u user "$KEY_NAME" 2>/dev/null || true)
           [ -n "$KEY_ID" ] && keyctl unlink "$KEY_ID" @u
        fi

        echo "✅ Reset completato. Riavvia senza --resetTPM per riconfigurare."
        exit 0
    else
        echo "Operazione annullata."
        exit 1
    fi
}

# --- 1. CONTROLLO ARGOMENTI ---
if [[ " $@ " =~ " --resetTPM " ]]; then
    # Verifica root prima del reset
    if [ "$EUID" -ne 0 ]; then
        echo "❌ ERRORE: Il reset richiede privilegi di ROOT (sudo)."
        exit 1
    fi
    reset_tpm
fi

# 1. RILEVAMENTO MODALITÀ
if [[ " $@ " =~ " --newconf " ]] || [ ! -f "$CONF_FILE" ]; then
    echo "🏗️  BOOTSTRAP: Preparazione ambiente per configurazione informata..."
    TPM_PIN="bootstrap_temp_pin"
    ID_MODE="BOOTSTRAP"
else
    # Legge la preferenza salvata
    ID_MODE=$(grep "tpm.id_mode=" "$CONF_FILE" | cut -d'=' -f2)
    echo "🛡️  RUNTIME: Modalità $ID_MODE rilevata."

    if [ "$ID_MODE" == "KEYRING" ]; then
        if command -v keyctl >/dev/null 2>&1; then
           KEY_ID=$(keyctl search @u user "$KEY_NAME" 2>/dev/null || true)
           [ -z "$KEY_ID" ] && (TPM_PIN=$(openssl rand -base64 16); echo -n "$TPM_PIN" | keyctl padd user "$KEY_NAME" @u) || TPM_PIN=$(keyctl pipe "$KEY_ID")
        else
            echo "⚠️  Keyctl non installato."
            echo "❓ Vuoi installarlo ora? (sudo apt install keyutils)"
            exit
          fi
    else
        HW_ID=$(cat /sys/class/dmi/id/product_uuid 2>/dev/null || cat /etc/machine-id 2>/dev/null || hostname)
    fi
fi

echo -n "$HW_ID" > "$TMP_PIN_FILE"
chmod 400 "$TMP_PIN_FILE"

docker run --rm -it \
  --device /dev/tpmrm0:/dev/tpmrm0 \
  --network host \
  -v tpm_data:/var/lib/tpm2-pkcs11 \
  -v "$TMP_PIN_FILE":/run/secrets/tpm_pin:ro \
  -v "$HOST_DIR":/app/output \
  --user "$(id -u):$(id -g)" \
  -e HOME=/app/output \
  -e HOST_HOSTNAME=$(hostname) \
  -e ADB_SERVER_SOCKET=tcp:localhost:5037 \
  -e TPM_BOOTSTRAP_MODE=$([ "$ID_MODE" == "BOOTSTRAP" ] && echo "true" || echo "false") \
  ghcr.io/pesegato/triceratops-app "$@"