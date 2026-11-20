/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.print;

import java.awt.Component;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.print.Paper;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.Messages;

/**
 * The DICOM print attributes of a node. In {@link Scope#NODE} every default of the node is shown;
 * in {@link Scope#JOB} only what the print dialog does not already own.
 *
 * @author Marcelo Porto (marcelo@animati.com.br)
 * @author Nicolas Roduit
 */
public class DicomPrintOptionPane extends JPanel {

  public enum Scope {
    NODE,
    JOB
  }

  private final Scope scope;
  private final JLabel jobLabel;
  private final JComponent jobField;

  final JComboBox<String> mediumTypeComboBox = new JComboBox<>();
  final JComboBox<String> priorityComboBox =
      new JComboBox<>(new String[] {DicomPrintOptions.DEF_PRIORITY, "MED", "HIGH"}); // NON-NLS
  final JComboBox<String> filmDestinationComboBox =
      new JComboBox<>(new String[] {DicomPrintOptions.DEF_FILM_DEST, "PROCESSOR"}); // NON-NLS
  final JSpinner numOfCopiesSpinner =
      new JSpinner(new SpinnerNumberModel(DicomPrintOptions.DEF_NUM_COPIES, 1, 100, 1));
  final JCheckBox colorPrintCheckBox =
      new JCheckBox(Messages.getString("DicomPrintDialog.print_color"));
  final JComboBox<String> filmOrientationComboBox =
      new JComboBox<>(
          new String[] {DicomPrintOptions.DEF_FILM_ORIENTATION, "LANDSCAPE"}); // NON-NLS
  final JComboBox<Paper> filmSizeIdComboBox =
      new JComboBox<>(DicomPrintOptions.filmSizes().toArray(Paper[]::new));
  final JComboBox<String> magnificationTypeComboBox =
      new JComboBox<>(
          new String[] {
            "REPLICATE", "BILINEAR", DicomPrintOptions.DEF_MAGNIFICATION_TYPE
          }); // NON-NLS
  final JComboBox<String> smoothingTypeComboBox =
      new JComboBox<>(
          new String[] {
            DicomPrintOptions.PRINTER_DEFAULT,
            DicomPrintOptions.DEF_SMOOTHING_TYPE,
            "SHARP",
            "SMOOTH"
          }); // NON-NLS
  final JComboBox<String> borderDensityComboBox =
      new JComboBox<>(new String[] {"BLACK", DicomPrintOptions.DEF_BORDER_DENSITY}); // NON-NLS
  final JComboBox<String> trimComboBox =
      new JComboBox<>(new String[] {DicomPrintOptions.DEF_TRIM, "YES"}); // NON-NLS
  final JComboBox<String> emptyDensityComboBox =
      new JComboBox<>(new String[] {DicomPrintOptions.DEF_EMPTY_DENSITY, "WHITE"}); // NON-NLS
  final JCheckBox printAnnotationsCheckBox =
      new JCheckBox(Messages.getString("PrintDialog.annotate"));
  final JComboBox<Integer> filmResolutionComboBox =
      new JComboBox<>(DicomPrintOptions.FILM_RESOLUTIONS.toArray(Integer[]::new));
  final JComboBox<String> requestedResolutionComboBox =
      new JComboBox<>(DicomPrintOptions.REQUESTED_RESOLUTIONS.toArray(String[]::new));
  final JSpinner minDensitySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 500, 5));
  final JSpinner maxDensitySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 500, 5));
  final JTextField configurationField = new JTextField();

  public DicomPrintOptionPane() {
    this(Scope.NODE);
  }

  public DicomPrintOptionPane(Scope scope) {
    this(scope, null, null);
  }

  /**
   * @param jobLabel the label of a setting of the job that is not a print option, shown in the cell
   *     the job scope leaves free, or {@code null}
   * @param jobField its field
   */
  public DicomPrintOptionPane(Scope scope, JLabel jobLabel, JComponent jobField) {
    super();
    this.scope = scope;
    this.jobLabel = jobLabel;
    this.jobField = jobField;
    mediumTypeComboBox.setModel(
        new DefaultComboBoxModel<>(
            new String[] {
              DicomPrintOptions.DEF_MEDIUM_TYPE,
              "CLEAR FILM", // NON-NLS
              "MAMMO CLEAR FILM", // NON-NLS
              "MAMMO BLUE FILM", // NON-NLS
              "PAPER" // NON-NLS
            }));
    // Values of the printer: the usual ones are offered, another one can be typed
    smoothingTypeComboBox.setEditable(true);
    smoothingTypeComboBox.setToolTipText(Messages.getString("DicomPrintDialog.smooth_tip"));
    filmResolutionComboBox.setEditable(true);
    filmResolutionComboBox.setToolTipText(
        Messages.getString("DicomPrintDialog.film_resolution_tip"));
    requestedResolutionComboBox.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean selected, boolean focus) {
            Object text =
                DicomPrintOptions.PRINTER_DEFAULT.equals(value)
                    ? Messages.getString("DicomPrintDialog.printer_default")
                    : value;
            return super.getListCellRendererComponent(list, text, index, selected, focus);
          }
        });
    String densityTip = Messages.getString("DicomPrintDialog.density_tip");
    minDensitySpinner.setToolTipText(densityTip);
    maxDensitySpinner.setToolTipText(densityTip);
    configurationField.setToolTipText(Messages.getString("DicomPrintDialog.configuration_tip"));
    magnificationTypeComboBox.addActionListener(e -> updateSmoothing());
    initComponents();
    // A new node starts from the default of each setting, not from the first item of each list
    applyOptions(new DicomPrintOptions());
  }

  private void initComponents() {
    // Two columns of label and field; the fields share the width, so the combos line up
    setLayout(
        new MigLayout(
            "fillx, wrap 4, insets 5lp 10lp 10lp 10lp", // NON-NLS
            "[trailing]rel[grow,fill,110lp::]unrel[trailing]rel[grow,fill,110lp::]")); // NON-NLS
    setBorder(GuiUtils.getTitledBorder(Messages.getString("DicomPrintDialog.option_title")));
    boolean node = scope == Scope.NODE;

    add(label("DicomPrintDialog.med_type"));
    add(mediumTypeComboBox);
    add(label("DicomPrintDialog.priority"));
    add(priorityComboBox);

    add(label("DicomPrintDialog.film_dest"));
    add(filmDestinationComboBox, node || jobField != null ? "" : "wrap"); // NON-NLS
    if (!node && jobField != null) {
      add(jobLabel == null ? new JLabel() : jobLabel);
      add(jobField);
    }
    if (node) {
      add(label("DicomPrintDialog.copies"));
      add(numOfCopiesSpinner, "growx 0, width 70lp"); // NON-NLS

      add(label("DicomPrintDialog.film_orientation"));
      add(filmOrientationComboBox);
      add(label("DicomPrintDialog.size_id"));
      add(filmSizeIdComboBox);

      add(label("DicomPrintDialog.film_resolution"));
      add(filmResolutionComboBox);
      add(label("DicomPrintDialog.requested_resolution"));
      add(requestedResolutionComboBox);
    }

    add(label("DicomPrintDialog.magn_type"));
    add(magnificationTypeComboBox);
    add(label("DicomPrintDialog.smooth"));
    add(smoothingTypeComboBox);

    add(label("DicomPrintDialog.border"));
    add(borderDensityComboBox);
    add(label("DicomPrintDialog.trim"));
    add(trimComboBox);

    if (node) {
      add(label("DicomPrintDialog.empty_density"));
      add(emptyDensityComboBox, "wrap"); // NON-NLS

      add(label("DicomPrintDialog.min_density"));
      add(minDensitySpinner, "growx 0, width 70lp"); // NON-NLS
      add(label("DicomPrintDialog.max_density"));
      add(maxDensitySpinner, "growx 0, width 70lp"); // NON-NLS

      add(label("DicomPrintDialog.configuration"));
      add(configurationField, "span 3"); // NON-NLS
    }
    add(colorPrintCheckBox, "skip 1, growx 0, gaptop rel"); // NON-NLS
    if (node) {
      add(printAnnotationsCheckBox, "skip 1, growx 0, gaptop rel"); // NON-NLS
    }
  }

  private static JLabel label(String key) {
    return new JLabel(Messages.getString(key) + StringUtil.COLON);
  }

  /** Runs {@code listener} whenever one of the shown settings changes. */
  public void addChangeListener(Runnable listener) {
    for (JComboBox<?> combo :
        new JComboBox<?>[] {
          mediumTypeComboBox,
          priorityComboBox,
          filmDestinationComboBox,
          filmOrientationComboBox,
          filmSizeIdComboBox,
          magnificationTypeComboBox,
          smoothingTypeComboBox,
          borderDensityComboBox,
          trimComboBox,
          emptyDensityComboBox,
          filmResolutionComboBox,
          requestedResolutionComboBox
        }) {
      combo.addActionListener(e -> listener.run());
    }
    numOfCopiesSpinner.addChangeListener(e -> listener.run());
    minDensitySpinner.addChangeListener(e -> listener.run());
    maxDensitySpinner.addChangeListener(e -> listener.run());
    configurationField.addActionListener(e -> listener.run());
    colorPrintCheckBox.addActionListener(e -> listener.run());
    printAnnotationsCheckBox.addActionListener(e -> listener.run());
  }

  public void applyOptions(DicomPrintOptions options) {
    if (options != null) {
      mediumTypeComboBox.setSelectedItem(options.getMediumType());
      priorityComboBox.setSelectedItem(options.getPriority());
      filmDestinationComboBox.setSelectedItem(options.getFilmDestination());
      numOfCopiesSpinner.setValue(options.getNumOfCopies());
      colorPrintCheckBox.setSelected(options.isColorPrint());
      filmOrientationComboBox.setSelectedItem(options.getFilmOrientation());
      filmSizeIdComboBox.setSelectedItem(options.getFilmSizeId());
      magnificationTypeComboBox.setSelectedItem(options.getMagnificationType());
      smoothingTypeComboBox.setSelectedItem(options.getSmoothingType());
      borderDensityComboBox.setSelectedItem(options.getBorderDensity());
      trimComboBox.setSelectedItem(options.getTrim());
      emptyDensityComboBox.setSelectedItem(options.getEmptyDensity());
      printAnnotationsCheckBox.setSelected(options.isShowingAnnotations());
      filmResolutionComboBox.setSelectedItem(options.getFilmResolution());
      requestedResolutionComboBox.setSelectedItem(options.getRequestedResolution());
      minDensitySpinner.setValue(options.getMinDensity());
      maxDensitySpinner.setValue(options.getMaxDensity());
      configurationField.setText(options.getConfigurationInformation());
      updateSmoothing();
    }
  }

  /** The smoothing type is defined for the cubic magnification only. */
  private void updateSmoothing() {
    smoothingTypeComboBox.setEnabled(
        DicomPrintOptions.CUBIC.equals(magnificationTypeComboBox.getSelectedItem()));
  }

  public void saveOptions(DicomPrintOptions options) {
    options.setMediumType((String) mediumTypeComboBox.getSelectedItem());
    options.setPriority((String) priorityComboBox.getSelectedItem());
    options.setFilmDestination((String) filmDestinationComboBox.getSelectedItem());
    options.setNumOfCopies((Integer) numOfCopiesSpinner.getValue());
    options.setColorPrint(colorPrintCheckBox.isSelected());
    options.setFilmOrientation((String) filmOrientationComboBox.getSelectedItem());
    options.setFilmSizeId((Paper) filmSizeIdComboBox.getSelectedItem());
    options.setMagnificationType((String) magnificationTypeComboBox.getSelectedItem());
    Object smoothing = smoothingTypeComboBox.getSelectedItem();
    options.setSmoothingType(smoothing == null ? null : smoothing.toString());
    options.setBorderDensity((String) borderDensityComboBox.getSelectedItem());
    options.setTrim((String) trimComboBox.getSelectedItem());
    options.setEmptyDensity((String) emptyDensityComboBox.getSelectedItem());
    options.setShowingAnnotations(printAnnotationsCheckBox.isSelected());
    // A typed value comes as text
    options.setFilmResolution(
        DicomPrintOptions.filmResolutionOf(
            String.valueOf(filmResolutionComboBox.getSelectedItem()), options.getFilmResolution()));
    options.setRequestedResolution((String) requestedResolutionComboBox.getSelectedItem());
    options.setMinDensity((Integer) minDensitySpinner.getValue());
    options.setMaxDensity((Integer) maxDensitySpinner.getValue());
    options.setConfigurationInformation(configurationField.getText());
  }
}
