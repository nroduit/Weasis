/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.pref;

import java.awt.FlowLayout;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.setting.Setting;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.wsi.Messages;
import org.weasis.dicom.wsi.WsiFactory;
import org.weasis.dicom.wsi.WsiSettings;
import org.weasis.dicom.wsi.tile.TileCache;

/** The zoom, the pan and the tile loading of the slide viewer. */
public class WsiPrefView extends AbstractItemDialogPage {

  /** The title and the position of the page in the preference tree, known before it is built. */
  public static final String PAGE_TITLE = WsiFactory.NAME;

  public static final int PAGE_POSITION = 509;

  private static final long MEGABYTE = 1L << 20;

  private final JCheckBox animation = new JCheckBox(Messages.getString("WsiPrefView.animation"));
  private final JSpinner step = new JSpinner(new SpinnerNumberModel(15, 1, 400, 1));
  private final JCheckBox cursor = new JCheckBox(Messages.getString("WsiPrefView.cursor"));
  private final JCheckBox snap = new JCheckBox(Messages.getString("WsiPrefView.snap"));
  private final JTextField presets = new JTextField(22);
  private final JCheckBox inertia = new JCheckBox(Messages.getString("WsiPrefView.inertia"));
  private final JSpinner memory = new JSpinner(new SpinnerNumberModel(0, 0, 65536, 256));
  private final JSpinner threads = new JSpinner(new SpinnerNumberModel(0, 0, 64, 1));
  private final JSpinner annotations =
      new JSpinner(new SpinnerNumberModel(20000, 100, 1_000_000, 1000));

  public WsiPrefView() {
    super(PAGE_TITLE, PAGE_POSITION);
    GuiUtils.setSpinnerWidth(step, 4);
    GuiUtils.setSpinnerWidth(memory, 6);
    GuiUtils.setSpinnerWidth(threads, 3);
    GuiUtils.setSpinnerWidth(annotations, 8);
    initGUI();
    load();
  }

  private void initGUI() {
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));
    cursor.setToolTipText(Messages.getString("WsiPrefView.cursor_tip"));
    snap.setToolTipText(Messages.getString("WsiPrefView.snap_tip"));
    presets.setToolTipText(Messages.getString("WsiPrefView.presets_tip"));
    memory.setToolTipText(Messages.getString("WsiPrefView.memory_tip"));
    threads.setToolTipText(Messages.getString("WsiPrefView.threads_tip"));

    JPanel zoom = GuiUtils.getVerticalBoxLayoutPanel();
    zoom.setBorder(GuiUtils.getTitledBorder(Messages.common("zoom")));
    zoom.add(GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, ITEM_SEPARATOR, animation));
    zoom.add(
        row(
            new JLabel(Messages.getString("WsiPrefView.step") + StringUtil.COLON),
            step,
            new JLabel("%"))); // NON-NLS
    zoom.add(GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, ITEM_SEPARATOR, cursor));
    zoom.add(GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, ITEM_SEPARATOR, snap));
    zoom.add(
        row(new JLabel(Messages.getString("WsiPrefView.presets") + StringUtil.COLON), presets));
    add(zoom);
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    JPanel pan = GuiUtils.getVerticalBoxLayoutPanel();
    pan.setBorder(GuiUtils.getTitledBorder(Messages.common("pan")));
    pan.add(GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, ITEM_SEPARATOR, inertia));
    add(pan);
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    JPanel tiles = GuiUtils.getVerticalBoxLayoutPanel();
    tiles.setBorder(GuiUtils.getTitledBorder(Messages.getString("WsiPrefView.tiles")));
    tiles.add(
        row(
            new JLabel(Messages.getString("WsiPrefView.memory") + StringUtil.COLON),
            memory,
            new JLabel(Messages.getString("WsiPrefView.memory_unit"))));
    tiles.add(
        row(
            new JLabel(Messages.getString("WsiPrefView.threads") + StringUtil.COLON),
            threads,
            new JLabel(Messages.getString("WsiPrefView.threads_unit"))));
    add(tiles);
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    JPanel overlay = GuiUtils.getVerticalBoxLayoutPanel();
    overlay.setBorder(GuiUtils.getTitledBorder(Messages.getString("WsiPrefView.annotations")));
    annotations.setToolTipText(Messages.getString("WsiPrefView.annotations_tip"));
    overlay.add(
        row(
            new JLabel(Messages.getString("WsiPrefView.annotations_max") + StringUtil.COLON),
            annotations));
    add(overlay);

    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
  }

  private static JPanel row(JComponent... components) {
    JPanel panel =
        GuiUtils.getFlowLayoutPanel(FlowLayout.LEADING, ITEM_SEPARATOR_SMALL, ITEM_SEPARATOR);
    panel.add(GuiUtils.boxHorizontalStrut(ITEM_SEPARATOR - ITEM_SEPARATOR_SMALL));
    for (JComponent c : components) {
      panel.add(c);
    }
    return panel;
  }

  // The values in force; a locked setting is shown but cannot be changed
  private void load() {
    animation.setSelected(Settings.get(WsiSettings.ZOOM_ANIMATION));
    step.setValue(Math.clamp(Settings.get(WsiSettings.ZOOM_STEP), 1, 400));
    cursor.setSelected(Settings.get(WsiSettings.ZOOM_AT_CURSOR));
    snap.setSelected(Settings.get(WsiSettings.ZOOM_SNAP));
    presets.setText(Settings.get(WsiSettings.ZOOM_PRESETS));
    inertia.setSelected(Settings.get(WsiSettings.PAN_INERTIA));
    memory.setValue((int) Math.min(65536, Settings.get(WsiSettings.TILE_MEMORY) / MEGABYTE));
    threads.setValue(Math.clamp(Settings.get(WsiSettings.TILE_THREADS), 0, 64));
    annotations.setValue(Math.clamp(Settings.get(WsiSettings.ANNOTATIONS_MAX), 100, 1_000_000));
    for (var entry :
        List.of(
            new Object[] {WsiSettings.ZOOM_ANIMATION, animation},
            new Object[] {WsiSettings.ZOOM_STEP, step},
            new Object[] {WsiSettings.ZOOM_AT_CURSOR, cursor},
            new Object[] {WsiSettings.ZOOM_SNAP, snap},
            new Object[] {WsiSettings.ZOOM_PRESETS, presets},
            new Object[] {WsiSettings.PAN_INERTIA, inertia},
            new Object[] {WsiSettings.TILE_MEMORY, memory},
            new Object[] {WsiSettings.TILE_THREADS, threads},
            new Object[] {WsiSettings.ANNOTATIONS_MAX, annotations})) {
      ((JComponent) entry[1]).setEnabled(!Settings.isLocked((Setting<?>) entry[0]));
    }
  }

  @Override
  public void closeAdditionalWindow() {
    Settings.set(WsiSettings.ZOOM_ANIMATION, animation.isSelected());
    Settings.set(WsiSettings.ZOOM_STEP, (Integer) step.getValue());
    Settings.set(WsiSettings.ZOOM_AT_CURSOR, cursor.isSelected());
    Settings.set(WsiSettings.ZOOM_SNAP, snap.isSelected());
    // A list that cannot be read keeps the presets in force
    WsiSettings.parsePresets(presets.getText())
        .ifPresent(_ -> Settings.set(WsiSettings.ZOOM_PRESETS, presets.getText().trim()));
    Settings.set(WsiSettings.PAN_INERTIA, inertia.isSelected());
    Settings.set(WsiSettings.TILE_MEMORY, (Integer) memory.getValue() * MEGABYTE);
    Settings.set(WsiSettings.TILE_THREADS, (Integer) threads.getValue());
    Settings.set(WsiSettings.ANNOTATIONS_MAX, (Integer) annotations.getValue());
    TileCache.getInstance().applyBudget();
    GuiUtils.getUICore().saveSystemPreferences();
  }

  @Override
  public void resetToDefaultValues() {
    Settings.reset(WsiSettings.ZOOM_ANIMATION);
    Settings.reset(WsiSettings.ZOOM_STEP);
    Settings.reset(WsiSettings.ZOOM_AT_CURSOR);
    Settings.reset(WsiSettings.ZOOM_SNAP);
    Settings.reset(WsiSettings.ZOOM_PRESETS);
    Settings.reset(WsiSettings.PAN_INERTIA);
    Settings.reset(WsiSettings.TILE_MEMORY);
    Settings.reset(WsiSettings.TILE_THREADS);
    Settings.reset(WsiSettings.ANNOTATIONS_MAX);
    load();
  }
}
