#!/bin/bash
set -e

STORE_PATH="/var/lib/tpm2-pkcs11"
# La nostra etichetta definita nel provisioning
TARGET_LABEL="T9sToken"

echo "🧼 Inizio procedura di pulizia..."

if [ "$1" == "--factory" ]; then
    echo "☢️  MODALITÀ FACTORY: Pulizia totale del TPM e dello Store."

    # 1. Rimuove TUTTE le maniglie persistenti nella gerarchia Owner
    HANDLES=$(tpm2_getcap handles-persistent | grep -o '0x81[0-9a-fA-F]*' || echo "")
    if [ -n "$HANDLES" ]; then
        echo "$HANDLES" | xargs -I {} tpm2_evictcontrol -C o -c {} || true
    fi

    # 2. Reset dei file
    rm -rf "$STORE_PATH"/*
    echo "✅ Reset totale completato."
else
    echo "🎯 MODALITÀ SELETTIVA: Rimozione specifica di $TARGET_LABEL."

    # 1. Cerca l'handle specifico della nostra chiave tramite pkcs11-tool o tpm2-tools
    # Spesso tpm2-pkcs11 salva gli oggetti come persistenti.
    # Proviamo a identificarli dal database o dai contesti.

    # Pulizia dei contesti rimasti appesi (solo i nostri)
    tpm2_flushcontext -t || true
    tpm2_flushcontext -s || true

# 1. Recuperiamo gli handle associati al nostro token nel database prima di cancellarlo
    # Usiamo sqlite3 (se presente) per trovare le maniglie persistenti registrate
    if command -v sqlite3 >/dev/null 2>&1 && [ -f "$STORE_PATH/tpm2_pkcs11.sqlite3" ]; then
        HANDLES=$(sqlite3 "$STORE_PATH/tpm2_pkcs11.sqlite3" "SELECT hex(obj.handle) FROM objects obj JOIN tokens tok ON obj.token_id = tok.id WHERE tok.label = '$TARGET_LABEL' AND obj.handle IS NOT NULL;")
        for h in $HANDLES; do
            echo "🗑️  Rilascio handle persistente: 0x$h"
            tpm2_evictcontrol -C o -c "0x$h" || true
        done
    fi

    # 2. Rimuove il database SQLite (che contiene i riferimenti PKCS11)
    # ma lascia intatte eventuali altre chiavi hardware non legate a questo store
    rm -f "$STORE_PATH/tpm2_pkcs11.sqlite3"
    rm -f "$STORE_PATH"/*.ctx

    echo "✅ Pulizia selettiva completata. Le altre chiavi TPM non sono state toccate."
fi

# Pulizia finale della sessione attuale
tpm2_flushcontext -l || true