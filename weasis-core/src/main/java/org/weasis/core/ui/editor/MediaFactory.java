/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.AbstractFileModel;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.FileCache;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.serialize.XmlSerializer;

/**
 * Utility class for creating {@link MediaReader} instances from files and for building {@link
 * MediaSeries} using the default data model.
 */
public final class MediaFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(MediaFactory.class);

  private MediaFactory() {} // static utility – no instances

  // ------------------------------------------------------------------
  //  Media I/O
  // ------------------------------------------------------------------

  /**
   * Creates a {@link MediaReader} for the given file, falling back to the system reader if no codec
   * is found.
   */
  public static MediaReader<MediaElement> getMedia(Path file) {
    return getMedia(file, true);
  }

  /**
   * Creates a {@link MediaReader} for the given file.
   *
   * @param file the file to read
   * @param systemReader if {@code true}, falls back to a {@link DefaultMimeIO} when no codec
   *     matches
   */
  public static MediaReader<MediaElement> getMedia(Path file, boolean systemReader) {
    if (file != null && Files.isReadable(file)) {
      // If file has been downloaded or copied
      boolean cache = file.startsWith(AppProperties.FILE_CACHE_DIR.toString());
      String mimeType = MimeInspector.getMimeType(file);
      if (mimeType != null) {
        Codec<?> codec = BundleTools.getCodec(mimeType, "dcm4che"); // NON-NLS
        if (codec != null) {
          @SuppressWarnings("unchecked")
          MediaReader<MediaElement> mreader =
              (MediaReader<MediaElement>) codec.getMediaIO(file.toUri(), mimeType, null);
          if (cache) {
            mreader.getFileCache().setOriginalTempFile(file);
          }
          return mreader;
        }
      }
      if (systemReader) {
        MediaReader<MediaElement> mreader = new DefaultMimeIO(file.toUri(), mimeType);
        if (cache) {
          mreader.getFileCache().setOriginalTempFile(file);
        }
        return mreader;
      }
    }
    return null;
  }

  // ------------------------------------------------------------------
  //  Series construction
  // ------------------------------------------------------------------

  /** Builds a {@link MediaSeries} using the default data model (no grouping). */
  public static MediaSeries<MediaElement> buildMediaSeriesWithDefaultModel(MediaReader reader) {
    return buildMediaSeriesWithDefaultModel(reader, null, null, null);
  }

  /** Builds a {@link MediaSeries} using the default data model with a group. */
  public static MediaSeries<MediaElement> buildMediaSeriesWithDefaultModel(
      MediaReader reader, String groupUID, TagW groupName, String groupValue) {
    return buildMediaSeriesWithDefaultModel(reader, groupUID, groupName, groupValue, null);
  }

  /**
   * Builds a {@link MediaSeries} using the default data model, creating the hierarchy group if
   * necessary and adding all media elements.
   */
  @SuppressWarnings("unchecked")
  public static MediaSeries<MediaElement> buildMediaSeriesWithDefaultModel(
      MediaReader<MediaElement> reader,
      String groupUID,
      TagW groupName,
      String groupValue,
      String seriesUID) {
    if (reader instanceof DefaultMimeIO) {
      return reader.getMediaSeries();
    }
    MediaSeries<MediaElement> series = null;
    // Require to read the header
    MediaElement[] medias = reader.getMediaElement();
    if (medias == null) {
      return null;
    }

    FileModel defaultModel = ViewerPluginBuilder.DefaultDataModel;
    String sUID = seriesUID == null ? UUID.randomUUID().toString() : seriesUID;
    String gUID = groupUID == null ? UUID.randomUUID().toString() : groupUID;
    MediaSeriesGroup group1 = defaultModel.getHierarchyNode(MediaSeriesGroupNode.rootNode, gUID);
    if (group1 == null) {
      group1 = new MediaSeriesGroupNode(TagW.Group, gUID, AbstractFileModel.group.tagView());
      group1.setTagNoNull(groupName, groupValue);
      defaultModel.addHierarchyNode(MediaSeriesGroupNode.rootNode, group1);
    }

    MediaSeriesGroup group2 = defaultModel.getHierarchyNode(group1, sUID);
    if (group2 instanceof Series) {
      series = (Series<MediaElement>) group2;
    }

    try {
      if (series == null) {
        series = reader.getMediaSeries();
        series.setTag(TagW.ExplorerModel, defaultModel);
        defaultModel.addHierarchyNode(group1, series);
      } else {
        // Test if SOPInstanceUID already exists
        TagW sopTag = TagW.get("SOPInstanceUID");
        if (((Series<?>) series).hasMediaContains(sopTag, reader.getTagValue(sopTag))) {
          return series;
        }

        for (MediaElement media : medias) {
          series.addMedia(media);
        }
      }

      for (MediaElement media : medias) {
        openAssociatedGraphics(media);
      }

    } catch (Exception e) {
      LOGGER.error("Build series error", e);
    }
    return series;
  }

  // ------------------------------------------------------------------
  //  Associated graphics
  // ------------------------------------------------------------------

  /** Loads XML presentation-state graphics associated with the given media element, if any. */
  public static void openAssociatedGraphics(MediaElement media) {
    if (media instanceof ImageElement) {
      FileCache fc = media.getFileCache();
      Optional<Path> fo = fc.getOriginalFile();
      if (fc.isLocalFile() && fo.isPresent()) {
        Path gpxFile = Path.of(fo.get() + ".xml");
        GraphicModel graphicModel = XmlSerializer.readPresentationModel(gpxFile.toFile());
        if (graphicModel != null) {
          media.setTag(TagW.PresentationModel, graphicModel);
        }
      }
    }
  }
}
