package com.pesegato.security;

import java.security.PublicKey;

public interface KeyProtector {
    String initialize(String alias) throws Exception;

    PublicKey getPublicKey() throws Exception;

    byte[] encrypt(byte[] data) throws Exception;

    byte[] decrypt(byte[] encryptedData) throws Exception;

    void test() throws Exception;
}
