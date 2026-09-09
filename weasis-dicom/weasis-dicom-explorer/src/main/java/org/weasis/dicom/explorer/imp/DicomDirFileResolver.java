/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.imp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves Referenced File IDs (0004,1500) of a DICOMDIR to files inside the directory owning the
 * DICOMDIR. Referenced File ID is attacker-controlled data, so the result must stay inside that
 * directory both lexically and once symbolic links are followed. Records of a file-set share a few
 * directories, so per-directory answers (real path, listing) are computed once: on a network share
 * or optical media every extra lookup is a round trip.
 */
final class DicomDirFileResolver {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomDirFileResolver.class);

  private final Path baseDir;
  private final Path baseRealDir;
  private final Map<Path, Boolean> directoriesInside = new HashMap<>();
  private final Map<Path, List<String>> listings = new HashMap<>();

  /** A candidate path inside the file-set; {@code exists} tells whether it was found on disk. */
  private record Candidate(Path file, boolean exists) {}

  DicomDirFileResolver(Path dicomDirFile) {
    this.baseDir = dicomDirFile.toAbsolutePath().normalize().getParent();
    this.baseRealDir = realPath(baseDir);
  }

  /**
   * Resolves a Referenced File ID to an existing file, tolerating the lower-case names of a CD-ROM
   * mounted on Linux and a missing file extension.
   *
   * @return the resolved file, or empty when the record points outside the file-set or to a missing
   *     file
   */
  Optional<Path> resolve(String[] fileID) {
    if (fileID == null || fileID.length == 0) {
      return Optional.empty();
    }
    Candidate candidate = resolveInside(fileID);
    if (candidate == null) {
      LOGGER.error("Rejecting DICOMDIR entry outside of {}: {}", baseDir, String.join("/", fileID));
      return Optional.empty();
    }
    if (candidate.exists()) {
      return Optional.of(candidate.file());
    }

    Candidate lowerCase = resolveInside(toLowerCase(fileID));
    if (lowerCase != null && lowerCase.exists()) {
      return Optional.of(lowerCase.file());
    }

    Optional<Path> withExtension = findWithExtension(candidate.file());
    if (withExtension.isEmpty()) {
      LOGGER.error("Missing DICOMDIR entry: {}", candidate.file());
    }
    return withExtension;
  }

  /**
   * Resolves the components under baseDir. Non-conformant components holding a separator or a
   * relative segment are tolerated, as long as the result stays inside baseDir.
   */
  private Candidate resolveInside(String[] fileID) {
    Path file = baseDir;
    try {
      for (String component : fileID) {
        if (component == null) {
          return null;
        }
        file = file.resolve(component);
      }
    } catch (InvalidPathException e) {
      return null;
    }
    Path resolved = file.normalize();
    if (!resolved.startsWith(baseDir)) {
      return null;
    }
    BasicFileAttributes attrs = readAttributesNoFollow(resolved);
    if (attrs == null) {
      return isDeepestAncestorInside(resolved) ? new Candidate(resolved, false) : null;
    }
    boolean inside =
        attrs.isSymbolicLink()
            ? isRealPathInside(resolved)
            : isDirectoryInside(resolved.getParent());
    return inside ? new Candidate(resolved, true) : null;
  }

  /** A regular file cannot escape when the real path of its directory is inside the file-set. */
  private boolean isDirectoryInside(Path directory) {
    if (directory == null) {
      return false;
    }
    if (directory.equals(baseDir)) {
      return true;
    }
    return directoriesInside.computeIfAbsent(directory, this::isRealPathInside);
  }

  /**
   * Checks the deepest existing ancestor of a missing path, which is where a symbolic link would
   * lead out of the file-set without {@link Path#normalize()} ever noticing.
   */
  private boolean isDeepestAncestorInside(Path missing) {
    Path existing = missing.getParent();
    while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
      existing = existing.getParent();
    }
    return existing != null && isDirectoryInside(existing);
  }

  private boolean isRealPathInside(Path path) {
    Path real = realPath(path);
    return real != null && baseRealDir != null && real.startsWith(baseRealDir);
  }

  private static Path realPath(Path path) {
    try {
      return path.toRealPath();
    } catch (IOException e) {
      return null;
    }
  }

  private static BasicFileAttributes readAttributesNoFollow(Path path) {
    try {
      return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    } catch (IOException e) {
      return null;
    }
  }

  private static String[] toLowerCase(String[] fileID) {
    String[] lowerCase = new String[fileID.length];
    for (int i = 0; i < fileID.length; i++) {
      lowerCase[i] = fileID[i] == null ? null : fileID[i].toLowerCase(Locale.ROOT);
    }
    return lowerCase;
  }

  /** Finds the sibling file named after the record plus an extension, when it is unambiguous. */
  private Optional<Path> findWithExtension(Path file) {
    Path parent = file.getParent();
    String name = file.getFileName().toString();
    String lowerCaseName = name.toLowerCase(Locale.ROOT);
    List<String> matches =
        listings.computeIfAbsent(parent, DicomDirFileResolver::listNames).stream()
            .filter(
                sibling ->
                    sibling.startsWith(name + ".") || sibling.startsWith(lowerCaseName + "."))
            .limit(2)
            .toList();
    return matches.size() == 1 ? Optional.of(parent.resolve(matches.getFirst())) : Optional.empty();
  }

  private static List<String> listNames(Path directory) {
    try (Stream<Path> siblings = Files.list(directory)) {
      return siblings.map(p -> p.getFileName().toString()).toList();
    } catch (IOException e) {
      return List.of();
    }
  }
}
