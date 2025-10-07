package com.pesegato.data;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Certificate {
    String name;
    //String version="1";
    String publicKey;

    public Certificate(String name, String publicKey) {
        this.name = name;
        this.publicKey = publicKey;
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
