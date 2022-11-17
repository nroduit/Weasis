/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import java.awt.FlowLayout;
import java.awt.Window;
import java.util.List;
import java.util.Objects;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.util.StringUtil;

/**
 * @author Marcelo Porto (marcelo@animati.com.br)
 * @author Nicolas Roduit
 */
public class PrintDialog<I extends ImageElement> extends javax.swing.JDialog {

  private final JCheckBox annotationsCheckBox =
      new JCheckBox(Messages.getString("PrintDialog.annotate"));
  private final JComboBox<String> positionComboBox = new JComboBox<>();
  private final JCheckBox selectedViewCheckbox =
      new JCheckBox(Messages.getString("PrintDialog.selected_view"));
  private final JComboBox<PrintOptions.DotPerInches> comboBoxDPI = new JComboBox<>();
  private final ImageViewerEventManager<I> eventManager;

  /** Creates new form PrintDialog */
  public PrintDialog(Window parent, String title, ImageViewerEventManager<I> eventManager) {
    super(parent, title, ModalityType.APPLICATION_MODAL);
    this.eventManager = eventManager;
    boolean layout =
        eventManager.getSelectedView2dContainer().getLayoutModel().getConstraints().size() > 1;
    initComponents(layout);
    pack();
  }

  private void initComponents(boolean layout) {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

    panel.setBorder(GuiUtils.getEmptyBorder(10, 20, 10, 20));

    JLabel positionLabel = new JLabel(Messages.getString("PrintDialog.pos") + StringUtil.COLON);
    positionComboBox.setModel(
        new javax.swing.DefaultComboBoxModel<>(
            new String[] {
              Messages.getString("PrintDialog.center"), Messages.getString("PrintDialog.top")
            }));
    panel.add(GuiUtils.getFlowLayoutPanel(2, 5, positionLabel, positionComboBox));

    JLabel label = new JLabel(Messages.getString("PrintDialog.dpi") + StringUtil.COLON);
    comboBoxDPI.setModel(new DefaultComboBoxModel<>(PrintOptions.DotPerInches.values()));
    comboBoxDPI.setSelectedItem(PrintOptions.DotPerInches.DPI_150);
    panel.add(GuiUtils.getFlowLayoutPanel(2, 5, label, comboBoxDPI));

    annotationsCheckBox.setSelected(true);
    panel.add(GuiUtils.getFlowLayoutPanel(2, 5, annotationsCheckBox));

    if (layout) {
      panel.add(GuiUtils.getFlowLayoutPanel(2, 5, selectedViewCheckbox));
    }

    javax.swing.JButton cancelButton =
        new javax.swing.JButton(Messages.getString("PrintDialog.cancel"));
    cancelButton.addActionListener(e -> dispose());

    javax.swing.JButton printButton =
        new javax.swing.JButton(Messages.getString("PrintDialog.print"));
    printButton.addActionListener(e -> printAction());
    getRootPane().setDefaultButton(printButton);

    panel.add(GuiUtils.boxYLastElement(15));
    panel.add(
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.TRAILING, 2, 5, printButton, GuiUtils.boxHorizontalStrut(20), cancelButton));
    setContentPane(panel);
    pack();
  }

  private void printAction() {
    PrintOptions printOptions = new PrintOptions();
    printOptions.setShowingAnnotations(annotationsCheckBox.isSelected());
    printOptions.setDpi((PrintOptions.DotPerInches) comboBoxDPI.getSelectedItem());
    printOptions.setCenter(
        Objects.equals(
            positionComboBox.getSelectedItem(), Messages.getString("PrintDialog.center")));

    ImageViewerPlugin<I> container = eventManager.getSelectedView2dContainer();

    List<ViewCanvas<I>> views = container.getImagePanels();
    if (views.isEmpty()) {
      JOptionPane.showMessageDialog(
          this, Messages.getString("PrintDialog.no_print"), null, JOptionPane.ERROR_MESSAGE);
      dispose();
      return;
    }
    dispose();

    ExportLayout<I> layout;
    if (!selectedViewCheckbox.isSelected()) {
      // Several views
      layout = new ExportLayout<>(container.getLayoutModel());
    } else {
      // One View
      layout = new ExportLayout<>(eventManager.getSelectedViewPane());
    }

    ImagePrint print = new ImagePrint(layout, printOptions);
    print.print();
    layout.dispose();
  }
}
