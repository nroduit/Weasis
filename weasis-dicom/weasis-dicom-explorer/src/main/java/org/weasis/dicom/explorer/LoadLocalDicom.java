/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.serialize.XmlSerializer;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.codec.DicomCodec;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;

public class LoadLocalDicom extends LoadDicom {

  private final File[] files;
  private final boolean recursive;

  public LoadLocalDicom(
      File[] files, boolean recursive, DataExplorerModel explorerModel, OpeningViewer openingMode) {
    this(files, recursive, explorerModel, new PluginOpeningStrategy(openingMode));
  }

  public LoadLocalDicom(
      File[] files,
      boolean recursive,
      DataExplorerModel explorerModel,
      PluginOpeningStrategy openingStrategy) {
    super(explorerModel, false, openingStrategy);
    this.files = Objects.requireNonNull(files);
    this.recursive = recursive;
  }

  @Override
  protected Boolean doInBackground() throws Exception {
    startLoadingEvent();
    if (files.length > 0) {
      openingStrategy.prepareImport();
      addSelectionAndNotify(files, true);
    }
    return true;
  }

  protected void addSelectionAndNotify(File[] file, boolean firstLevel) {
    if (file == null || file.length < 1) {
      return;
    }

    ArrayList<SeriesThumbnail> thumbs = new ArrayList<>();
    ArrayList<File> folders = new ArrayList<>();
    for (File value : file) {
      if (isCancelled()) {
        return;
      }

      if (value != null && value.isDirectory()) {
        if (firstLevel || recursive) {
          folders.add(value);
        }
      } else if (value != null
              && value.canRead()
              && FileUtil.isFileExtensionMatching(value, DicomCodec.FILE_EXTENSIONS)
          || MimeInspector.isMatchingMimeTypeFromMagicNumber(value, DicomMediaIO.DICOM_MIMETYPE)) {
        DicomMediaIO loader = new DicomMediaIO(value);
        if (loader.isReadableDicom()) {
          // Issue: must handle adding image to viewer and building thumbnail (middle image)
          SeriesThumbnail t = buildDicomStructure(loader);
          if (t != null) {
            thumbs.add(t);
          }

          File gpxFile = new File(value.getPath() + ".xml");
          GraphicModel graphicModel = XmlSerializer.readPresentationModel(gpxFile);
          if (graphicModel != null) {
            loader.setTag(TagW.PresentationModel, graphicModel);
          }
        }
      }
    }
    for (final SeriesThumbnail t : thumbs) {
      MediaSeries<MediaElement> series = t.getSeries();
      // Avoid rebuilding most of CR series thumbnail
      if (series != null && series.size(null) > 2) {
        GuiExecutor.instance().execute(t::reBuildThumbnail);
      }
    }
    for (File folder : folders) {
      addSelectionAndNotify(folder.listFiles(), false);
    }
  }
}
