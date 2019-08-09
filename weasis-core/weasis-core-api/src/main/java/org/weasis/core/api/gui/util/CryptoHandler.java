package org.weasis.core.api.gui.util;

import java.security.GeneralSecurityException;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.weasis.core.api.util.StringUtil;

public class CryptoHandler {

    private static final String BLOWFISH = "Blowfish"; //$NON-NLS-1$

    public static String encrypt(String strClearText, String strKey) throws GeneralSecurityException {
        if(!StringUtil.hasText(strClearText)) {
            return null;
        }
        return new String(encrypt(Objects.requireNonNull(strClearText).getBytes(), strKey));
    }

    public static String decrypt(String strEncrypted, String strKey) throws GeneralSecurityException {
        if(!StringUtil.hasText(strEncrypted)) {
            return null;
        }
        return new String(decrypt(Objects.requireNonNull(strEncrypted).getBytes(), strKey));
    }

    public static byte[] encrypt(byte[] input, String strKey) throws GeneralSecurityException {
        SecretKeySpec skeyspec = new SecretKeySpec(Objects.requireNonNull(strKey).getBytes(), BLOWFISH);
        Cipher cipher = Cipher.getInstance(BLOWFISH);
        cipher.init(Cipher.ENCRYPT_MODE, skeyspec);
        return cipher.doFinal(input);
    }

    public static byte[] decrypt(byte[] input, String strKey) throws GeneralSecurityException {
        SecretKeySpec skeyspec = new SecretKeySpec(Objects.requireNonNull(strKey).getBytes(), BLOWFISH);
        Cipher cipher = Cipher.getInstance(BLOWFISH);
        cipher.init(Cipher.DECRYPT_MODE, skeyspec);
        return cipher.doFinal(input);
    }

}