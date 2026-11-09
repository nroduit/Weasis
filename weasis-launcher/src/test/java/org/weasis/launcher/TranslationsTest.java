/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;
import org.weasis.pref.ConfigData;

class TranslationsTest {

  private static final String PACKAGE = "weasis-launcher-i18n";

  @TempDir Path dist;
  @TempDir Path cacheDir;

  private Locale previousLocale;
  private String previousI18n;

  @BeforeEach
  void setUp() {
    previousLocale = Locale.getDefault();
    previousI18n = System.getProperty(ConfigData.P_WEASIS_I18N);
    System.setProperty(ConfigData.P_WEASIS_I18N, dist.toUri().toString());
    Locale.setDefault(Locale.FRENCH);
  }

  @AfterEach
  void tearDown() {
    Locale.setDefault(previousLocale);
    if (previousI18n == null) {
      System.clearProperty(ConfigData.P_WEASIS_I18N);
    } else {
      System.setProperty(ConfigData.P_WEASIS_I18N, previousI18n);
    }
    Messages.setBundle(null);
    ResourceBundle.clearCache();
  }

  @Test
  void moduleOfStripsTheVersionOfListedModules() {
    Properties modules = modules("r1");
    modules.setProperty("weasis-core-i18n.jar.xz", "r1");
    assertEquals("weasis-core", Translations.moduleOf("weasis-core-5.0.0", modules));
    assertEquals("weasis-core", Translations.moduleOf("weasis-core-5.0.0-SNAPSHOT", modules));
    assertEquals("weasis-core", Translations.moduleOf("weasis-core", modules));
    // Another library sharing the prefix is not the module
    assertNull(Translations.moduleOf("weasis-core-img-4.13.0", modules));
    assertNull(Translations.moduleOf("joml-1.10.9", modules));
  }

  @Test
  void fetchDownloadsOnceAndAgainWhenTheQualifierChanges() throws IOException {
    writePackage("first");
    Path jar = Translations.fetch(cacheDir, "weasis-launcher", modules("r1"), false);
    assertNotNull(jar);
    assertEquals("first", bundle(jar).getProperty("WeasisLauncher.ok"));

    // Same qualifier: the cached copy is used even if the distribution changed
    writePackage("second");
    jar = Translations.fetch(cacheDir, "weasis-launcher", modules("r1"), false);
    assertEquals("first", bundle(jar).getProperty("WeasisLauncher.ok"));

    // New qualifier: downloaded again
    jar = Translations.fetch(cacheDir, "weasis-launcher", modules("r2"), false);
    assertEquals("second", bundle(jar).getProperty("WeasisLauncher.ok"));

    // Distribution unreachable: the cached copy is kept
    Files.delete(dist.resolve(PACKAGE + ".jar.xz"));
    jar = Translations.fetch(cacheDir, "weasis-launcher", modules("r3"), false);
    assertNotNull(jar);
    assertEquals("second", bundle(jar).getProperty("WeasisLauncher.ok"));
  }

  @Test
  void fetchIgnoresModulesWithoutPackage() {
    assertNull(Translations.fetch(cacheDir, "weasis-other", modules("r1"), false));
    assertNull(Translations.fetch(cacheDir, null, modules("r1"), false));
    assertNull(Translations.fetch(cacheDir, "weasis-launcher", new Properties(), false));
  }

  @Test
  void loadLauncherTranslatesMessagesAndSwingWithEnglishFallback() throws IOException {
    writePackage("J'accepte");
    Translations.loadLauncher(cacheDir, modules("r1"));

    assertEquals("J'accepte", Messages.getString("WeasisLauncher.ok"));
    // Missing in the package: the English base of the launcher jar
    assertEquals("No", Messages.getString("WeasisLauncher.no"));
    assertEquals("Annuler", UIManager.get("OptionPane.cancelButtonText"));
  }

  @Test
  void getBundleOfALibraryReadsTheCachedPackages() throws IOException {
    writePackage("J'accepte");
    Translations.fetch(cacheDir, "weasis-launcher", modules("r1"), false);

    ResourceBundle bundle = Translations.getBundle(Messages.BUNDLE_NAME, Messages.class);
    assertEquals("J'accepte", bundle.getString("WeasisLauncher.ok"));
    assertEquals("No", bundle.getString("WeasisLauncher.no"));

    Locale.setDefault(Locale.ENGLISH);
    bundle = Translations.getBundle(Messages.BUNDLE_NAME, Messages.class);
    assertEquals("I accept", bundle.getString("WeasisLauncher.ok"));
  }

  @Test
  void withoutPackageTheLauncherStaysInEnglish() {
    Translations.loadLauncher(cacheDir, modules("r1"));
    assertEquals("I accept", Messages.getString("WeasisLauncher.ok"));
    assertTrue(Messages.getString("WeasisLauncher.unknown").startsWith("!"));
  }

  private static Properties modules(String qualifier) {
    Properties modules = new Properties();
    modules.setProperty(PACKAGE + ".jar.xz", qualifier);
    return modules;
  }

  /** Reads the cached jar directly, the JDK caches the jar files opened through a class loader. */
  private static Properties bundle(Path jar) throws IOException {
    try (JarFile file = new JarFile(jar.toFile())) {
      Properties properties = new Properties();
      try (var in =
          file.getInputStream(file.getEntry("org/weasis/launcher/messages_fr.properties"))) {
        properties.load(in);
      }
      return properties;
    }
  }

  /** Writes a compressed package like the weasis-i18n build does (messages and Swing dialogs). */
  private void writePackage(String okText) throws IOException {
    Map<String, String> entries =
        Map.of(
            "org/weasis/launcher/messages_fr.properties",
            "WeasisLauncher.ok=" + okText + "\n",
            "swing/basic_fr.properties",
            "OptionPane.cancelButton.textAndMnemonic=Annuler\n");
    Path target = dist.resolve(PACKAGE + ".jar.xz");
    try (OutputStream out = new XZOutputStream(Files.newOutputStream(target), new LZMA2Options());
        JarOutputStream jar = new JarOutputStream(out)) {
      for (Map.Entry<String, String> e : entries.entrySet()) {
        jar.putNextEntry(new JarEntry(e.getKey()));
        jar.write(e.getValue().getBytes(StandardCharsets.ISO_8859_1));
        jar.closeEntry();
      }
    }
  }
}
