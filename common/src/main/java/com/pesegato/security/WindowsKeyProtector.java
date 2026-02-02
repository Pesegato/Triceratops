package com.pesegato.security;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;

import static com.pesegato.RSACrypt.TOKEN_LABEL;

public class WindowsKeyProtector implements KeyProtector {
    @Override
    public String initialize(String alias) throws Exception {
        return null;
    }

    private PrivateKey getPrivateKey() {
        try {
            // Su Windows usiamo il KeyStore nativo che integra il TPM
            KeyStore ks = KeyStore.getInstance("Windows-MY");
            ks.load(null, null);
            return (PrivateKey) ks.getKey(TOKEN_LABEL, null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    @Override
    public PublicKey getPublicKey() throws Exception {
        return null;
    }

    @Override
    public byte[] encrypt(byte[] data) throws Exception {
        return new byte[0];
    }

    @Override
    public byte[] decrypt(byte[] encryptedData) throws Exception {
        return new byte[0];
    }


    @Override
    public void test() throws Exception {

    }
}
