package com.pesegato.security;

import com.pesegato.MainJ;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class SoftwareManager implements KeyProtector {

    @Override
    public String initialize(String alias) throws Exception {
        PublicKey publicKey;
        try {
            //read from file
            File publicKeyFile = new File(getPublicKeyPath());
            byte[] publicKeyBytes = Base64.getDecoder().decode(Files.readAllBytes(publicKeyFile.toPath()));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
            publicKey = keyFactory.generatePublic(publicKeySpec);
            System.out.println("LOADED THE KEY");
            return "" + publicKey; //string or publickey?
        } catch (Exception e) {
            System.out.println("KEY NOT PRESET");
        }
        System.out.println("GENERATING NEW KEYS");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        PrivateKey privateKey = pair.getPrivate();
        publicKey = pair.getPublic();
        //Storing Keys in Files
        try (FileOutputStream fos = new FileOutputStream(getPrivateKeyPath())) {
            fos.write(privateKey.getEncoded());
        }
        try (FileOutputStream fos = new FileOutputStream(getPublicKeyPath())) {
            fos.write(Base64.getEncoder().encode(publicKey.getEncoded()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return "" + publicKey;
    }

    private PrivateKey getPrivateKey() throws Exception {
        // Caso Fallback: Chiave RSA standard su filesystem
        byte[] keyBytes = Files.readAllBytes(Paths.get(getPrivateKeyPath()));
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));

    }

    @Override
    public PublicKey getPublicKey() throws Exception {
        File publicKeyFile = new File(getPublicKeyPath());
        byte[] publicKeyBytes = Base64.getDecoder().decode(Files.readAllBytes(publicKeyFile.toPath()));
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        return keyFactory.generatePublic(publicKeySpec);
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

    public static String getPrivateKeyPath() {
        String path = MainJ.getPath() + "keystore/";
        System.out.println("PATH: " + path);
        new File(path).mkdirs();
        return path + "private.key";
    }

    public static String getPublicKeyPath() {
        String path = MainJ.getPath() + "keystore/";
        System.out.println("PATH: " + path);
        new File(path).mkdirs();
        return path + "public.key";
    }
}
