/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import com.formdev.flatlaf.util.SystemInfo;
import java.awt.Desktop;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class Utils {
  private static final Logger LOGGER = System.getLogger(Utils.class.getName());

  private Utils() {}

  public static boolean getEmptytoFalse(String val) {
    if (hasText(val)) {
      return getBoolean(val);
    }
    return false;
  }

  public static boolean geEmptytoTrue(String val) {
    if (hasText(val)) {
      return getBoolean(val);
    }
    return true;
  }

  private static boolean getBoolean(String val) {
    return Boolean.TRUE.toString().equalsIgnoreCase(val);
  }

  public static boolean hasLength(CharSequence str) {
    return str != null && str.length() > 0;
  }

  public static boolean hasLength(String str) {
    return hasLength((CharSequence) str);
  }

  public static boolean hasText(CharSequence str) {
    if (!hasLength(str)) {
      return false;
    }
    int strLen = str.length();
    for (int i = 0; i < strLen; i++) {
      if (!Character.isWhitespace(str.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasText(String str) {
    return hasText((CharSequence) str);
  }

  public static String getWeasisProtocol(String... params) {
    Pattern pattern = Pattern.compile("^weasis(-.*)?://.*?");
    for (String p : params) {
      if (pattern.matcher(p).matches()) {
        return p;
      }
    }
    return null;
  }

  public static int getWeasisProtocolIndex(String... params) {
    Pattern pattern = Pattern.compile("^weasis(-.*)?://.*?");
    for (int i = 0; i < params.length; i++) {
      if (pattern.matcher(params[i]).matches()) {
        return i;
      }
    }
    return -1;
  }

  public static String removeEnglobingQuotes(String value) {
    return value.replaceAll("(?:^\")|(?:\"$)", "");
  }

  public static String adaptPathToUri(String value) {
    return value.replace("\\", "/").replace(" ", "%20");
  }

  public static List<String> splitSpaceExceptInQuotes(String s) {
    if (s == null) {
      return Collections.emptyList();
    }
    List<String> matchList = new ArrayList<>();
    Pattern patternSpaceExceptQuotes = Pattern.compile("'[^']*'|\"[^\"]*\"|( )");
    Matcher m = patternSpaceExceptQuotes.matcher(s);
    StringBuilder b = new StringBuilder();
    while (m.find()) {
      if (m.group(1) == null) {
        m.appendReplacement(b, m.group(0));
        String arg = b.toString();
        b.setLength(0);
        if (Utils.hasText(arg)) {
          matchList.add(arg.trim());
        }
      }
    }
    b.setLength(0);
    m.appendTail(b);
    String arg = b.toString();
    if (Utils.hasText(arg)) {
      matchList.add(arg.trim());
    }
    return matchList;
  }

  public static byte[] getByteArrayProperty(Properties prop, String key, byte[] def) {
    byte[] result = def;
    if (key != null) {
      String value = prop.getProperty(key);
      if (Utils.hasText(value)) {
        try {
          result =
              FileUtil.gzipUncompressToByte(
                  Base64.getDecoder().decode(value.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
          System.getLogger(Utils.class.getName()).log(Level.ERROR, "Get byte property", e);
        }
      }
    }
    return result;
  }

  public static byte[] decrypt(byte[] input, String strKey) throws GeneralSecurityException {
    SecretKeySpec skeyspec =
        new SecretKeySpec(
            Objects.requireNonNull(strKey).getBytes(StandardCharsets.UTF_8), "Blowfish"); // NON-NLS
    Cipher cipher = Cipher.getInstance("Blowfish"); // NON-NLS
    cipher.init(Cipher.DECRYPT_MODE, skeyspec);
    return cipher.doFinal(input);
  }

  public static void openInDefaultBrowser(URL url) {
    if (url != null) {
      if (SystemInfo.isLinux) {
        try {
          String[] cmd = new String[] {"xdg-open", url.toString()}; // NON-NLS
          Runtime.getRuntime().exec(cmd);
        } catch (IOException e) {
          LOGGER.log(Level.ERROR, "Cannot open URL to the system browser", e);
        }
      } else if (Desktop.isDesktopSupported()) {
        final Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Desktop.Action.BROWSE)) {
          try {
            desktop.browse(url.toURI());
          } catch (IOException | URISyntaxException e) {
            LOGGER.log(Level.ERROR, "Cannot open URL to the desktop browser", e);
          }
        }
      } else {
        LOGGER.log(Level.WARNING, "Cannot open URL to the system browser");
      }
    }
  }
}
