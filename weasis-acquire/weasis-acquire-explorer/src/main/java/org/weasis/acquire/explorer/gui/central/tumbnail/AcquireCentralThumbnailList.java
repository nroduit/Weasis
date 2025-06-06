/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.central.tumbnail;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.Collections;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.AcquireMediaInfo;
import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.acquire.explorer.gui.central.AcquireTabPanel;
import org.weasis.acquire.explorer.gui.central.SeriesButton;
import org.weasis.acquire.explorer.gui.dialog.AcquireNewSeriesDialog;
import org.weasis.base.explorer.JIThumbnailCache;
import org.weasis.base.explorer.list.AbstractThumbnailList;
import org.weasis.base.explorer.list.IThumbnailModel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.util.StringUtil;

public class AcquireCentralThumbnailList<E extends MediaElement> extends AbstractThumbnailList<E> {

  private AcquireTabPanel acquireTabPanel;

  public AcquireCentralThumbnailList(JIThumbnailCache thumbCache) {
    super(thumbCache);
  }

  @Override
  public IThumbnailModel<E> newModel() {
    return new AcquireCentralThumbnailModel<>(this, thumbCache);
  }

  public void setAcquireTabPanel(AcquireTabPanel acquireTabPanel) {
    this.acquireTabPanel = acquireTabPanel;
  }

  public SeriesButton getSelectedSeries() {
    if (acquireTabPanel != null) {
      return acquireTabPanel.getSelected();
    }
    return null;
  }

  @Override
  public void registerDragListeners() {
    // No drag capabilities
  }

  @Override
  public void openSelection() {
    for (E s : getSelectedValuesList()) {
      openSelection(Collections.singletonList(s), true, true, false);
    }
  }

  @Override
  public JPopupMenu buildContextMenu(final MouseEvent e) {
    final List<E> medias = getSelected(e);

    if (!medias.isEmpty()) {
      E first = medias.getFirst();
      JPopupMenu popupMenu = new JPopupMenu();

      if (first instanceof ImageElement) {
        popupMenu.add(
            new JMenuItem(
                new DefaultAction(
                    Messages.getString("AcquireCentralThumnailList.edit"), _ -> openSelection())));
      }

      popupMenu.add(
          new JMenuItem(
              new DefaultAction(
                  Messages.getString("AcquireCentralThumnailList.remove"),
                  _ -> {
                    clearSelection();
                    AcquireManager.getInstance().removeMedias(medias);
                    repaint();
                  })));

      JMenu moveToMenu = new JMenu(Messages.getString("AcquireCentralThumnailList.moveto"));

      moveToOther(moveToMenu, medias);
      moveToExisting(moveToMenu, medias);
      moveToNewSeries(moveToMenu, medias);
      popupMenu.add(moveToMenu);

      if (first instanceof ImageElement) {
        JMenu operationsMenu =
            new JMenu(Messages.getString("AcquireCentralThumnailList.operations"));
        operationRotate(
            operationsMenu,
            medias,
            Messages.getString("AcquireCentralThumnailList.rotate")
                + StringUtil.COLON_AND_SPACE
                + Messages.getString("AcquireCentralThumnailList.plus90"),
            90);
        operationRotate(
            operationsMenu,
            medias,
            Messages.getString("AcquireCentralThumnailList.rotate")
                + StringUtil.COLON_AND_SPACE
                + Messages.getString("AcquireCentralThumnailList.min90"),
            270);
        popupMenu.add(operationsMenu);
      }

      return popupMenu;
    }

    return null;
  }

  private void moveToExisting(JMenu moveToMenu, final List<E> medias) {
    if (medias == null || medias.isEmpty()) {
      return;
    }
    int menuSize = moveToMenu.getMenuComponentCount();
    var infos = AcquireManager.toAcquireMediaInfo(medias);
    var first = infos.getFirst();
    AcquireCentralThumbnailList.this.acquireTabPanel.getSeries().stream()
        .forEach(
            s -> {
              if (!s.equals(first.getSeries())
                  && !SeriesGroup.Type.IMAGE.equals(s.getType())
                  && s.getType().equals(first.getSeries().getType())) {
                moveToMenu.add(
                    new JMenuItem(
                        new DefaultAction(
                            s.getDisplayName(),
                            _ -> {
                              AcquireCentralThumbnailList.this.acquireTabPanel.moveElements(
                                  s, infos);
                              repaint();
                            })));
              }
            });
    if (moveToMenu.getMenuComponentCount() > menuSize) {
      moveToMenu.addSeparator();
    }
  }

  private void moveToOther(JMenu moveToMenu, final List<E> medias) {
    if (medias == null || medias.isEmpty()) {
      return;
    }
    var infos = AcquireManager.toAcquireMediaInfo(medias);
    if (!infos.isEmpty()) {
      var group = AcquireManager.getDefaultSeries(infos.getFirst());
      if (group != null) {
        moveToMenu.add(
            new JMenuItem(
                new DefaultAction(
                    group.getDisplayName(),
                    _ -> {
                      AcquireCentralThumbnailList.this.acquireTabPanel.moveElements(group, infos);
                      repaint();
                    })));
        moveToMenu.addSeparator();
      }
    }
  }

  private void moveToNewSeries(JMenu moveToMenu, final List<E> medias) {
    if (medias == null || medias.isEmpty()) {
      return;
    }
    moveToMenu.add(
        new JMenuItem(
            new DefaultAction(
                Messages.getString("AcquireCentralThumnailList.new_series"),
                _ -> {
                  JDialog dialog =
                      new AcquireNewSeriesDialog(
                          AcquireCentralThumbnailList.this.acquireTabPanel,
                          AcquireManager.toMediaElement(medias));
                  GuiUtils.showCenterScreen(
                      dialog, AcquireCentralThumbnailList.this.acquireTabPanel);
                  repaint();
                })));
  }

  private void operationRotate(
      JMenu operationsMenu, final List<E> medias, String label, final int angle) {
    operationsMenu.add(
        new JMenuItem(
            new DefaultAction(
                label,
                _ ->
                    medias.stream()
                        .filter(ImageElement.class::isInstance)
                        .map(ImageElement.class::cast)
                        .forEach(
                            i -> {
                              AcquireImageInfo info = AcquireManager.findByImage(i);
                              int lastRotation = info.getNextValues().getFullRotation();
                              int rotation =
                                  (info.getNextValues().getFullRotation() + angle >= 0)
                                      ? info.getNextValues().getRotation() + angle
                                      : info.getNextValues().getRotation() + 360 + angle;
                              info.getNextValues().setRotation(rotation);
                              info.getNextValues().setCropZone(null);
                              GraphicModel modelList =
                                  (GraphicModel)
                                      info.getImage().getTagValue(TagW.PresentationModel);
                              if (modelList != null) {
                                AffineTransform inverse =
                                    info.getAffineTransform(lastRotation, true);
                                AffineTransform transform =
                                    info.getAffineTransform(
                                        info.getNextValues().getFullRotation(), false);
                                for (Graphic g : modelList.getModels()) {
                                  g.getPts()
                                      .forEach(
                                          p -> {
                                            inverse.transform(p, p);
                                            transform.transform(p, p);
                                          });
                                  g.buildShape();
                                }
                              }
                            }))));
  }

  @Override
  public void mouseClickedEvent(MouseEvent e) {
    if (e.getClickCount() == 2) {
      openSelection();
    }
  }

  public void updateAll() {
    acquireTabPanel.clearUnusedSeries(AcquireManager.getBySeries());
    acquireTabPanel.setSelected(acquireTabPanel.getSelected());
    acquireTabPanel.revalidate();
    acquireTabPanel.repaint();
  }

  @Override
  public void jiThumbnailKeyPressed(KeyEvent e) {
    switch (e.getKeyCode()) {
      case KeyEvent.VK_PAGE_DOWN -> nextPage(e);
      case KeyEvent.VK_PAGE_UP -> lastPage(e);
      case KeyEvent.VK_ENTER -> {
        openSelection();
        e.consume();
      }
      case KeyEvent.VK_DELETE -> {
        List<E> selected = getSelectedValuesList();
        if (!selected.isEmpty()) {
          List<AcquireMediaInfo> list = AcquireManager.toAcquireMediaInfo(selected);
          clearSelection();
          AcquireManager.getInstance().removeImages(list);
        }
      }
    }
  }
}
