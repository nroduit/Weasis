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
import org.weasis.core.ui.util.PrintOptions;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.print.DicomPrintDialog.FilmSize;

public class DicomPrintOptionPane extends JPanel {

  JComboBox<String> borderDensityComboBox;
  JCheckBox colorPrintCheckBox;
  JComboBox<String> filmDestinationComboBox;
  JComboBox<String> filmOrientationComboBox;
  JComboBox<FilmSize> filmSizeIdComboBox;
  JComboBox<String> imageDisplayFormatComboBox;
  JComboBox<String> magnificationTypeComboBox;
  //  JSpinner maxDensitySpinner;
  //  JSpinner minDensitySpinner;
  JComboBox<String> mediumTypeComboBox;
  JSpinner numOfCopiesSpinner;
  JCheckBox printAnnotationsCheckBox;
  JSeparator printOptionsSeparator;
  JComboBox<String> priorityComboBox;
  JComboBox<String> smoothingTypeComboBox;
  JComboBox<String> trimComboBox;
  DefaultComboBoxModel<String> portraitDisplayFormatsModel;
  JCheckBox checkboxSelectedView;
  JComboBox<PrintOptions.DotPerInches> comboBoxDPI;
  JComboBox<String> comboBoxEmpty;

  public DicomPrintOptionPane() {
    super();
    portraitDisplayFormatsModel =
        new DefaultComboBoxModel<>(new String[] {DicomPrintOptions.DEF_IMG_DISP_FORMAT});
    initComponents();
  }

  private void initComponents() {
    setLayout(new MigLayout("fillx", "[right]rel[left]15lp[right]rel[left]")); // NON-NLS
    setBorder(GuiUtils.getTitledBorder(Messages.getString("DicomPrintDialog.option_title")));

    add(
        new JLabel(Messages.getString("DicomPrintDialog.med_type") + StringUtil.COLON),
        GuiUtils.NEWLINE);
    mediumTypeComboBox = new JComboBox<>();
    mediumTypeComboBox.setModel(
        new DefaultComboBoxModel<>(
            new String[] {
              DicomPrintOptions.DEF_MEDIUM_TYPE,
              "CLEAR FILM", // NON-NLS
              "MAMMO CLEAR FILM", // NON-NLS
              "MAMMO BLUE FILM", // NON-NLS
              "PAPER" // NON-NLS
            }));
    add(mediumTypeComboBox);

    add(new JLabel(Messages.getString("DicomPrintDialog.priority") + StringUtil.COLON));
    priorityComboBox =
        new JComboBox<>(
            new DefaultComboBoxModel<>(
                new String[] {DicomPrintOptions.DEF_PRIORITY, "MED", "HIGH"})); // NON-NLS
    add(priorityComboBox);

    add(
        new JLabel(Messages.getString("DicomPrintDialog.film_dest") + StringUtil.COLON),
        GuiUtils.NEWLINE);

    filmDestinationComboBox =
        new JComboBox<>(
            new DefaultComboBoxModel<>(
                new String[] {DicomPrintOptions.DEF_FILM_DEST, "PROCESSOR"}));
    add(filmDestinationComboBox);

    add(new JLabel(Messages.getString("DicomPrintDialog.copies") + StringUtil.COLON));
    numOfCopiesSpinner =
        new JSpinner(new SpinnerNumberModel(DicomPrintOptions.DEF_NUM_COPIES, 1, 100, 1));
    add(numOfCopiesSpinner);

    colorPrintCheckBox = new JCheckBox(Messages.getString("DicomPrintDialog.print_color"));
    add(colorPrintCheckBox, "newline, alignx leading, spanx 2"); // NON-NLS

    printOptionsSeparator = new JSeparator();
    add(printOptionsSeparator, "newline, spanx"); // NON-NLS

    add(
        new JLabel(Messages.getString("DicomPrintDialog.film_orientation") + StringUtil.COLON),
        GuiUtils.NEWLINE);
    filmOrientationComboBox =
        new JComboBox<>(
            new DefaultComboBoxModel<>(
                new String[] {DicomPrintOptions.DEF_FILM_ORIENTATION, "LANDSCAPE"}));
    add(filmOrientationComboBox);

    add(new JLabel(Messages.getString("DicomPrintDialog.size_id") + StringUtil.COLON));
    filmSizeIdComboBox = new JComboBox<>(new DefaultComboBoxModel<>(FilmSize.values()));
    add(filmSizeIdComboBox);

    add(
        new JLabel(Messages.getString("DicomPrintDialog.disp_format") + StringUtil.COLON),
        GuiUtils.NEWLINE);
    imageDisplayFormatComboBox = new JComboBox<>(portraitDisplayFormatsModel);
    imageDisplayFormatComboBox.setMaximumRowCount(10);
    add(imageDisplayFormatComboBox);

    add(new JLabel(Messages.getString("DicomPrintDialog.magn_type") + StringUtil.COLON));
    magnificationTypeComboBox =
        new JComboBox<>(
            new DefaultComboBoxModel<>(
                new String[] {
                  "REPLICATE", "BILINEAR", DicomPrintOptions.DEF_MAGNIFICATION_TYPE
                })); // NON-NLS
    add(magnificationTypeComboBox);

    add(
        new JLabel(Messages.getString("DicomPrintDialog.smooth") + StringUtil.COLON),
        GuiUtils.NEWLINE);
    smoothingTypeComboBox =
        new JComboBox<>(
            new DefaultComboBoxModel<>(
                new String[] {DicomPrintOptions.DEF_SMOOTHING_TYPE, "SHARP", "SMOOTH"})); // NON-NLS
    add(smoothingTypeComboBox);

    add(new JLabel(Messages.getString("DicomPrintDialog.border") + StringUtil.COLON));
    borderDensityComboBox =
        new JComboBox<>(
            new DefaultComboBoxModel<>(
                new String[] {"BLACK", DicomPrintOptions.DEF_BORDER_DENSITY}));
    add(borderDensityComboBox);

    //    add(new JLabel(Messages.getString("DicomPrintDialog.min_density") + StringUtil.COLON),
    // GuiUtils.NEWLINE);
    //     minDensitySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 255, 1));
    //     // Not used for 8 bits images
    //     minDensitySpinner.setEnabled(false);
    //     add(minDensitySpinner);
    //
    //    add(new JLabel(Messages.getString("DicomPrintDialog.max_density") + StringUtil.COLON));
    //     maxDensitySpinner = new JSpinner(new SpinnerNumberModel(255, 0, 255, 1));
    //     // Not used for 8 bits images
    //     maxDensitySpinner.setEnabled(false);
    //     add(maxDensitySpinner);

    add(
        new JLabel(Messages.getString("DicomPrintDialog.trim") + StringUtil.COLON),
        GuiUtils.NEWLINE);
    trimComboBox =
        new JComboBox<>(
            new DefaultComboBoxModel<>(new String[] {DicomPrintOptions.DEF_TRIM, "YES"}));
    add(trimComboBox);

    add(new JLabel(Messages.getString("DicomPrintDialog.empty_density") + StringUtil.COLON));
    comboBoxEmpty =
        new JComboBox<>(
            new DefaultComboBoxModel<>(
                new String[] {DicomPrintOptions.DEF_EMPTY_DENSITY, "WHITE"}));
    add(comboBoxEmpty);

    printAnnotationsCheckBox = new JCheckBox(Messages.getString("PrintDialog.annotate"));
    printAnnotationsCheckBox.setSelected(true);
    add(printAnnotationsCheckBox, "newline, alignx leading, spanx 2"); // NON-NLS

    checkboxSelectedView = new JCheckBox(Messages.getString("PrintDialog.selected_view"));
    add(checkboxSelectedView, "newline, alignx leading, spanx 2"); // NON-NLS

    add(new JLabel(Messages.getString("DicomPrintDialog.dpi") + StringUtil.COLON));

    comboBoxDPI = new JComboBox<>(new DefaultComboBoxModel<>(PrintOptions.DotPerInches.values()));
    add(comboBoxDPI);
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
      comboBoxEmpty.setSelectedItem(options.getEmptyDensity());
      // minDensitySpinner.setValue(options.getMinDensity());
      // maxDensitySpinner.setValue(options.getMaxDensity());
      printAnnotationsCheckBox.setSelected(options.isShowingAnnotations());
      checkboxSelectedView.setSelected(options.isPrintOnlySelectedView());
      comboBoxDPI.setSelectedItem(options.getDpi());
    }
  }

  public void saveOptions(DicomPrintOptions options) {
    options.setMediumType((String) mediumTypeComboBox.getSelectedItem());
    options.setPriority((String) priorityComboBox.getSelectedItem());
    options.setFilmDestination((String) filmDestinationComboBox.getSelectedItem());
    options.setNumOfCopies((Integer) numOfCopiesSpinner.getValue());
    options.setColorPrint(colorPrintCheckBox.isSelected());

    options.setFilmOrientation((String) filmOrientationComboBox.getSelectedItem());
    options.setFilmSizeId((FilmSize) filmSizeIdComboBox.getSelectedItem());
    options.setImageDisplayFormat((String) imageDisplayFormatComboBox.getSelectedItem());
    options.setMagnificationType((String) magnificationTypeComboBox.getSelectedItem());
    options.setSmoothingType((String) smoothingTypeComboBox.getSelectedItem());
    options.setBorderDensity((String) borderDensityComboBox.getSelectedItem());
    options.setTrim((String) trimComboBox.getSelectedItem());
    options.setEmptyDensity((String) comboBoxEmpty.getSelectedItem());
    // options.setMinDensity((Integer) minDensitySpinner.getValue());
    // options.setMaxDensity((Integer) maxDensitySpinner.getValue());
    options.setShowingAnnotations(printAnnotationsCheckBox.isSelected());
    options.setPrintOnlySelectedView(checkboxSelectedView.isSelected());
    options.setDpi((PrintOptions.DotPerInches) comboBoxDPI.getSelectedItem());
  }
}
