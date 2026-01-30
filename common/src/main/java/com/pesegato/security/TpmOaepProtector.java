package com.pesegato.security;

import javax.crypto.Cipher;
import java.nio.ByteBuffer;

public class TpmOaepProtector extends AbstractTpmProtector {
    public TpmOaepProtector() throws Exception {
        super();
    }

    @Override
    protected String getTransformation() {
        return "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    }

    @Override
    public byte[] decrypt(byte[] encryptedData) throws Exception {
        // OAEP è complesso da parsare a mano; proviamo la decifratura diretta del driver
        Cipher c = Cipher.getInstance(getTransformation(), pkcs11Provider);
        c.init(Cipher.DECRYPT_MODE, getPrivateKey());

        ByteBuffer in = ByteBuffer.allocateDirect(encryptedData.length).put(encryptedData);
        in.flip();
        ByteBuffer out = ByteBuffer.allocateDirect(256);
        c.doFinal(in, out);
        out.flip();

        byte[] res = new byte[out.remaining()];
        out.get(res);
        return res;
    }
}