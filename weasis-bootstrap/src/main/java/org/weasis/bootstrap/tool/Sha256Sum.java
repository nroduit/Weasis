/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.bootstrap.tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Writes the {@code sha256sum} manifest of a launcher set: {@code Sha256Sum <output> <directory>}
 * digests every {@code .jar} of the directory, the launcher jar first. Run by the distribution
 * build; stage 1 reads the result ({@code weasis.launcher.sha256}).
 */
public final class Sha256Sum {

  private Sha256Sum() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      System.err.println("usage: Sha256Sum <output file> <directory of jars>"); // NON-NLS
      System.exit(2);
    }
    Path output = Path.of(args[0]);
    Path dir = Path.of(args[1]);
    try (Stream<Path> files = Files.list(dir)) {
      List<Path> jars =
          files
              .filter(p -> p.getFileName().toString().endsWith(".jar")) // NON-NLS
              .sorted(Sha256Sum::launcherFirst)
              .toList();
      StringBuilder sb = new StringBuilder();
      for (Path jar : jars) {
        sb.append(sha256(jar)).append("  ").append(jar.getFileName()).append('\n');
      }
      Files.createDirectories(output.toAbsolutePath().getParent());
      Files.writeString(output, sb.toString(), StandardCharsets.UTF_8);
      System.out.println("Wrote " + jars.size() + " digests to " + output); // NON-NLS
    }
  }

  private static int launcherFirst(Path a, Path b) {
    boolean la = a.getFileName().toString().startsWith("weasis-launcher"); // NON-NLS
    boolean lb = b.getFileName().toString().startsWith("weasis-launcher"); // NON-NLS
    if (la != lb) {
      return la ? -1 : 1;
    }
    return a.getFileName().compareTo(b.getFileName());
  }

  static String sha256(Path file) throws IOException {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256"); // NON-NLS
      try (var in = Files.newInputStream(file)) {
        byte[] buffer = new byte[64 * 1024];
        int n;
        while ((n = in.read(buffer)) > 0) {
          md.update(buffer, 0, n);
        }
      }
      return HexFormat.of().formatHex(md.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
