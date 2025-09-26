import javax.crypto.Cipher;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class RSACrypt {

    public static PublicKey generateOrGetKeyPair(String alias) {
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

    public static String getPrivateKeyPath() {
        String path=System.getProperty("user.home") + "/.Triceratops/";
        new File(path).mkdirs();
        return System.getProperty("user.home") + "/.Triceratops/private.key";
    }

    public static String getPublicKeyPath() {
        String path=System.getProperty("user.home") + "/.Triceratops/";
        new File(path).mkdirs();
        return System.getProperty("user.home") + "/.Triceratops/public.key";
    }
}
