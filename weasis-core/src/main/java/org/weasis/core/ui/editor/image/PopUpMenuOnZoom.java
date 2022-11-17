/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Objects;
import java.util.Optional;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.ZoomWin.SyncType;

/**
 * The Class PopUpMenuOnZoom.
 *
 * @author Nicolas Roduit
 */
public class PopUpMenuOnZoom extends JPopupMenu {

  private static final int[] magnify = {1, 2, 3, 4, 6};

  /** The display image zone */
  private final ZoomWin zoomWin;

  private final JMenuItem jMenuItemZoom = new JMenuItem();
  private final ButtonGroup buttonMagnify = new ButtonGroup();
  private JRadioButtonMenuItem[] jRadioButtonMenuItemMagnify;
  private final JMenu jMenuMagnify = new JMenu();
  private final JMenu jMenuImage = new JMenu();
  private final JRadioButtonMenuItem jMenuItemMagnifyOther = new JRadioButtonMenuItem();
  private final JCheckBoxMenuItem jCheckBoxMenuItemDraw = new JCheckBoxMenuItem();
  private final JCheckBoxMenuItem jCheckBoxMenuItemSynchronize = new JCheckBoxMenuItem();
  private final JMenuItem resetFreeze = new JMenuItem(Messages.getString("PopUpMenuOnZoom.reset"));
  private final ActionListener magnifyListener;

  public PopUpMenuOnZoom(ZoomWin zoomWin) {
    this.zoomWin = Objects.requireNonNull(zoomWin);
    magnifyListener = this::magnifyActionPerformed;
    init();
  }

  private void init() {
    jMenuItemZoom.setText(Messages.getString("PopUpMenuOnZoom.hide"));
    jMenuItemZoom.addActionListener(e -> zoomWin.hideZoom());
    jCheckBoxMenuItemDraw.setText(Messages.getString("PopUpMenuOnZoom.showDraw"));
    jCheckBoxMenuItemDraw.addActionListener(
        e -> {
          zoomWin.setActionInView(ActionW.DRAWINGS.cmd(), jCheckBoxMenuItemDraw.isSelected());
          zoomWin.repaint();
        });
    jMenuImage.setText(Messages.getString("PopUpMenuOnZoom.image"));
    final JMenuItem freesParams = new JMenuItem(Messages.getString("PopUpMenuOnZoom.freeze"));
    freesParams.addActionListener(e -> zoomWin.setFreezeImage(SyncType.PARENT_PARAMETERS));
    jMenuImage.add(freesParams);
    final JMenuItem freeze = new JMenuItem(Messages.getString("PopUpMenuOnZoom.freezeImg"));
    freeze.addActionListener(e -> zoomWin.setFreezeImage(SyncType.PARENT_IMAGE));
    jMenuImage.add(freeze);
    jMenuImage.addSeparator();
    resetFreeze.addActionListener(e -> zoomWin.setFreezeImage(null));
    jMenuImage.add(resetFreeze);

    jMenuMagnify.setText(Messages.getString("PopUpMenuOnZoom.magnify"));
    jCheckBoxMenuItemSynchronize.setText(Messages.getString("PopUpMenuOnZoom.synch"));
    jCheckBoxMenuItemSynchronize.addActionListener(
        e -> {
          zoomWin.setActionInView(ZoomWin.SYNCH_CMD, jCheckBoxMenuItemSynchronize.isSelected());
          zoomWin.updateZoom();
        });
    this.add(jMenuItemZoom);
    this.addSeparator();
    this.add(jCheckBoxMenuItemDraw);
    this.add(jCheckBoxMenuItemSynchronize);
    this.add(jMenuMagnify);
    iniMenuItemZoomMagnify();
    this.add(jMenuImage);
  }

  public void iniMenuItemZoomMagnify() {
    jRadioButtonMenuItemMagnify = new JRadioButtonMenuItem[magnify.length];
    for (int i = 0; i < jRadioButtonMenuItemMagnify.length; i++) {
      JRadioButtonMenuItem item = new JRadioButtonMenuItem();
      item.setText(magnify[i] + "X"); // NON-NLS
      buttonMagnify.add(item);
      item.addActionListener(magnifyListener);
      jMenuMagnify.add(item);
      jRadioButtonMenuItemMagnify[i] = item;
    }
  }

  public void enableMenuItem() {
    // Do not trigger actionListener
    jCheckBoxMenuItemSynchronize.setSelected((Boolean) zoomWin.getActionValue(ZoomWin.SYNCH_CMD));
    jCheckBoxMenuItemDraw.setSelected((Boolean) zoomWin.getActionValue(ActionW.DRAWINGS.cmd()));
    Object type = zoomWin.getActionValue(ZoomWin.FREEZE_CMD);
    resetFreeze.setEnabled(
        SyncType.PARENT_PARAMETERS.equals(type) || SyncType.PARENT_IMAGE.equals(type));

    // Get current zoom magnitude
    boolean noSelection = true;
    if (jRadioButtonMenuItemMagnify.length < jMenuMagnify.getItemCount()) {
      jMenuMagnify.remove(jMenuItemMagnifyOther);
    }
    Double ratio = (Double) zoomWin.getActionValue(ActionW.ZOOM.cmd());
    if (ratio == null) {
      ratio = 1.0;
    }
    int currentZoomRatio = (int) (ratio * 100.0);
    for (int i = 0; i < jRadioButtonMenuItemMagnify.length; i++) {
      if ((magnify[i] * 100) == currentZoomRatio) {
        JRadioButtonMenuItem item3 = jRadioButtonMenuItemMagnify[i];
        item3.setSelected(true);
        noSelection = false;
        break;
      }
    }
    if (noSelection) {
      ratio = Math.abs(ratio);
      jMenuItemMagnifyOther.setText(ratio + "X"); // NON-NLS
      buttonMagnify.add(jMenuItemMagnifyOther);
      if ((magnify[magnify.length - 1]) < ratio) {
        jMenuMagnify.add(jMenuItemMagnifyOther);
      } else {
        int k = 0;
        for (int i = 0; i < magnify.length; i++) {
          if ((magnify[i]) > ratio) {
            k = i;
            break;
          }
        }
        jMenuMagnify.add(jMenuItemMagnifyOther, k);
      }
      jMenuItemMagnifyOther.setSelected(true);
    }
  }

  private void magnifyActionPerformed(ActionEvent e) {
    if (e.getSource() instanceof JRadioButtonMenuItem item) {
      for (int i = 0; i < jRadioButtonMenuItemMagnify.length; i++) {
        if (item.equals(jRadioButtonMenuItemMagnify[i])) {
          ImageViewerEventManager<?> manager = zoomWin.getView2d().getEventManager();
          Optional<SliderChangeListener> zoomAction = manager.getAction(ActionW.LENS_ZOOM);
          if (zoomAction.isPresent()) {
            zoomAction.get().setRealValue(magnify[i]);
          }
          break;
        }
      }
    }
  }
}
