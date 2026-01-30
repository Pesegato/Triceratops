package com.pesegato.security;

import static com.pesegato.RSACrypt.TOKEN_LABEL;

public class SecurityFactory {
    public static KeyProtector getProtector() {
        String os = System.getProperty("os.name").toLowerCase();
        KeyProtector keyProtector;
        if (os.contains("win")) {
            return new WindowsKeyProtector(); // Usa Windows-MY
        } else if (os.contains("nix") || os.contains("nux")) {
            try {
                keyProtector = getProtectorFromConfig();
                keyProtector.initialize(TOKEN_LABEL);
                return keyProtector;
            } catch (Exception e) {
                System.out.println("ℹ️ TPM non disponibile o errore inizializzazione: " + e.getMessage());
                System.err.println("🔄 Procedo con il fallback su filesystem...");
            }
            keyProtector = new SoftwareManager();
            try {
                keyProtector.initialize(TOKEN_LABEL);
            } catch (Exception e) {
                System.out.println("❌ Errore inizializzazione software: " + e.getMessage());
            }
            return keyProtector;
        }
        throw new UnsupportedOperationException("Sistema operativo non supportato.");
    }

    public static KeyProtector getProtectorFromConfig() throws Exception {

        String algo = Config.getAlgorithm();

        if ("OAEP".equals(algo)) {
            return new TpmOaepProtector();
        } else {
            return new TpmPkcs1Protector();
        }
    }
}
