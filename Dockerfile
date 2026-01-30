# --- STAGE 1: BUILDER ---
FROM eclipse-temurin:21-jre-noble AS builder

# Installiamo i tool di compilazione
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3-pip python3-setuptools python3-dev gcc pkg-config libtss2-dev \
    && rm -rf /var/lib/apt/lists/*

COPY tpm2-pkcs11 /opt/tpm2-pkcs11
WORKDIR /opt/tpm2-pkcs11/tools

# Installiamo tutto dentro la cartella /install (mantenendo la gerarchia /usr/local/...)
RUN pip3 install --break-system-packages --root=/install .

# --- STAGE 2: FINAL ---
FROM eclipse-temurin:21-jre-noble

# Installiamo SOLO le librerie runtime di sistema
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 \
    python3-yaml \
    python3-cryptography \
    libtpm2-pkcs11-1 \
    tpm2-openssl \
    openssl \
    opensc \
    tpm2-tools \
    libtss2-esys* libtss2-mu* libtss2-policy* libtss2-tctildr* libtss2-rc* \
    && rm -rf /var/lib/apt/lists/* && ldconfig

# Copiamo l'intero pacchetto installato dal builder (binari e librerie python)
# Questo comando unisce la struttura di /install alla root del nuovo container
COPY --from=builder /install /

#WORKDIR /app
ENV TPM2_PKCS11_STORE=/app/output
# Impedisce a Python di generare file .pyc pesanti
ENV PYTHONDONTWRITEBYTECODE=1

# Comando di verifica rapida al build (opzionale)
RUN tpm2_ptool --help > /dev/null