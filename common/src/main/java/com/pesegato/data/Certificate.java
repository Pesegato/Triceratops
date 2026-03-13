package com.pesegato.data;

import com.pesegato.device.DeviceManager;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Certificate {
    String name;
    //String version="1";
    String pin;
    String hwid;
    String publicKey;

    public Certificate(String name, String publicKey) {
        this.name = name;
        this.publicKey = publicKey;
        this.hwid = DeviceManager.hwId;
    }

    public static byte[] serialize(String name, String hwid, byte[] pubKey) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Scrivi Name
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        dos.writeByte(nameBytes.length); // Lunghezza in 1 byte (max 256)
        dos.write(nameBytes);

        // Scrivi HWID
        byte[] hwidBytes = hwid.getBytes(StandardCharsets.UTF_8);
        dos.writeByte(hwidBytes.length);
        dos.write(hwidBytes);

        // Scrivi PubKey
        dos.writeInt(pubKey.length); // Lunghezza in 4 byte
        dos.write(pubKey);

        dos.close();
        return baos.toByteArray();
    }

    public String getName() {
        return name;
    }

    public String getPublicKey() {
        return publicKey;
    }


    public String getFingerprint() {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(publicKey.getBytes());
            byte[] digest = md.digest();

            // Convert byte array into a hex string, ensuring it's zero-padded
            BigInteger no = new BigInteger(1, digest);
            String hashtext = no.toString(16);
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }

            // --- IMPROVE READABILITY BY ADDING COLONS ---
            // Use a StringBuilder for efficient string manipulation
            StringBuilder formattedFingerprint = new StringBuilder();
            for (int i = 0; i < hashtext.length(); i += 2) {
                // Append the pair of characters
                formattedFingerprint.append(hashtext.substring(i, i + 2));
                // Add a colon, but not after the last pair
                if (i < hashtext.length() - 2) {
                    formattedFingerprint.append(":");
                }
            }
            return formattedFingerprint.toString();

        } catch (NoSuchAlgorithmException e) {
            System.err.println("MD5 algorithm not found: " + e.getMessage());
            return null; // Or return an empty string ""
        }
    }


}
