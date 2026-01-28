#!/bin/bash
set -e

# Configurazione variabili d'ambiente
export TPM2_PKCS11_STORE="/var/lib/tpm2-pkcs11"
export TPM2_PKCS11_TCTI="device:/dev/tpmrm0"
export TPM2TOOLS_TCTI="device:/dev/tpmrm0"

echo "🛡️ [1/4] Verifica integrità Hardware TPM..."
# Pulizia preventiva dei contesti (evita esaurimento memoria TPM)
# Usiamo un approccio più aggressivo per liberare risorse
for handle in $(tpm2_getcap handles-transient | awk '{print $2}'); do
    tpm2_flushcontext $handle 2>/dev/null || true
done

# Controlla se il counter è diverso da zero
COUNTER=$(tpm2_getcap properties-variable | grep TPM2_PT_LOCKOUT_COUNTER | awk '{print $2}')

if [ "$COUNTER" != "0x0" ]; then
    echo "🔓 Rilevato Lockout Counter a $COUNTER. Tento il reset..."
    tpm2_dictionarylockout --clear-lockout || echo "⚠️ Reset non possibile, attendo timeout."
fi

echo "🚀 Avvio sistema TPM-Java..."

# 2. Provisioning
if [ ! -f "$TPM2_PKCS11_STORE/tpm2_pkcs11.sqlite3" ]; then
    echo "🏗️ Inizializzazione database PKCS11..."
    # Passiamo il PIN dal segreto allo script di provisioning
    TPM_PIN=$(cat /run/secrets/tpm_pin)
    bash /provision_tpm.sh "$TPM_PIN"
    echo "✅ Provisioning completato."
else
    echo "📦 Database TPM pronto."
fi

pkcs11-tool --module /usr/lib/x86_64-linux-gnu/pkcs11/libtpm2_pkcs11.so \
    --read-object --type pubkey --label "T9sToken" > tpm_public_key.der

# 3. Avvio dell'applicazione Java
echo "☕ Avvio JVM..."
exec java -Dsun.security.pkcs11.disableKFM=true -cp /app/resources:/app/classes:/app/libs/* MainKt