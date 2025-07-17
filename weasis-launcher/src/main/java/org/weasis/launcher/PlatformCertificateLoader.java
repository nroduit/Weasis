/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import com.formdev.flatlaf.util.SystemInfo;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Enumeration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class PlatformCertificateLoader {

  public static void main(String[] args) throws Exception {
    long startTime = System.nanoTime();
    KeyStore mergedTrustStore = setupDefaultSSLContext();
    System.out.printf(
        "Setup took: %.2f ms%n", (System.nanoTime() - startTime) / 1_000_000.0); // NON-NLS

    printTrustStoreAliases(mergedTrustStore);
  }

  /**
   * Sets up the default SSLContext using a merged trust store containing both platform-specific and
   * Java default trust stores.
   */
  public static KeyStore setupDefaultSSLContext() throws Exception {
    // Load platform-specific trust store
    KeyStore platformTrustStore = loadPlatformTrustStore();
    KeyStore javaTrustStore = loadJavaDefaultTrustStore();
    KeyStore mergedTrustStore = mergeTrustStores(platformTrustStore, javaTrustStore);

    // Create and set SSL context
    SSLContext sslContext = createSSLContext(mergedTrustStore);
    SSLContext.setDefault(sslContext);

    return mergedTrustStore;
  }

  public static KeyStore loadPlatformTrustStore() throws Exception {
    if (SystemInfo.isWindows) {
      return loadKeyStore("Windows-ROOT", null); // NON-NLS
    } else if (SystemInfo.isMacOS) {
      return loadKeyStore("KeychainStore", null);
    } else {
      return loadLinuxTrustStore();
    }
  }

  private static KeyStore loadLinuxTrustStore() throws Exception {
    String[] TRUSTED_LINUX_PATHS = {
      "/etc/ssl/certs/java/cacerts", "/etc/pki/java/cacerts"
    }; // NON-NLS
    for (String path : TRUSTED_LINUX_PATHS) {
      if (Files.isReadable(Paths.get(path))) {
        return loadKeyStore(KeyStore.getDefaultType(), path);
      }
    }
    throw new Exception("No accessible Linux trust store found.");
  }

  public static KeyStore loadJavaDefaultTrustStore() throws Exception {
    // Construct Java trust store path
    String trustStorePath =
        Paths.get(System.getProperty("java.home"), "lib", "security", "cacerts").toString();

    // Validate the Java default trust store file
    if (!Files.exists(Paths.get(trustStorePath))) {
      throw new Exception("Default Java trust store not found at: " + trustStorePath);
    }

    // Load the default trust store
    return loadKeyStore(
        KeyStore.getDefaultType(), trustStorePath, "changeit".toCharArray()); // NON-NLS
  }

  private static KeyStore loadKeyStore(String type, String filePath) throws Exception {
    return loadKeyStore(type, filePath, null);
  }

  private static KeyStore loadKeyStore(String type, String filePath, char[] password)
      throws Exception {
    KeyStore keyStore = KeyStore.getInstance(type);
    if (filePath == null) {
      keyStore.load(null, null); // No password required
    } else {
      try (FileInputStream fis = new FileInputStream(filePath)) {
        keyStore.load(fis, password);
      }
    }
    return keyStore;
  }

  public static KeyStore mergeTrustStores(KeyStore store1, KeyStore store2) throws Exception {
    KeyStore mergedTrustStore = KeyStore.getInstance(KeyStore.getDefaultType());
    mergedTrustStore.load(null, null);

    // Copy all certificates from both input stores to the merged store
    copyCertificates(store1, mergedTrustStore, "");
    copyCertificates(store2, mergedTrustStore, "java-"); // NON-NLS

    return mergedTrustStore;
  }

  private static void copyCertificates(KeyStore source, KeyStore destination, String aliasPrefix)
      throws Exception {
    Enumeration<String> aliases = source.aliases();
    while (aliases.hasMoreElements()) {
      String alias = aliases.nextElement();
      if (source.isCertificateEntry(alias)) {
        var certificate = source.getCertificate(alias);

        // Avoid duplicate certificates in the destination trust store
        if (!isCertificatePresentInStore(certificate, destination)) {
          String uniqueAlias = aliasPrefix + alias;
          destination.setCertificateEntry(uniqueAlias, certificate);
        }
      }
    }
  }

  private static boolean isCertificatePresentInStore(
      java.security.cert.Certificate certificate, KeyStore keyStore) throws Exception {
    Enumeration<String> aliases = keyStore.aliases();
    while (aliases.hasMoreElements()) {
      String alias = aliases.nextElement();
      if (keyStore.isCertificateEntry(alias)) {
        var existingCert = keyStore.getCertificate(alias);
        if (existingCert.equals(certificate)) {
          return true;
        }
      }
    }
    return false;
  }

  public static SSLContext createSSLContext(KeyStore trustStore) throws Exception {
    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(trustStore);

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, tmf.getTrustManagers(), null);

    return sslContext;
  }

  private static void printTrustStoreAliases(KeyStore trustStore) throws Exception {
    System.out.println("Merged Trust Store Aliases:"); // NON-NLS
    Enumeration<String> aliases = trustStore.aliases();
    while (aliases.hasMoreElements()) {
      System.out.println(" - " + aliases.nextElement());
    }
  }
}
