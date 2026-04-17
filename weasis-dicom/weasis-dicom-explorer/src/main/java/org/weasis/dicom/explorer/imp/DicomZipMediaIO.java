/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.imp;

import java.awt.Component;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.Timer;
import net.lingala.zip4j.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.FileCache;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.net.ClosableURLConnection;
import org.weasis.core.api.net.NetworkUtil;
import org.weasis.core.api.net.URLParameters;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;
import org.weasis.dicom.explorer.LoadDicomDir;
import org.weasis.dicom.explorer.LoadLocalDicom;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.wado.LoadSeries;
import org.weasis.opencv.data.PlanarImage;

/**
 * A {@link MediaReader} that delegates loading of a DICOM ZIP archive. This allows drag-and-drop of
 * {@code .zip} files containing DICOM data to be recognized and imported automatically through the
 * codec infrastructure.
 */
public class DicomZipMediaIO implements MediaReader<MediaElement> {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomZipMediaIO.class);

  public static final String MIME_TYPE = "application/x-zip"; // NON-NLS
  private final URI uri;
  private final FileCache fileCache;
  private final DicomZipCodec codec;

  public DicomZipMediaIO(URI uri, DicomZipCodec codec) {
    this.uri = Objects.requireNonNull(uri);
    this.fileCache = new FileCache(this);
    this.codec = codec;
  }

  @Override
  public URI getUri() {
    return uri;
  }

  @Override
  public FileCache getFileCache() {
    return fileCache;
  }

  @Override
  public boolean isOnlyDelegate() {
    return true;
  }

  @Override
  public boolean delegate(DataExplorerModel explorerModel) {
    File zipFile = new File(uri);
    if (zipFile.canRead() && explorerModel instanceof DicomModel dicomModel) {
      loadDicomZip(
          zipFile,
          dicomModel,
          OpeningViewer.ALL_PATIENTS,
          GuiUtils.getUICore().getApplicationWindow());
      return true;
    }
    return false;
  }

  @Override
  public MediaElement getPreview() {
    return null;
  }

  @Override
  public PlanarImage getImageFragment(MediaElement media) throws Exception {
    return null;
  }

  @Override
  public MediaElement[] getMediaElement() {
    return new MediaElement[0];
  }

  @Override
  public MediaSeries<MediaElement> getMediaSeries() {
    return null;
  }

  @Override
  public int getMediaElementNumber() {
    return 0;
  }

  @Override
  public String getMediaFragmentMimeType() {
    return MIME_TYPE;
  }

  @Override
  public Map<TagW, Object> getMediaFragmentTags(Object key) {
    return Collections.emptyMap();
  }

  @Override
  public void close() {
    // Nothing to close
  }

  @Override
  public Codec<MediaElement> getCodec() {
    return codec;
  }

  @Override
  public String[] getReaderDescription() {
    return new String[] {"DICOM ZIP reader"}; // NON-NLS
  }

  @Override
  public Object getTagValue(TagW tag) {
    return null;
  }

  @Override
  public void setTag(TagW tag, Object value) {
    // Not supported
  }

  @Override
  public boolean containTagKey(TagW tag) {
    return false;
  }

  @Override
  public void setTagNoNull(TagW tag, Object value) {
    // Not supported
  }

  @Override
  public Iterator<Entry<TagW, Object>> getTagEntrySetIterator() {
    return Collections.emptyIterator();
  }

  @Override
  public void replaceURI(URI uri) {
    // Not supported
  }

  @Override
  public boolean buildFile(File output) {
    return false;
  }

  public static void loadDicomZip(
      File file, DicomModel dicomModel, OpeningViewer openingViewer, Component parent) {
    if (file != null && file.canRead()) {
      Path dir =
          FileUtil.createTempDir(
              AppProperties.buildAccessibleTempDirectory("tmp", "zip")); // NON-NLS
      try (ZipFile zipFile = new ZipFile(file)) {
        if (zipFile.isEncrypted()) {
          JPanel panel = new JPanel();
          JPasswordField pass = new JPasswordField(16);
          panel.add(new JLabel(Messages.getString("enter.password")));
          panel.add(pass);
          ActionListener al = ae -> pass.requestFocusInWindow();
          Timer timer = new Timer(250, al);
          timer.setRepeats(false);
          timer.start();
          int response =
              JOptionPane.showOptionDialog(
                  parent,
                  panel,
                  Messages.getString("DicomZipImport.title"),
                  JOptionPane.OK_CANCEL_OPTION,
                  JOptionPane.PLAIN_MESSAGE,
                  null,
                  null,
                  pass);
          if (response == JOptionPane.OK_OPTION) {
            zipFile.setPassword(pass.getPassword());
          }
        }
        zipFile.extractAll(dir.toString());
      } catch (IOException e) {
        LOGGER.error("unzipping", e);
      }
      File dicomdir = new File(dir.toFile(), "DICOMDIR");
      if (dicomdir.canRead()) {
        DicomDirLoader dirImport = new DicomDirLoader(dicomdir, dicomModel, false);
        List<LoadSeries> loadSeries = dirImport.readDicomDir();
        if (loadSeries != null && !loadSeries.isEmpty()) {
          DicomModel.LOADING_EXECUTOR.execute(
              new LoadDicomDir(loadSeries, dicomModel, openingViewer));
        } else {
          LOGGER.error("Cannot import DICOM from {}", file);
        }
      } else {
        LoadLocalDicom dicom =
            new LoadLocalDicom(new File[] {dir.toFile()}, true, dicomModel, openingViewer);
        DicomModel.LOADING_EXECUTOR.execute(dicom);
      }
    }
  }

  public static void loadDicomZip(String uri, DicomModel dicomModel) {
    if (StringUtil.hasText(uri)) {
      File zipFile = null;
      try {
        URI u = new URI(uri);
        if ("file".equals(u.getScheme())) { // NON-NLS
          zipFile = new File(u.getPath());
        } else {
          zipFile =
              Files.createTempFile(AppProperties.APP_TEMP_DIR, "dicom_", ".zip")
                  .toFile(); // NON-NLS

          ClosableURLConnection urlConnection =
              NetworkUtil.getUrlConnection(u.toURL(), URLParameters.DEFAULT);
          FileUtil.writeStreamWithIOException(urlConnection.getInputStream(), zipFile.toPath());
        }
      } catch (Exception e) {
        LOGGER.error("Loading DICOM Zip", e);
      }
      OpeningViewer openingViewer = OpeningViewer.ALL_PATIENTS;
      loadDicomZip(zipFile, dicomModel, openingViewer, GuiUtils.getUICore().getApplicationWindow());
    }
  }
}
