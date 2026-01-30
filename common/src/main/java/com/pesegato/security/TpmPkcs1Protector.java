package com.pesegato.security;

import javax.crypto.Cipher;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

public class TpmPkcs1Protector extends AbstractTpmProtector {
    public TpmPkcs1Protector() throws Exception {
        super();
    }

    @Override
    protected String getTransformation() {
        return "RSA/ECB/PKCS1Padding";
    }

    @Override
    public PublicKey getPublicKey() throws Exception {
        // Carichiamo il file DER generato dal TPM durante il provisioning
        // Questo garantisce che la cifratura avvenga con la chiave CORRETTA
        byte[] pubKeyBytes = Files.readAllBytes(Paths.get("tpm_public_key.der"));
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(new X509EncodedKeySpec(pubKeyBytes));
    }

    @Override
    public byte[] decrypt(byte[] encryptedData) throws Exception {
        Cipher decryptCipher = Cipher.getInstance("RSA/ECB/NoPadding", pkcs11Provider);
        decryptCipher.init(Cipher.DECRYPT_MODE, getPrivateKey());

        ByteBuffer inputBuf = ByteBuffer.allocateDirect(encryptedData.length).put(encryptedData);
        inputBuf.flip();
        ByteBuffer outputBuf = ByteBuffer.allocateDirect(256); // RSA 2048

        decryptCipher.doFinal(inputBuf, outputBuf);
        outputBuf.flip();

        byte[] rawResult = new byte[outputBuf.remaining()];
        outputBuf.get(rawResult);

        return parsePkcs1PaddingRaw(rawResult); // Restituisce byte[] senza conversione in String
    }

    private byte[] parsePkcs1PaddingRaw(byte[] raw) throws Exception {
        int i = 2;
        while (i < raw.length && raw[i] != 0) i++;
        i++; // Salta il separatore 0x00

        byte[] data = new byte[raw.length - i];
        System.arraycopy(raw, i, data, 0, data.length);
        return data;
    }
}