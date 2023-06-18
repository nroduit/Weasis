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

import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.acquire.explorer.gui.central.AcquireTabPanel;
import org.weasis.core.api.media.data.ImageElement;

public class AcquireNewSeriesDialog extends JDialog implements PropertyChangeListener {
  private final JTextField seriesName = new JTextField();
  private final JOptionPane optionPane;

  private final AcquireTabPanel acquireTabPanel;
  private final List<ImageElement> medias;

  public AcquireNewSeriesDialog(AcquireTabPanel acquireTabPanel, final List<ImageElement> medias) {
    this.acquireTabPanel = acquireTabPanel;
    this.medias = medias;
    optionPane =
        new JOptionPane(
            initPanel(),
            JOptionPane.QUESTION_MESSAGE,
            JOptionPane.OK_CANCEL_OPTION,
            null,
            AcquireImportDialog.OPTIONS,
            AcquireImportDialog.OPTIONS[0]);
    optionPane.addPropertyChangeListener(this);

    setContentPane(optionPane);
    setModal(true);
    pack();
  }

  private JPanel initPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());

    JLabel question = new JLabel(Messages.getString("AcquireNewSerieDialog.enter_name"));
    panel.add(question, BorderLayout.NORTH);

    panel.add(seriesName, BorderLayout.CENTER);

    return panel;
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    Object action = evt.getNewValue();
    boolean close = true;
    if (action != null) {
      if (AcquireImportDialog.OPTIONS[0].equals(action)) {
        if (seriesName.getText() != null && !seriesName.getText().isEmpty()) {
          acquireTabPanel.moveElements(
              new SeriesGroup(seriesName.getText()), AcquireManager.toAcquireImageInfo(medias));
        } else {
          JOptionPane.showMessageDialog(
              this,
              Messages.getString("AcquireImportDialog.add_name_msg"),
              Messages.getString("AcquireImportDialog.add_name_title"),
              JOptionPane.ERROR_MESSAGE);
          optionPane.setValue(AcquireImportDialog.REVALIDATE);
          close = false;
        }
      } else if (action.equals(AcquireImportDialog.REVALIDATE)) {
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
