/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.explorer.list.impl;

import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.base.explorer.JIThumbnailCache;
import org.weasis.base.explorer.Messages;
import org.weasis.base.explorer.list.AbstractThumbnailList;
import org.weasis.base.explorer.list.IThumbnailModel;
import org.weasis.base.explorer.list.ThumbnailList;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.ui.util.TitleMenuItem;

public final class DefaultThumbnailList<E extends MediaElement> extends AbstractThumbnailList<E>
    implements ThumbnailList<E> {
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultThumbnailList.class);

  public DefaultThumbnailList(JIThumbnailCache thumbCache) {
    super(thumbCache);
  }

  @Override
  public IThumbnailModel<E> newModel() {
    return new JIListModel<>(this, thumbCache);
  }

  @Override
  public JPopupMenu buildContextMenu(final MouseEvent e) {

    try {
      final List<E> selMedias = getSelected(e);
      if (selMedias.isEmpty()) {
        return null;
      } else {
        JPopupMenu popupMenu = new JPopupMenu();
        TitleMenuItem itemTitle = new TitleMenuItem(Messages.getString("JIThumbnailList.sel_menu"));
        popupMenu.add(itemTitle);
        popupMenu.addSeparator();

        if (selMedias.size() == 1) {
          JMenuItem menuItem =
              new JMenuItem(
                  new DefaultAction(
                      Messages.getString("JIThumbnailList.open"),
                      event -> openSelection(selMedias, true, true, false)));
          popupMenu.add(menuItem);

          menuItem =
              new JMenuItem(
                  new DefaultAction(
                      Messages.getString("JIThumbnailList.open_win"),
                      event -> openSelection(selMedias, false, true, false)));
          popupMenu.add(menuItem);

          menuItem =
              new JMenuItem(
                  new DefaultAction(
                      Messages.getString("JIThumbnailList.open_sel_win"),
                      event -> openSelection(selMedias, true, false, true)));
          popupMenu.add(menuItem);
        } else {
          JMenu menu = new JMenu(Messages.getString("JIThumbnailList.open_win"));
          JMenuItem menuItem =
              new JMenuItem(
                  new DefaultAction(
                      Messages.getString("JIThumbnailList.stack_mode"),
                      event -> openGroup(selMedias, false, true, false, false)));
          menu.add(menuItem);
          menuItem =
              new JMenuItem(
                  new DefaultAction(
                      Messages.getString("JIThumbnailList.layout_mode"),
                      event -> openGroup(selMedias, false, true, true, false)));
          menu.add(menuItem);
          popupMenu.add(menu);

          menu = new JMenu(Messages.getString("JIThumbnailList.open_sel_win"));
          menuItem =
              new JMenuItem(
                  new DefaultAction(
                      Messages.getString("JIThumbnailList.stack_mode"),
                      event -> openGroup(selMedias, true, false, false, true)));
          menu.add(menuItem);
          menuItem =
              new JMenuItem(
                  new DefaultAction(
                      Messages.getString("JIThumbnailList.layout_mode"),
                      event -> openGroup(selMedias, true, false, true, true)));
          menu.add(menuItem);
          popupMenu.add(menu);
        }
        return popupMenu;
      }
    } catch (Exception ex) {
      LOGGER.error("Build context menu", ex);
    } finally {
      e.consume();
    }
    return null;
  }

  @Override
  public void mouseClickedEvent(MouseEvent e) {
    if (e.getClickCount() == 2) {
      openSelection();
    }
  }
}
