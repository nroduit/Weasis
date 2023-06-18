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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import org.weasis.base.explorer.JIExplorerContext;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.core.ui.editor.image.DefaultView2d;

public abstract class AThumbnailListPane<E extends MediaElement> extends JScrollPane
    implements IThumbnailListPane<E> {
  protected final ThumbnailList<E> thumbnailList;
  protected final ExecutorService pool;

  protected AThumbnailListPane(ThumbnailList<E> thumbList) {
    super(
        thumbList.asComponent(),
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    this.pool = ThreadUtil.buildNewSingleThreadExecutor("Thumbnail List"); // NON-NLS

    this.thumbnailList = thumbList;
    this.thumbnailList.addListSelectionListener(new JIListSelectionAdapter());
    this.thumbnailList.registerListeners();

    this.setPreferredSize(GuiUtils.getDimension(200, 200));
    this.setAutoscrolls(true);
  }

  @Override
  public void loadDirectory(Path dir) {
    JRootPane pane = getRootPane();
    Optional.ofNullable(pane).ifPresent(p -> p.setCursor(DefaultView2d.WAIT_CURSOR));
    this.pool.execute(
        () -> {
          AThumbnailListPane.this.thumbnailList.getThumbnailListModel().setData(dir);
          AThumbnailListPane.this.thumbnailList.setChanged();
          AThumbnailListPane.this.thumbnailList.clearChanged();
          Optional.ofNullable(pane).ifPresent(p -> p.setCursor(DefaultView2d.DEFAULT_CURSOR));
        });
  }

  final class JIListSelectionAdapter implements javax.swing.event.ListSelectionListener {

    JIListSelectionAdapter() {}

    @Override
    public void valueChanged(final ListSelectionEvent e) {
      final Thread runner =
          new Thread(
              () ->
                  SwingUtilities.invokeLater(
                      () -> AThumbnailListPane.this.thumbnailList.listValueChanged(e)));
      runner.start();
    }
  }

  public void notifyObservers() {
    this.thumbnailList.notifyObservers(null);
  }

  @Override
  public void notifyObservers(final Object arg) {
    this.thumbnailList.notifyObservers(arg);
  }

  @Override
  public boolean hasChanged() {
    return this.thumbnailList.hasChanged();
  }

  @Override
  public IThumbnailModel<E> getFileListModel() {
    return this.thumbnailList.getThumbnailListModel();
  }

  public JIExplorerContext getReloadContext() {
    return this.thumbnailList.getThumbnailListModel().getReloadContext();
  }

  public void setReloadContext(final JIExplorerContext reloadContext) {
    this.thumbnailList.getThumbnailListModel().setReloadContext(reloadContext);
  }

  public int[] getSelectedIndices() {
    return this.thumbnailList.getSelectedIndices();
  }

  public JComponent getRendererComponent() {
    final Object obj = this.thumbnailList.getCellRenderer();
    return (obj instanceof JComponent component) ? component : null;
  }

  @Override
  public List<E> getSelectedValuesList() {
    return thumbnailList.getSelectedValuesList();
  }

  public ThumbnailList<E> getThumbnailList() {
    return thumbnailList;
  }
}
