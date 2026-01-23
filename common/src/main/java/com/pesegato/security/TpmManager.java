package com.pesegato.security;

import java.io.*;
import java.nio.file.*;
import java.security.SecureRandom;

public class TpmManager {

    private static final String BASE_PATH = "/var/lib/tpm2-pkcs11";
    private static final String DB_FILE = BASE_PATH + "/tpm2_pkcs11.sqlite3";
    private static final String PIN_FILE = BASE_PATH + "/tpm_pin.txt";
    private static final String TOKEN_LABEL = "T9SToken";

    private static final boolean DEBUG_PATH = false;

    public static void resetTPM() {
        try {
            Files.deleteIfExists(Path.of(DB_FILE));
            performDeepReset();
            System.out.println("🧹 Eliminato database e pulito TPM.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String setupAndGetPin() throws Exception {
        File dbFile = new File(DB_FILE);
        File pinFile = new File(PIN_FILE);

        if (DEBUG_PATH) {
            System.out.println("🔍 Debug percorsi:");
            System.out.println("Path DB: " + dbFile.getAbsolutePath() + " -> " + dbFile.exists());
            System.out.println("Path PIN: " + pinFile.getAbsolutePath() + " -> " + pinFile.exists());

            File outDir = new File("/app/output/");
            if (outDir.exists()) {
                System.out.println("Contenuto di /app/output/: " + java.util.Arrays.toString(outDir.list()));
            }
        }

        if (dbFile.exists() && pinFile.exists()) {
            String existingPin = Files.readString(pinFile.toPath()).trim();
            System.out.println("🛡️ [SECURITY-MODE] HARDWARE (TPM) - Configurazione esistente caricata.");
            return existingPin;
        }

        // 2. Se arriviamo qui, significa che dobbiamo inizializzare (o resettare)
        System.out.println("⚠️ [SECURITY] Configurazione mancante o reset richiesto. Inizializzazione...");

        // Esegui la pulizia profonda per evitare conflitti con rimasugli hardware
        performDeepReset();

        // Pulizia file residui senza cancellare il mount point
        System.out.println("🧹 Pulizia preventiva della cartella database...");
        runCommand("sh", "-c", "rm -rf " + BASE_PATH + "/*");
        runCommand("sh", "-c", "rm -rf " + BASE_PATH + "/.tpm2_*"); // Rimuove anche file nascosti come .tpm2_pkcs11
        // 3. Creazione nuova identità
        String newPin = createNewTpmIdentity();

        System.out.println("🛡️ [SECURITY-MODE] HARDWARE (TPM) - Nuova identità creata.");
        return newPin;
    }

    private static String createNewTpmIdentity() throws Exception {
        String tempSopin = generateRandomString(12);
        String newUserPin = generateRandomString(12);

        // Crea la directory se non esiste prima di iniziare
        new File(BASE_PATH).mkdirs();

        // 1. Init con percorso esplicito
        String initOutput = runCommandAndGetOutput("tpm2_ptool", "init", "--path", BASE_PATH);

        String pid = initOutput.split("id:")[1].trim().split("\\s+")[0];

        runCommand("tpm2_ptool", "addtoken",
                "--pid", pid,
                "--label", TOKEN_LABEL,
                "--sopin", tempSopin,
                "--userpin", newUserPin,
                "--path", BASE_PATH);

        runCommand("tpm2_ptool", "addkey",
                "--label", TOKEN_LABEL,
                "--userpin", newUserPin,
                "--algorithm", "rsa2048",
                "--path", BASE_PATH);

        Files.writeString(Paths.get(PIN_FILE), newUserPin);
        System.out.println("✅ Provisioning completato. Hardware sigillato.");
        return newUserPin;
    }


    private static void performDeepReset() throws Exception {
        System.out.println("⚠️ Reset richiesto. Pulizia profonda...");

        // 1. Rimuove oggetti PERSISTENTI (0x81...)
        runCommand("sh", "-c", "tpm2_getcap handles-persistent | grep 0x81 | awk '{print $2}' | xargs -L1 tpm2_evictcontrol -c 2>/dev/null || true");

        // 2. Svuota oggetti TRANSIENTI (0x80...) con il flag -t
        runCommand("sh", "-c", "tpm2_getcap handles-transient | grep 0x80 | awk '{print $2}' | xargs -I {} tpm2_flushcontext -t {} 2>/dev/null || true");

        // 3. Svuota eventuali SESSIONI caricate (0x02...) che potrebbero bloccare il chip
        runCommand("sh", "-c", "tpm2_getcap handles-loaded-session | grep 0x02 | awk '{print $2}' | xargs -I {} tpm2_flushcontext -l {} 2>/dev/null || true");

        // 4. Pulizia file software
        Files.deleteIfExists(Paths.get(DB_FILE));
        Files.deleteIfExists(Paths.get(PIN_FILE));

        System.out.println("✅ TPM riportato allo stato vergine.");
    }

    private static void runCommand(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO(); // Redirige l'output sulla console Java
        Process p = pb.start();
        if (p.waitFor() != 0) {
            throw new RuntimeException("Errore comando TPM: " + String.join(" ", command));
        }
    }

    private static String generateRandomString(int length) {
        SecureRandom random = new SecureRandom();
        // Usiamo solo alfanumerici per evitare problemi con i comandi shell
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static String runCommandAndGetOutput(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true); // Unisce stdout e stderr
        Process p = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                System.out.println(line); // Stampiamolo comunque per debug
            }
        }

        if (p.waitFor() != 0) {
            throw new RuntimeException("Errore comando TPM: " + String.join(" ", command));
        }
        return output.toString();
    }
}