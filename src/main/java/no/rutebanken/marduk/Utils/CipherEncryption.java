package no.rutebanken.marduk.Utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

@Component
public class CipherEncryption {

    @Value("${encryption.key:keyfortests}")
    private String encryptionKeyString;

    public byte[] encrypt(String password) throws Exception {

        byte[] encryptionKeyBytes = encryptionKeyString.getBytes();
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        SecretKey secretKey = new SecretKeySpec(encryptionKeyBytes, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        return cipher.doFinal(password.getBytes());
    }

    public String decrypt(byte[] password) throws Exception {

        byte[] encryptionKeyBytes = encryptionKeyString.getBytes();
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        SecretKey secretKey = new SecretKeySpec(encryptionKeyBytes, "AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decryptedMessageBytes = cipher.doFinal(password);

        return new String(decryptedMessageBytes);
    }
}
