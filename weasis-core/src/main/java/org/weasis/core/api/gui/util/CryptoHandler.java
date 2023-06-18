/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.weasis.core.util.StringUtil;

public class CryptoHandler {

  private static final String BLOWFISH = "Blowfish"; // NON-NLS

  public static String encrypt(String strClearText, String strKey) throws GeneralSecurityException {
    if (!StringUtil.hasText(strClearText)) {
      return null;
    }
    return new String(
        encrypt(Objects.requireNonNull(strClearText).getBytes(StandardCharsets.UTF_8), strKey),
        StandardCharsets.UTF_8);
  }

  public static String decrypt(String strEncrypted, String strKey) throws GeneralSecurityException {
    if (!StringUtil.hasText(strEncrypted)) {
      return null;
    }
    return new String(
        decrypt(Objects.requireNonNull(strEncrypted).getBytes(StandardCharsets.UTF_8), strKey),
        StandardCharsets.UTF_8);
  }

  public static byte[] encrypt(byte[] input, String strKey) throws GeneralSecurityException {
    SecretKeySpec skeyspec =
        new SecretKeySpec(
            Objects.requireNonNull(strKey).getBytes(StandardCharsets.UTF_8), BLOWFISH);
    Cipher cipher = Cipher.getInstance(BLOWFISH);
    cipher.init(Cipher.ENCRYPT_MODE, skeyspec);
    return cipher.doFinal(input);
  }

  public static byte[] decrypt(byte[] input, String strKey) throws GeneralSecurityException {
    SecretKeySpec skeyspec =
        new SecretKeySpec(
            Objects.requireNonNull(strKey).getBytes(StandardCharsets.UTF_8), BLOWFISH);
    Cipher cipher = Cipher.getInstance(BLOWFISH);
    cipher.init(Cipher.DECRYPT_MODE, skeyspec);
    return cipher.doFinal(input);
  }
}
