/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.gui.util;

import java.security.GeneralSecurityException;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.weasis.core.util.StringUtil;

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