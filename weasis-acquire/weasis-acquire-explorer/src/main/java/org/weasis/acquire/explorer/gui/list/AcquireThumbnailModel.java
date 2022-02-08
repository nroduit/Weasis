/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.list;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JList;
import org.weasis.base.explorer.JIThumbnailCache;
import org.weasis.base.explorer.list.AThumbnailModel;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.dicom.codec.DicomMediaIO;

public class AcquireThumbnailModel<E extends MediaElement> extends AThumbnailModel<E> {

  public AcquireThumbnailModel(JList<E> list, JIThumbnailCache thumbCache) {
    super(list, thumbCache);
  }

  @Override
  public void setData(Path dir) {
    if (this.loading) {
      return;
    }

    if (dir != null) {
      synchronized (this) {
        this.loading = true;
      }
      this.list.getSelectionModel().setValueIsAdjusting(true);
      this.list.requestFocusInWindow();
      loadContent(dir);
      synchronized (this) {
        fireContentsChanged(this, 0, getSize() - 1);
        this.loading = false;
      }
      this.list.getSelectionModel().setValueIsAdjusting(false);
    }
  }

  @Override
  public void loadContent(Path path) {
    DirectoryStream.Filter<Path> filter =
        p ->
            !Files.isDirectory(p)
                && !MimeInspector.isMatchingMimeTypeFromMagicNumber(
                    p.toFile(), DicomMediaIO.DICOM_MIMETYPE);
    loadContent(path, filter);
  }
}
