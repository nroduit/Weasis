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

import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
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
  final JComboBox<String> imageDisplayFormatComboBox =
      new JComboBox<>(new String[] {DicomPrintOptions.DEF_IMG_DISP_FORMAT});
  final JComboBox<String> magnificationTypeComboBox =
      new JComboBox<>(
          new String[] {
            "REPLICATE", "BILINEAR", DicomPrintOptions.DEF_MAGNIFICATION_TYPE
          }); // NON-NLS
  final JComboBox<String> smoothingTypeComboBox =
      new JComboBox<>(
          new String[] {DicomPrintOptions.DEF_SMOOTHING_TYPE, "SHARP", "SMOOTH"}); // NON-NLS
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

  public DicomPrintOptionPane() {
    this(Scope.NODE);
  }

  public DicomPrintOptionPane(Scope scope) {
    super();
    this.scope = scope;
    mediumTypeComboBox.setModel(
        new DefaultComboBoxModel<>(
            new String[] {
              DicomPrintOptions.DEF_MEDIUM_TYPE,
              "CLEAR FILM", // NON-NLS
              "MAMMO CLEAR FILM", // NON-NLS
              "MAMMO BLUE FILM", // NON-NLS
              "PAPER" // NON-NLS
            }));
    imageDisplayFormatComboBox.setMaximumRowCount(10);
    printAnnotationsCheckBox.setSelected(true);
    initComponents();
  }

  private void initComponents() {
    setLayout(
        new MigLayout("fillx", "[trailing]rel[leading]15lp[trailing]rel[leading]")); // NON-NLS
    setBorder(GuiUtils.getTitledBorder(Messages.getString("DicomPrintDialog.option_title")));

    add(label("DicomPrintDialog.med_type"), GuiUtils.NEWLINE);
    add(mediumTypeComboBox);
    add(label("DicomPrintDialog.priority"));
    add(priorityComboBox);

    add(label("DicomPrintDialog.film_dest"), GuiUtils.NEWLINE);
    add(filmDestinationComboBox);
    if (scope == Scope.NODE) {
      add(label("DicomPrintDialog.copies"));
      add(numOfCopiesSpinner);
    }

    add(colorPrintCheckBox, "newline, alignx leading, spanx 2"); // NON-NLS
    add(new JSeparator(), "newline, spanx"); // NON-NLS

    if (scope == Scope.NODE) {
      add(label("DicomPrintDialog.film_orientation"), GuiUtils.NEWLINE);
      add(filmOrientationComboBox);
      add(label("DicomPrintDialog.size_id"));
      add(filmSizeIdComboBox);

      add(label("DicomPrintDialog.disp_format"), GuiUtils.NEWLINE);
      add(imageDisplayFormatComboBox);
      add(label("DicomPrintDialog.film_resolution"));
      add(filmResolutionComboBox);
    }

    add(label("DicomPrintDialog.magn_type"), GuiUtils.NEWLINE);
    add(magnificationTypeComboBox);
    add(label("DicomPrintDialog.smooth"));
    add(smoothingTypeComboBox);

    add(label("DicomPrintDialog.border"), GuiUtils.NEWLINE);
    add(borderDensityComboBox);
    add(label("DicomPrintDialog.trim"));
    add(trimComboBox);

    if (scope == Scope.NODE) {
      add(label("DicomPrintDialog.empty_density"), GuiUtils.NEWLINE);
      add(emptyDensityComboBox);
      add(printAnnotationsCheckBox, "newline, alignx leading, spanx 2"); // NON-NLS
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
          imageDisplayFormatComboBox,
          magnificationTypeComboBox,
          smoothingTypeComboBox,
          borderDensityComboBox,
          trimComboBox,
          emptyDensityComboBox,
          filmResolutionComboBox
        }) {
      combo.addActionListener(e -> listener.run());
    }
    numOfCopiesSpinner.addChangeListener(e -> listener.run());
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
      imageDisplayFormatComboBox.setSelectedItem(options.getImageDisplayFormat());
      magnificationTypeComboBox.setSelectedItem(options.getMagnificationType());
      smoothingTypeComboBox.setSelectedItem(options.getSmoothingType());
      borderDensityComboBox.setSelectedItem(options.getBorderDensity());
      trimComboBox.setSelectedItem(options.getTrim());
      emptyDensityComboBox.setSelectedItem(options.getEmptyDensity());
      printAnnotationsCheckBox.setSelected(options.isShowingAnnotations());
      filmResolutionComboBox.setSelectedItem(options.getFilmResolution());
    }
  }

  public void saveOptions(DicomPrintOptions options) {
    options.setMediumType((String) mediumTypeComboBox.getSelectedItem());
    options.setPriority((String) priorityComboBox.getSelectedItem());
    options.setFilmDestination((String) filmDestinationComboBox.getSelectedItem());
    options.setNumOfCopies((Integer) numOfCopiesSpinner.getValue());
    options.setColorPrint(colorPrintCheckBox.isSelected());
    options.setFilmOrientation((String) filmOrientationComboBox.getSelectedItem());
    options.setFilmSizeId((Paper) filmSizeIdComboBox.getSelectedItem());
    options.setImageDisplayFormat((String) imageDisplayFormatComboBox.getSelectedItem());
    options.setMagnificationType((String) magnificationTypeComboBox.getSelectedItem());
    options.setSmoothingType((String) smoothingTypeComboBox.getSelectedItem());
    options.setBorderDensity((String) borderDensityComboBox.getSelectedItem());
    options.setTrim((String) trimComboBox.getSelectedItem());
    options.setEmptyDensity((String) emptyDensityComboBox.getSelectedItem());
    options.setShowingAnnotations(printAnnotationsCheckBox.isSelected());
    if (filmResolutionComboBox.getSelectedItem() instanceof Integer dpi) {
      options.setFilmResolution(dpi);
    }
  }
}
