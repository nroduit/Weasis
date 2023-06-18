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

import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Map.Entry;
import java.util.Properties;
import javax.swing.UIManager;

public class SwingResources {

  private static final Logger LOGGER = System.getLogger(SwingResources.class.getName());

  static final String AND_MNEMONIC = "AndMnemonic";
  static final String TITLE_SUFFIX = ".titleAndMnemonic";
  static final String TEXT_SUFFIX = ".textAndMnemonic";

  static final String KEY_MNEMONIC = "Mnemonic"; // NON-NLS
  static final String KEY_MNEMONIC_INDEX = "DisplayedMnemonicIndex";
  static final String KEY_TEXT = "Text"; // NON-NLS

  static final String KEY_TITLE = "Title"; // NON-NLS

  private SwingResources() {}

  /**
   * <code>TextAndMnemonicHashMap</code> stores swing resource strings. Many of strings can have a
   * mnemonic. For example: FileChooser.saveButton.textAndMnemonic=&Save For this case method get
   * returns "Save" for the key "FileChooser.saveButtonText" and mnemonic "S" for the key
   * "FileChooser.saveButtonMnemonic"
   *
   * <p>There are several patterns for the text and mnemonic suffixes which are checked by the
   * <code>TextAndMnemonicHashMap</code> class. Patterns which are converted to the
   * xxx.textAndMnemonic key: (xxxNameText, xxxNameMnemonic) (xxxNameText, xxxMnemonic)
   * (xxx.nameText, xxx.mnemonic) (xxxText, xxxMnemonic)
   *
   * <p>These patterns can have a mnemonic index in format (xxxDisplayedMnemonicIndex)
   *
   * <p>Pattern which is converted to the xxx.titleAndMnemonic key: (xxxTitle, xxxMnemonic)
   */
  public static void loadResources(String path) {
    InputStream inStream = WeasisLauncher.class.getResourceAsStream(path);
    if (inStream != null) {
      Properties swingDialogs = new Properties();
      try {
        swingDialogs.load(inStream);
      } catch (IOException e) {
        LOGGER.log(Level.ERROR, "Cannot read swing translations", e);
      } finally {
        FileUtil.safeClose(inStream);
      }

      for (Entry<Object, Object> entry : swingDialogs.entrySet()) {
        String stringKey = entry.getKey().toString();
        Object value = entry.getValue();
        String compositeKey;

        if (stringKey.endsWith(AND_MNEMONIC)) {
          if (value != null) {
            String text = value.toString();
            String mnemonic = null;
            int index = text.indexOf('&');
            if (0 <= index && index < text.length() - 1) {
              char c = text.charAt(index + 1);
              mnemonic = Integer.toString(Character.toUpperCase(c));
            }

            if (stringKey.endsWith(TEXT_SUFFIX)) {
              compositeKey = composeKey(stringKey, TEXT_SUFFIX.length(), KEY_TEXT);
              UIManager.put(compositeKey, getTextFromProperty(text));
              if (mnemonic != null) {
                if (stringKey.startsWith("ColorChooser")) {
                  compositeKey = composeKey(stringKey, TEXT_SUFFIX.length(), "NameText");
                  UIManager.put(compositeKey, getTextFromProperty(text));
                }
                compositeKey = composeKey(stringKey, TEXT_SUFFIX.length(), KEY_MNEMONIC);
                UIManager.put(compositeKey, mnemonic);
                compositeKey = composeKey(stringKey, TEXT_SUFFIX.length(), KEY_MNEMONIC_INDEX);
                UIManager.put(compositeKey, Integer.toString(index));
              }
            } else if (stringKey.endsWith(TITLE_SUFFIX)) {
              compositeKey = composeKey(stringKey, TITLE_SUFFIX.length(), KEY_TITLE);
              UIManager.put(compositeKey, getTextFromProperty(text));
              if (mnemonic != null) {
                compositeKey = composeKey(stringKey, TITLE_SUFFIX.length(), KEY_MNEMONIC);
                UIManager.put(compositeKey, mnemonic);
                compositeKey = composeKey(stringKey, TITLE_SUFFIX.length(), KEY_MNEMONIC_INDEX);
                UIManager.put(compositeKey, Integer.toString(index));
              }
            }
          }
        } else {
          UIManager.put(entry.getKey(), value);
        }
      }
    }
  }

  static String composeKey(String key, int reduce, String sufix) {
    return key.substring(0, key.length() - reduce) + sufix;
  }

  static String getTextFromProperty(String text) {
    return text.replace("&", "");
  }

  static String getMnemonicFromProperty(String text) {
    int index = text.indexOf('&');
    if (0 <= index && index < text.length() - 1) {
      char c = text.charAt(index + 1);
      return Integer.toString(Character.toUpperCase(c));
    }
    return null;
  }
}
