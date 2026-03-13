package com.pesegato.security;

import javax.crypto.Cipher;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class EphemeralManager implements KeyProtector {

    // Campi in memoria per mantenere le chiavi durante il runtime
    private PrivateKey privateKey;
    private PublicKey publicKey;

    @Override
    public String initialize(String alias) throws Exception {
        // Se le chiavi esistono già in RAM, non rigenerarle
        if (this.publicKey != null) {
            System.out.println("KEYS ALREADY IN MEMORY");
            return this.publicKey.toString();
        }

        System.out.println("GENERATING NEW KEYS IN RAM (NON-PERSISTENT)");
        KeyPair pair = CryptoEngine.generateInitialDHKeyPair();
        //KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        //KeyPairGenerator generator = KeyPairGenerator.getInstance("DiffieHellman");
        //generator.initialize(2048);
        //KeyPair pair = generator.generateKeyPair();

        // Salvataggio nei campi della classe invece che su file
        this.privateKey = pair.getPrivate();
        this.publicKey = pair.getPublic();

        return this.publicKey.toString();
    }

    // Metodo aggiornato per restituire la chiave in RAM
    public PrivateKey getPrivateKey() throws Exception {
        if (this.privateKey == null) {
            throw new IllegalStateException("Private key not initialized in memory.");
        }
        return this.privateKey;
    }

    @Override
    public PublicKey getPublicKey() throws Exception {
        if (this.publicKey == null) {
            throw new IllegalStateException("Public key not initialized in memory.");
        }
        return this.publicKey;
    }

    @Override
    public byte[] encrypt(byte[] data) throws Exception {
        // Esempio rapido di implementazione usando la public key in RAM
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, getPublicKey());
        return cipher.doFinal(data);
    }

    @Override
    public byte[] decrypt(byte[] encryptedData) throws Exception {
        // Esempio rapido di implementazione usando la private key in RAM
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, getPrivateKey());
        return cipher.doFinal(encryptedData);
    }

    @Override
    public void test() throws Exception {
        // Test di coerenza
        String test = "Hello Memory!";
        byte[] encrypted = encrypt(test.getBytes());
        byte[] decrypted = decrypt(encrypted);
        System.out.println("Test Decrypted: " + new String(decrypted));
    }

    @Override
    public void setPIN(String pin) {

    }
}