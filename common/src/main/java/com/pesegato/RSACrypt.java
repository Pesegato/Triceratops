package com.pesegato;

import com.pesegato.security.KeyProtector;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

public class RSACrypt {

    public static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    public static final String TOKEN_LABEL = "T9sToken";
    private static final int AES_KEY_SIZE = 256; // bit
    private static final int GCM_IV_LENGTH = 12; // bytes
    private static final int GCM_TAG_LENGTH = 128; // bit


    private static KeyProtector keyProtector;
    public RSACrypt(KeyProtector keyProtector) {
        this.keyProtector = keyProtector;
    }

    public PublicKey getPublicKey() throws Exception {
        return keyProtector.getPublicKey();
    }

    public void test()throws  Exception{
        String orig="pippo";
        System.out.println("Stringa originale "+orig);
        String crypted=encrypt(orig);
        System.out.println("Stringa cifrata "+crypted);
        String plain=decrypt(crypted);
        System.out.println("Stringa decifrata "+plain);


        //keyProtector.test();
    }

    /**
     * CIFRATURA IBRIDA:
     * 1. Genera una chiave AES casuale.
     * 2. Cifra i dati con AES/GCM.
     * 3. Cifra la chiave AES con RSA (tramite TPM o KeyProtector).
     * 4. Impacchetta tutto in un payload Base64.
     */
    public String encrypt(String plainText) throws Exception {
        // 1. Generazione chiave AES temporanea
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(AES_KEY_SIZE);
        SecretKey aesKey = keyGen.generateKey();

        // 2. Generazione IV casuale per GCM
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        // 3. Cifratura dei dati (AES/GCM)
        Cipher dataCipher = Cipher.getInstance(AES_TRANSFORMATION);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        dataCipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec);
        byte[] encryptedData = dataCipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        // 4. Cifratura della chiave AES (RSA)
        // Passiamo i byte della chiave AES al protector (TPM)
        byte[] encryptedAesKey = keyProtector.encrypt(aesKey.getEncoded());

        // 5. Impacchettamento Payload: [IV_SIZE][IV][KEY_SIZE][ENC_KEY][DATA]
        ByteBuffer buffer = ByteBuffer.allocate(
                4 + iv.length + 4 + encryptedAesKey.length + encryptedData.length
        );
        buffer.putInt(iv.length);
        buffer.put(iv);
        buffer.putInt(encryptedAesKey.length);
        buffer.put(encryptedAesKey);
        buffer.put(encryptedData);

        return Base64.getEncoder().encodeToString(buffer.array());
    }

    /**
     * DECIFRATURA IBRIDA:
     * Percorso inverso utilizzando la logica binaria per il TPM.
     */
    public String decrypt(String base64Payload) {
        try {
            byte[] payloadBytes = Base64.getDecoder().decode(base64Payload);
            ByteBuffer buffer = ByteBuffer.wrap(payloadBytes);

            // Parsing IV
            int ivSize = buffer.getInt();
            byte[] iv = new byte[ivSize];
            buffer.get(iv);

            // Parsing Chiave AES cifrata
            int encryptedKeySize = buffer.getInt();
            byte[] encryptedAesKey = new byte[encryptedKeySize];
            buffer.get(encryptedAesKey);

            // Parsing Dati
            byte[] encryptedData = new byte[buffer.remaining()];
            buffer.get(encryptedData);

            // Decifratura della chiave AES (RSA via TPM)
            // Utilizza internamente NoPadding e buffer diretti per stabilità
            byte[] decryptedAesKeyBytes = keyProtector.decrypt(encryptedAesKey);
            SecretKey aesKey = new SecretKeySpec(decryptedAesKeyBytes, "AES");

            // Decifratura dei dati (AES/GCM)
            Cipher dataCipher = Cipher.getInstance(AES_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            dataCipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec);

            byte[] decryptedDataBytes = dataCipher.doFinal(encryptedData);
            return new String(decryptedDataBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            System.err.println("❌ Errore nella decifratura ibrida: " + e.getMessage());
            return null;
        }
    }

    public static String printHexBinary(byte[] data) {
        StringBuilder r = new StringBuilder(data.length * 2);
        for (byte b : data) {
            r.append(String.format("%02X ", b));
        }
        return r.toString().trim();
    }
}
