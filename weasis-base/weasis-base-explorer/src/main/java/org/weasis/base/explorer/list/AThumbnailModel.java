/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.explorer.list;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.StreamSupport;
import javax.swing.AbstractListModel;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.base.explorer.JIExplorerContext;
import org.weasis.base.explorer.JIThumbnailCache;
import org.weasis.base.explorer.TreeNode;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.ui.editor.ViewerPluginBuilder;

public abstract class AThumbnailModel<E extends MediaElement> extends AbstractListModel<E>
    implements IThumbnailModel<E> {
  private static final Logger LOGGER = LoggerFactory.getLogger(AThumbnailModel.class);

  protected JIExplorerContext reloadContext;
  protected boolean loading = false;

  protected final JList<E> list;
  protected final DefaultListModel<E> listModel;
  protected final JIThumbnailCache thumbCache;

  protected AThumbnailModel(JList<E> list, JIThumbnailCache thumbCache) {
    this.list = list;
    this.thumbCache = thumbCache;
    // Fix list reselection interval when dragging
    this.list.putClientProperty("List.isFileList", Boolean.TRUE);
    listModel = new DefaultListModel<>();
    list.setModel(listModel);
  }

  public synchronized boolean loading() {
    return this.loading;
  }

  @Override
  public synchronized void notifyAsUpdated(final int index) {
    fireContentsChanged(this, index, index);
  }

  @Override
  public void reload() {
    Optional<TreeNode> t = this.reloadContext.getSelectedDirNodes().stream().findFirst();
    t.ifPresent(treeNode -> setData(treeNode.getNodePath()));
  }

  @Override
  public final synchronized JIExplorerContext getReloadContext() {
    return this.reloadContext;
  }

  @Override
  public final synchronized void setReloadContext(final JIExplorerContext reloadContext) {
    this.reloadContext = reloadContext;
  }

  public final synchronized boolean isLoading() {
    return this.loading;
  }

  @Override
  public int getSize() {
    return listModel.getSize();
  }

  @Override
  public E getElementAt(final int index) {
    if (listModel.isEmpty()) {
      return null;
    }
    return listModel.getElementAt(index);
  }

  @Override
  public void clear() {
    if (thumbCache != null) {
      for (int i = 0; i < listModel.size(); i++) {
        if (listModel.getElementAt(i) instanceof ImageElement imageElement) {
          thumbCache.removeInQueue(imageElement);
        }
      }
    }
    list.getSelectionModel().clearSelection();
    listModel.clear();
  }

  @Override
  public boolean isEmpty() {
    return listModel.isEmpty();
  }

  @Override
  public void addElement(E element) {
    listModel.addElement(element);
  }

  @Override
  public boolean contains(E elem) {
    return listModel.contains(elem);
  }

  @Override
  public boolean removeElement(E obj) {
    if (thumbCache != null && obj instanceof ImageElement imageElement) {
      thumbCache.removeInQueue(imageElement);
    }
    return listModel.removeElement(obj);
  }

  @Override
  public void loadContent(Path path, Filter<Path> filter) {
    if (path == null) {
      return;
    }
    clear();

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(path, filter)) {
      StreamSupport.stream(stream.spliterator(), false)
          .sorted(Comparator.comparing(Path::getFileName))
          .forEachOrdered(
              p -> {
                MediaReader media = ViewerPluginBuilder.getMedia(p.toFile());
                if (media != null) {
                  MediaElement preview = media.getPreview();
                  if (preview instanceof ImageElement) {
                    preview.getFileCache().setRequireTransformation(true);
                    addElement((E) preview);
                  }

                  if (getSize() == 1) {
                    this.list.setSelectedIndex(0);
                  }
                }
              });
    } catch (IOException e) {
      LOGGER.error("Building child directories", e);
    }
  }

  @Override
  public void loadContent(Path path) {
    DirectoryStream.Filter<Path> filter = p -> !Files.isDirectory(p);
    loadContent(path, filter);
  }
}
