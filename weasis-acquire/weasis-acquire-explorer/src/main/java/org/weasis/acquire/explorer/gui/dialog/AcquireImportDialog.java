/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.dialog;

import java.awt.Component;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.Preferences;
import org.weasis.acquire.explorer.AcquireExplorer;
import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.acquire.explorer.gui.control.ImportPanel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.util.StringUtil;

public class AcquireImportDialog extends JDialog implements PropertyChangeListener {

  private static final String P_MAX_RANGE = "maxMinuteRange";

  private final ImportPanel importPanel;

  static final Object[] OPTIONS = {
    Messages.getString("AcquireImportDialog.validate"),
    Messages.getString("AcquireImportDialog.cancel")
  };
  static final String REVALIDATE = "ReValidate";

  private final JTextField seriesName = new JTextField(20);
  private final ButtonGroup btnGrp = new ButtonGroup();

  private final JRadioButton btn1 =
      new JRadioButton(Messages.getString("AcquireImportDialog.no_grp"));
  private final JRadioButton btn2 =
      new JRadioButton(Messages.getString("AcquireImportDialog.date_grp"));
  private final JRadioButton btn3 =
      new JRadioButton(Messages.getString("AcquireImportDialog.name_grp"));
  private final JSpinner spinner;

  private final JOptionPane optionPane;

  private final List<ImageElement> mediaList;

  public AcquireImportDialog(ImportPanel importPanel, List<ImageElement> mediaList) {
    super();
    this.importPanel = importPanel;
    this.mediaList = mediaList;

    int maxRange = 60;
    Preferences prefs =
        BundlePreferences.getDefaultPreferences(
            FrameworkUtil.getBundle(this.getClass()).getBundleContext());
    if (prefs != null) {
      Preferences p = prefs.node(AcquireExplorer.PREFERENCE_NODE);
      maxRange = p.getInt(P_MAX_RANGE, maxRange);
    }
    spinner = new JSpinner(new SpinnerNumberModel(maxRange, 1, 5256000, 5)); // <=> 4 years

    optionPane =
        new JOptionPane(
            initPanel(),
            JOptionPane.QUESTION_MESSAGE,
            JOptionPane.OK_CANCEL_OPTION,
            null,
            OPTIONS,
            OPTIONS[0]);
    optionPane.addPropertyChangeListener(this);
    setContentPane(optionPane);
    setModal(true);
    setLocationRelativeTo(null);
    pack();
  }

  private JPanel initPanel() {
    JPanel panel = GuiUtils.getVerticalBoxLayoutPanel();
    panel.setBorder(GuiUtils.getEmptyBorder(10, 5, 20, 15));

    JLabel question =
        new JLabel(Messages.getString("AcquireImportDialog.grp_msg") + StringUtil.COLON);
    JLabel maxRange = new JLabel(Messages.getString("AcquireImportDialog.max_range_min"));
    panel.add(GuiUtils.getFlowLayoutPanel(question));
    panel.add(GuiUtils.boxVerticalStrut(15));
    panel.add(GuiUtils.getFlowLayoutPanel(btn1));
    panel.add(GuiUtils.getFlowLayoutPanel(btn2, spinner, maxRange));
    panel.add(GuiUtils.getFlowLayoutPanel(btn3, seriesName));

    installFocusListener(spinner);

    GuiUtils.setPreferredWidth(seriesName, 150);
    seriesName.addFocusListener(
        new FocusListener() {

          @Override
          public void focusLost(FocusEvent e) {
            // Do nothing
          }

          @Override
          public void focusGained(FocusEvent e) {
            btnGrp.setSelected(btn3.getModel(), true);
          }
        });

    btnGrp.add(btn1);
    btnGrp.add(btn2);
    btnGrp.add(btn3);
    btnGrp.setSelected(btn1.getModel(), true);

    return panel;
  }

  public void installFocusListener(JSpinner spinner) {
    JComponent spinnerEditor = spinner.getEditor();
    if (spinnerEditor != null) {
      Component c = spinnerEditor.getComponent(0);
      if (c != null) {
        c.addFocusListener(
            new FocusListener() {

              @Override
              public void focusLost(FocusEvent e) {
                // Do nothing
              }

              @Override
              public void focusGained(FocusEvent e) {
                btnGrp.setSelected(btn2.getModel(), true);
              }
            });
      }
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    Object action = evt.getNewValue();
    if (action != null) {
      boolean close = true;
      if (action.equals(OPTIONS[0])) {
        SeriesGroup seriesType;
        if (btnGrp.getSelection().equals(btn1.getModel())) {
          seriesType = null;
        } else if (btnGrp.getSelection().equals(btn2.getModel())) {
          seriesType = SeriesGroup.DATE_SERIES;
        } else {
          if (seriesName.getText() != null && !seriesName.getText().isEmpty()) {
            seriesType = new SeriesGroup(seriesName.getText());
            seriesType.setNeedUpdateFromGlobalTags(true);
          } else {
            seriesType = null;
            JOptionPane.showMessageDialog(
                this,
                Messages.getString("AcquireImportDialog.add_name_msg"),
                Messages.getString("AcquireImportDialog.add_name_title"),
                JOptionPane.ERROR_MESSAGE);
            optionPane.setValue(REVALIDATE);
            close = false;
          }
        }

        if (close) {
          importPanel.getCentralPane().setSelectedAndGetFocus();

          Integer maxRangeInMinutes = (Integer) spinner.getValue();
          Preferences prefs =
              BundlePreferences.getDefaultPreferences(
                  FrameworkUtil.getBundle(this.getClass()).getBundleContext());
          if (prefs != null) {
            Preferences p = prefs.node(AcquireExplorer.PREFERENCE_NODE);
            BundlePreferences.putIntPreferences(p, P_MAX_RANGE, maxRangeInMinutes);
          }

          importPanel.importImageList(mediaList, seriesType, maxRangeInMinutes);
        }
      } else if (action.equals(REVALIDATE)) {
        close = false;
      }

      if (close) {
        clearAndHide();
      }
    }
  }

  public void clearAndHide() {
    seriesName.setText(null);
    setVisible(false);
  }
}
