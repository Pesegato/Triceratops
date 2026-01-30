package com.pesegato.security;

import javax.crypto.Cipher;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.util.Arrays;

public abstract class AbstractTpmProtector implements KeyProtector {
    protected final String configPath;
    protected final String pin;
    protected Provider pkcs11Provider;
    protected String alias;

    public AbstractTpmProtector() throws Exception {
        this.configPath = "/tpm2.cfg";
        this.pin = Files.readString(Paths.get("/run/secrets/tpm_pin")).trim();
    }

    @Override
    public String initialize(String alias) throws Exception {
        // Pulizia sessioni per stabilità driver
        Runtime.getRuntime().exec("tpm2_flushcontext -s").waitFor();
        this.pkcs11Provider = Security.getProvider("SunPKCS11").configure(configPath);
        Security.addProvider(pkcs11Provider);
        this.alias = alias;
        return "TPM Initialized: " + alias;
    }

    PrivateKey getPrivateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS11", pkcs11Provider);
        ks.load(null, pin.toCharArray());
        return (PrivateKey) ks.getKey(alias, null);
    }

    @Override
    public PublicKey getPublicKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS11", pkcs11Provider);
        ks.load(null, pin.toCharArray());
        return ks.getCertificate(alias).getPublicKey();
    }

    protected abstract String getTransformation();

    @Override
    public byte[] encrypt(byte[] data) throws Exception {
        Cipher c = Cipher.getInstance(getTransformation());
        c.init(Cipher.ENCRYPT_MODE, getPublicKey());
        return c.doFinal(data);
    }

    // Metodo helper per la decifratura grezza (NoPadding) necessaria con i TPM
    protected byte[] decryptRaw(byte[] encryptedData) throws Exception {
        Cipher c = Cipher.getInstance("RSA/ECB/NoPadding", pkcs11Provider);
        c.init(Cipher.DECRYPT_MODE, getPrivateKey());

        ByteBuffer in = ByteBuffer.allocateDirect(encryptedData.length).put(encryptedData);
        in.flip();
        ByteBuffer out = ByteBuffer.allocateDirect(256); // Modulo RSA 2048

        c.doFinal(in, out);
        out.flip();

        byte[] raw = new byte[out.remaining()];
        out.get(raw);
        return raw;
    }

    @Override
    public void test() throws Exception {
        byte[] data = "VITTORIA_2026".getBytes();
        byte[] enc = encrypt(data);
        byte[] dec = decrypt(enc);
        if (Arrays.equals(data, dec)) {
            System.out.println("✅ Test superato per " + this.getClass().getSimpleName());
        } else {
            throw new RuntimeException("❌ Test fallito!");
        }
    }
}