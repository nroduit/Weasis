/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.pref;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.seg.SegVisibilityPolicy;
import org.weasis.dicom.viewer2d.Messages;

/**
 * Which segmentations start hidden. The rules live in {@link SegVisibilityPolicy} and apply to
 * every view that draws a SEG — 2D, MPR and 3D — so they get a page of their own rather than
 * sitting under the 2D viewer settings.
 */
public class SegPrefView extends AbstractItemDialogPage {

  private final JCheckBox checkBoxHideAll = new JCheckBox();

  /**
   * Sized in columns, not by its content: the keyword list is long and a field left to its own
   * preferred width would push the page wider than the dialog and raise a horizontal scrollbar.
   */
  private final JTextField hideKeywords = new JTextField(20);

  /** {@code 0} disables the rule, hence the minimum. */
  private final JSpinner hideCount =
      new JSpinner(new SpinnerNumberModel(SegVisibilityPolicy.DEFAULT_HIDE_COUNT, 0, 999, 1));

  public SegPrefView() {
    super(Messages.getString("segmentation"), 508);
    initGUI();
  }

  private void initGUI() {
    WProperties prefs = GuiUtils.getUICore().getSystemPreferences();

    checkBoxHideAll.setText(Messages.getString("ViewerPrefView.seg_hide_all"));
    checkBoxHideAll.setToolTipText(Messages.getString("ViewerPrefView.seg_hide_all_tooltip"));
    checkBoxHideAll.setSelected(prefs.getBooleanProperty(SegVisibilityPolicy.HIDE_ALL, false));

    JLabel lblKeywords =
        new JLabel(Messages.getString("ViewerPrefView.seg_hide") + StringUtil.COLON);
    hideKeywords.setToolTipText(Messages.getString("ViewerPrefView.seg_hide_tooltip"));
    hideKeywords.setText(
        prefs.getProperty(
            SegVisibilityPolicy.HIDE_KEYWORDS, SegVisibilityPolicy.DEFAULT_HIDE_KEYWORDS));
    // A long list would otherwise open scrolled to its end, showing the caret and no keyword.
    hideKeywords.setCaretPosition(0);

    JLabel lblCount =
        new JLabel(Messages.getString("ViewerPrefView.seg_hide_count") + StringUtil.COLON);
    hideCount.setToolTipText(Messages.getString("ViewerPrefView.seg_hide_count_tooltip"));
    GuiUtils.setSpinnerWidth(hideCount, 3);
    hideCount.setValue(
        prefs.getIntProperty(
            SegVisibilityPolicy.HIDE_COUNT, SegVisibilityPolicy.DEFAULT_HIDE_COUNT));

    // One grid for the whole group: the checkbox spans both columns, the two rules line their
    // labels up in the first and let the editor take whatever width is left.
    JPanel panel =
        new JPanel(
            new MigLayout(
                "fillx, insets 5lp 10lp 10lp 10lp", // NON-NLS
                "[right]rel[grow,fill]")); // NON-NLS
    panel.setBorder(GuiUtils.getTitledBorder(Messages.getString("ViewerPrefView.seg_hide_title")));
    panel.add(checkBoxHideAll, "spanx 2, alignx leading, gapbottom rel"); // NON-NLS
    panel.add(lblKeywords, GuiUtils.NEWLINE);
    panel.add(hideKeywords, "growx"); // NON-NLS
    panel.add(lblCount, GuiUtils.NEWLINE);
    panel.add(hideCount, "growx 0, alignx leading"); // NON-NLS

    // "Hide all" subsumes the two finer rules, so grey them out rather than letting the user tune
    // settings that cannot change anything.
    setRulesEnabled(!checkBoxHideAll.isSelected(), lblKeywords, lblCount);
    checkBoxHideAll.addItemListener(
        e -> setRulesEnabled(e.getStateChange() != ItemEvent.SELECTED, lblKeywords, lblCount));

    // The page stacks its groups vertically: without a bounded height the group would absorb all
    // the free space, and without an unbounded width it would be centred instead of stretched.
    panel.setMaximumSize(new Dimension(Short.MAX_VALUE, panel.getPreferredSize().height));
    panel.setAlignmentX(Component.LEFT_ALIGNMENT);
    add(panel);

    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
    getProperties().setProperty(PreferenceDialog.KEY_HELP, "dicom-segmentation"); // NON-NLS
  }

  private void setRulesEnabled(boolean enabled, JLabel lblKeywords, JLabel lblCount) {
    lblKeywords.setEnabled(enabled);
    hideKeywords.setEnabled(enabled);
    lblCount.setEnabled(enabled);
    hideCount.setEnabled(enabled);
  }

  @Override
  public void closeAdditionalWindow() {
    WProperties prefs = GuiUtils.getUICore().getSystemPreferences();
    prefs.setProperty(SegVisibilityPolicy.HIDE_KEYWORDS, hideKeywords.getText().trim());
    prefs.putBooleanProperty(SegVisibilityPolicy.HIDE_ALL, checkBoxHideAll.isSelected());
    prefs.putIntProperty(SegVisibilityPolicy.HIDE_COUNT, (Integer) hideCount.getValue());
    // Segmentations already loaded still follow the default unless the user checked them by hand,
    // so the new settings take effect on the next repaint rather than on the next study.
    SegVisibilityPolicy.invalidate();
    GuiUtils.getUICore().saveSystemPreferences();
  }

  @Override
  public void resetToDefaultValues() {
    WProperties prefs = GuiUtils.getUICore().getSystemPreferences();
    prefs.resetProperty(
        SegVisibilityPolicy.HIDE_KEYWORDS, SegVisibilityPolicy.DEFAULT_HIDE_KEYWORDS);
    hideKeywords.setText(
        prefs.getProperty(
            SegVisibilityPolicy.HIDE_KEYWORDS, SegVisibilityPolicy.DEFAULT_HIDE_KEYWORDS));
    hideKeywords.setCaretPosition(0);
    prefs.resetProperty(SegVisibilityPolicy.HIDE_ALL, Boolean.FALSE.toString());
    checkBoxHideAll.setSelected(prefs.getBooleanProperty(SegVisibilityPolicy.HIDE_ALL, false));
    prefs.resetProperty(
        SegVisibilityPolicy.HIDE_COUNT, String.valueOf(SegVisibilityPolicy.DEFAULT_HIDE_COUNT));
    hideCount.setValue(
        prefs.getIntProperty(
            SegVisibilityPolicy.HIDE_COUNT, SegVisibilityPolicy.DEFAULT_HIDE_COUNT));
  }
}
