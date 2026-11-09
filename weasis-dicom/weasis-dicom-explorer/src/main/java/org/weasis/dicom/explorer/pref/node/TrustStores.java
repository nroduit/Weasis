/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.pref.node;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.launcher.PlatformCertificateLoader;

/**
 * The key store files dcm4che reads for a TLS association: the platform trust store (the OS
 * certificates merged with the JDK ones, the same the HTTPS client trusts) written once per run as
 * a PKCS12 file, and an empty key store, since dcm4che builds a key manager even for a client that
 * presents no certificate. Both hold no secret: the password is a constant.
 */
public final class TrustStores {

  private static final Logger LOGGER = LoggerFactory.getLogger(TrustStores.class);

  public static final String PKCS12 = "PKCS12"; // NON-NLS
  public static final String JKS = "JKS"; // NON-NLS

  /** The password of the files written here; they hold public certificates only. */
  public static final String PASSWORD = "changeit"; // NON-NLS

  private static volatile Path platformStore;
  private static volatile Path emptyStore;

  private TrustStores() {}

  /** The directory of the files, under the cache of this profile. */
  static Path directory() throws IOException {
    Path dir = AppProperties.FILE_CACHE_DIR.resolve("tls"); // NON-NLS
    Files.createDirectories(dir);
    return dir;
  }

  /** The key store type a file name says: PKCS12 for .p12 and .pfx, JKS otherwise. */
  public static String typeOf(Path file) {
    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".p12") || name.endsWith(".pfx") ? PKCS12 : JKS; // NON-NLS
  }

  /**
   * The platform trust store as a PKCS12 file, written at the first association of the run from the
   * certificates {@link PlatformCertificateLoader} merged at start-up; the JDK certificates alone
   * when the platform store could not be read.
   */
  public static Path platformTrustStore() throws IOException {
    Path file = platformStore;
    if (file != null && Files.isReadable(file)) {
      return file;
    }
    synchronized (TrustStores.class) {
      file = platformStore;
      if (file != null && Files.isReadable(file)) {
        return file;
      }
      Path target = directory().resolve("platform-truststore.p12"); // NON-NLS
      Optional<KeyStore> merged = PlatformCertificateLoader.trustStore();
      KeyStore store;
      try {
        store =
            merged.isPresent()
                ? merged.get()
                : PlatformCertificateLoader.loadJavaDefaultTrustStore();
      } catch (Exception e) {
        throw new IOException("Cannot read the trust store of the platform", e);
      }
      write(store, target);
      LOGGER.info("Platform trust store written for DICOM TLS: {}", target);
      platformStore = target;
      return target;
    }
  }

  /** An empty PKCS12 key store: the client presents no certificate. */
  public static Path emptyKeyStore() throws IOException {
    Path file = emptyStore;
    if (file != null && Files.isReadable(file)) {
      return file;
    }
    synchronized (TrustStores.class) {
      file = emptyStore;
      if (file != null && Files.isReadable(file)) {
        return file;
      }
      Path target = directory().resolve("empty-keystore.p12"); // NON-NLS
      try {
        KeyStore store = KeyStore.getInstance(PKCS12);
        store.load(null, null);
        write(store, target);
      } catch (GeneralSecurityException e) {
        throw new IOException("Cannot create an empty key store", e);
      }
      emptyStore = target;
      return target;
    }
  }

  private static void write(KeyStore source, Path target) throws IOException {
    try {
      KeyStore copy = KeyStore.getInstance(PKCS12);
      copy.load(null, null);
      var aliases = source.aliases();
      while (aliases.hasMoreElements()) {
        String alias = aliases.nextElement();
        if (source.isCertificateEntry(alias)) {
          copy.setCertificateEntry(alias, source.getCertificate(alias));
        }
      }
      Path tmp = Files.createTempFile(target.getParent(), "truststore", ".tmp"); // NON-NLS
      try (OutputStream out = Files.newOutputStream(tmp)) {
        copy.store(out, PASSWORD.toCharArray());
      }
      Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    } catch (GeneralSecurityException e) {
      throw new IOException("Cannot write the trust store " + target, e);
    }
  }

  /** Forgets the files written, for a test or after the platform certificates changed. */
  static void reset() {
    platformStore = null;
    emptyStore = null;
  }
}
