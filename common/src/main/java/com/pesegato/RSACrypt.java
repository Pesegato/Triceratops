package com.pesegato;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class RSACrypt {

    public static final String RSA_TRANSFORMATION_STRING = "RSA/ECB/PKCS1Padding";

    public static PublicKey generateOrGetKeyPair() {
        PublicKey publicKey;
        try {
            //read from file
            File publicKeyFile = new File(getPublicKeyPath());
            byte[] publicKeyBytes = Base64.getDecoder().decode(Files.readAllBytes(publicKeyFile.toPath()));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
            publicKey = keyFactory.generatePublic(publicKeySpec);
            System.out.println("LOADED THE KEY");
            return publicKey;
        } catch (Exception e) {
            System.out.println("KEY NOT PRESET");
        }
        System.out.println("GENERATING NEW KEYS");
        try {
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
            return publicKey;
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }
    public static void test(PublicKey publicKey, PrivateKey privateKey){
        try{
            //test string
            String secretMessage = "Baeldung secret message";
            Cipher encryptCipher = Cipher.getInstance("RSA");
            encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] secretMessageBytes = secretMessage.getBytes(StandardCharsets.UTF_8);
            byte[] encryptedMessageBytes = encryptCipher.doFinal(secretMessageBytes);
            String encodedMessage = Base64.getEncoder().encodeToString(encryptedMessageBytes);
            System.out.println(encodedMessage);
            Cipher decryptCipher = Cipher.getInstance("RSA");
            decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedMessageBytes = decryptCipher.doFinal(encryptedMessageBytes);
            String decryptedMessage = new String(decryptedMessageBytes, StandardCharsets.UTF_8);
            System.out.println(decryptedMessage);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String decrypt(String secret) {
        try {
            // 1. Decode Base64 string to get the serialized payload bytes
            byte[] payloadBytes = Base64.getDecoder().decode(secret);

            // 2. Parse the payload (mimic payloadFromByteArray)
            ByteBuffer buffer = ByteBuffer.wrap(payloadBytes);

            int ivSize = buffer.getInt();
            byte[] iv = new byte[ivSize];
            buffer.get(iv);

            int keySize = buffer.getInt();
            byte[] encryptedAesKey = new byte[keySize];
            buffer.get(encryptedAesKey);

            byte[] encryptedData = new byte[buffer.remaining()];
            buffer.get(encryptedData);

            // 3. Load Private Key
            File privateKeyFile = new File(getPrivateKeyPath());
            byte[] privateKeyBytes = Files.readAllBytes(privateKeyFile.toPath());
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

            // 4. Unwrap AES Key
            Cipher keyUnwrapper = Cipher.getInstance(RSA_TRANSFORMATION_STRING);
            keyUnwrapper.init(Cipher.UNWRAP_MODE, privateKey);
            SecretKey aesKey = (SecretKey) keyUnwrapper.unwrap(encryptedAesKey, "AES", Cipher.SECRET_KEY);

            // 5. Decrypt Data
            Cipher dataCipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            dataCipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec);
            byte[] decryptedDataBytes = dataCipher.doFinal(encryptedData);

            return new String(decryptedDataBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getPrivateKeyPath() {
        String path=MainJ.getPath()+"keystore/";
        System.out.println("PATH: "+path);
        new File(path).mkdirs();
        return path + "private.key";
    }

    public static String getPublicKeyPath() {
        String path=MainJ.getPath()+"keystore/";
        System.out.println("PATH: "+path);
        new File(path).mkdirs();
        return path + "public.key";
    }
}
