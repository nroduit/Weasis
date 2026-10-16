/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.hp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;

/**
 * Reads the studies of a folder without the explorer model: every readable DICOM file is grouped by
 * Study and Series Instance UID into {@link DicomSeries} the engine can match. Nothing is decoded
 * beyond the headers. The readers stay open until {@link Study#close()} so the features can be
 * derived; close each study once matched.
 */
public final class CorpusStudies {

  private static final Logger LOGGER = LoggerFactory.getLogger(CorpusStudies.class);

  /** One study of the corpus: its series, the files they came from and the readers to close. */
  public static final class Study implements AutoCloseable {
    private final String uid;
    private final Map<String, DicomSeries> series = new LinkedHashMap<>();
    private final List<DicomMediaIO> readers = new ArrayList<>();
    private int files;
    private Path firstFile;

    Study(String uid) {
      this.uid = uid;
    }

    public String uid() {
      return uid;
    }

    public List<DicomSeries> series() {
      return List.copyOf(series.values());
    }

    public int fileCount() {
      return files;
    }

    /** The folder of the first file read, a human handle on the study. */
    public Path folder() {
      return firstFile == null ? null : firstFile.getParent();
    }

    /** A description: the folder name, the patient-free study description and the file count. */
    public String describe() {
      DicomSeries first = series.isEmpty() ? null : series.values().iterator().next();
      String description =
          first == null ? null : TagD.getTagValue(first, Tag.StudyDescription, String.class);
      StringBuilder text = new StringBuilder();
      if (folder() != null) {
        text.append(folder().getFileName()).append(' ');
      }
      if (description != null && !description.isBlank()) {
        text.append('"').append(description).append("\" "); // NON-NLS
      }
      return text.append('(')
          .append(series.size())
          .append(" series, ") // NON-NLS
          .append(files)
          .append(" files)") // NON-NLS
          .toString();
    }

    void add(Path file, DicomMediaIO reader, DicomImageElement[] elements) {
      String seriesUid = TagD.getTagValue(reader, Tag.SeriesInstanceUID, String.class);
      if (seriesUid == null) {
        seriesUid = "unknown"; // NON-NLS
      }
      DicomSeries target = series.get(seriesUid);
      if (target == null) {
        target = new DicomSeries(seriesUid);
        reader.writeMetaData(target);
        series.put(seriesUid, target);
      }
      for (DicomImageElement element : elements) {
        target.addMedia(element);
      }
      readers.add(reader);
      files++;
      if (firstFile == null) {
        firstFile = file;
      }
    }

    @Override
    public void close() {
      for (DicomMediaIO reader : readers) {
        try {
          reader.close();
        } catch (RuntimeException e) {
          LOGGER.debug("Cannot close {}", reader.getUri(), e);
        }
      }
      readers.clear();
      series.values().forEach(DicomSeries::dispose);
    }
  }

  private CorpusStudies() {}

  /**
   * The studies under a folder, walked recursively; files that are not DICOM are skipped.
   *
   * @param root the folder
   * @param progress told the number of files read so far, may be null
   */
  public static List<Study> read(Path root, Consumer<Integer> progress) throws IOException {
    Map<String, Study> studies = new LinkedHashMap<>();
    int[] count = {0};
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path file : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
        DicomMediaIO reader = new DicomMediaIO(file);
        DicomImageElement[] elements;
        try {
          if (!reader.isReadableDicom()) {
            reader.close();
            continue;
          }
          elements = reader.getMediaElement();
        } catch (RuntimeException e) {
          LOGGER.debug("Skip {}: {}", file, e.getMessage());
          reader.close();
          continue;
        }
        if (elements == null || elements.length == 0) {
          reader.close();
          continue;
        }
        String studyUid = TagD.getTagValue(reader, Tag.StudyInstanceUID, String.class);
        if (studyUid == null) {
          studyUid = "unknown"; // NON-NLS
        }
        studies.computeIfAbsent(studyUid, Study::new).add(file, reader, elements);
        count[0]++;
        if (progress != null) {
          progress.accept(count[0]);
        }
      }
    }
    return new ArrayList<>(studies.values());
  }
}
