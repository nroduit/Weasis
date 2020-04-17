/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.explorer.print;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.TitledBorder;

import org.weasis.core.util.StringUtil;
import org.weasis.core.ui.util.PrintOptions;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.print.DicomPrintDialog.FilmSize;

public class DicomPrintOptionPane extends JPanel {

    JComboBox<String> borderDensityComboBox;
    JLabel borderDensityLabel;
    JCheckBox colorPrintCheckBox;
    JComboBox<String> filmDestinationComboBox;
    JLabel filmDestinationLabel;
    JComboBox<String> filmOrientationComboBox;
    JLabel filmOrientationLabel;
    JComboBox<FilmSize> filmSizeIdComboBox;
    JLabel filmSizeIdLabel;
    JComboBox<String> imageDisplayFormatComboBox;
    JLabel imageDisplayLabel;
    JComboBox<String> magnificationTypeComboBox;
    JLabel magnificationTypeLabel;
    // JLabel maxDensityLabel;
    // JSpinner maxDensitySpinner;
    JComboBox<String> mediumTypeComboBox;
    JLabel mediumTypeLabel;
    // JLabel minDensityLabel;
    // JSpinner minDensitySpinner;
    JLabel numOfCopiesLabel;
    JSpinner numOfCopiesSpinner;
    JCheckBox printAnnotationsCheckBox;
    JSeparator printOptionsSeparator;
    JComboBox<String> priorityComboBox;
    JLabel priorityLabel;
    JComboBox<String> smoothingTypeComboBox;
    JLabel smoothingTypeLabel;
    JComboBox<String> trimComboBox;
    JLabel trimLabel;
    DefaultComboBoxModel<String> portraitDisplayFormatsModel;
    JCheckBox chckbxSelctedView;
    JLabel label;
    JComboBox<PrintOptions.DotPerInches> comboBoxDPI;
    JLabel labelEmpty;
    JComboBox<String> comboBoxEmpty;

    public DicomPrintOptionPane() {
        super();
        portraitDisplayFormatsModel =
            new DefaultComboBoxModel<>(new String[] { DicomPrintOptions.DEF_IMG_DISP_FORMAT });
        initComponents();
    }

    private void initComponents() {

        this.setBorder(new TitledBorder(null, Messages.getString("DicomPrintDialog.option_title"), //$NON-NLS-1$
            TitledBorder.LEADING, TitledBorder.TOP, null, null));
        GridBagLayout gblContent = new GridBagLayout();
        this.setLayout(gblContent);

        mediumTypeLabel = new JLabel();
        mediumTypeLabel.setText(Messages.getString("DicomPrintDialog.med_type") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcMediumTypeLabel = new GridBagConstraints();
        gbcMediumTypeLabel.anchor = GridBagConstraints.EAST;
        gbcMediumTypeLabel.insets = new Insets(0, 0, 5, 5);
        gbcMediumTypeLabel.gridx = 0;
        gbcMediumTypeLabel.gridy = 0;
        this.add(mediumTypeLabel, gbcMediumTypeLabel);

        mediumTypeComboBox = new JComboBox<>();
        mediumTypeComboBox.setModel(new DefaultComboBoxModel<>(new String[] { DicomPrintOptions.DEF_MEDIUM_TYPE,
            "CLEAR FILM", "MAMMO CLEAR FILM", "MAMMO BLUE FILM", "PAPER" })); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        GridBagConstraints gbcMediumTypeComboBox = new GridBagConstraints();
        gbcMediumTypeComboBox.anchor = GridBagConstraints.WEST;
        gbcMediumTypeComboBox.insets = new Insets(0, 0, 5, 5);
        gbcMediumTypeComboBox.gridx = 1;
        gbcMediumTypeComboBox.gridy = 0;
        this.add(mediumTypeComboBox, gbcMediumTypeComboBox);

        priorityLabel = new JLabel();
        priorityLabel.setText(Messages.getString("DicomPrintDialog.priority") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcPriorityLabel = new GridBagConstraints();
        gbcPriorityLabel.anchor = GridBagConstraints.EAST;
        gbcPriorityLabel.insets = new Insets(0, 0, 5, 5);
        gbcPriorityLabel.gridx = 3;
        gbcPriorityLabel.gridy = 0;
        this.add(priorityLabel, gbcPriorityLabel);

        priorityComboBox = new JComboBox<>();
        priorityComboBox
            .setModel(new DefaultComboBoxModel<>(new String[] { DicomPrintOptions.DEF_PRIORITY, "MED", "HIGH" })); //$NON-NLS-1$ //$NON-NLS-2$
                                                                                                                   // //
                                                                                                                   // //$NON-NLS-3$
        GridBagConstraints gbcPriorityComboBox = new GridBagConstraints();
        gbcPriorityComboBox.anchor = GridBagConstraints.WEST;
        gbcPriorityComboBox.insets = new Insets(0, 0, 5, 0);
        gbcPriorityComboBox.gridx = 4;
        gbcPriorityComboBox.gridy = 0;
        this.add(priorityComboBox, gbcPriorityComboBox);

        filmDestinationLabel = new JLabel();
        filmDestinationLabel.setText(Messages.getString("DicomPrintDialog.film_dest") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcFilmDestinationLabel = new GridBagConstraints();
        gbcFilmDestinationLabel.anchor = GridBagConstraints.EAST;
        gbcFilmDestinationLabel.insets = new Insets(0, 0, 5, 5);
        gbcFilmDestinationLabel.gridx = 0;
        gbcFilmDestinationLabel.gridy = 1;
        this.add(filmDestinationLabel, gbcFilmDestinationLabel);

        filmDestinationComboBox = new JComboBox<>();
        filmDestinationComboBox
            .setModel(new DefaultComboBoxModel<>(new String[] { DicomPrintOptions.DEF_FILM_DEST, "PROCESSOR" })); //$NON-NLS-1$
        GridBagConstraints gbcFilmDestinationComboBox = new GridBagConstraints();
        gbcFilmDestinationComboBox.fill = GridBagConstraints.VERTICAL;
        gbcFilmDestinationComboBox.anchor = GridBagConstraints.WEST;
        gbcFilmDestinationComboBox.insets = new Insets(0, 0, 5, 5);
        gbcFilmDestinationComboBox.gridx = 1;
        gbcFilmDestinationComboBox.gridy = 1;
        this.add(filmDestinationComboBox, gbcFilmDestinationComboBox);

        numOfCopiesLabel = new JLabel();
        numOfCopiesLabel.setText(Messages.getString("DicomPrintDialog.copies") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcNumOfCopiesLabel = new GridBagConstraints();
        gbcNumOfCopiesLabel.anchor = GridBagConstraints.EAST;
        gbcNumOfCopiesLabel.insets = new Insets(0, 0, 5, 5);
        gbcNumOfCopiesLabel.gridx = 3;
        gbcNumOfCopiesLabel.gridy = 1;
        this.add(numOfCopiesLabel, gbcNumOfCopiesLabel);

        numOfCopiesSpinner = new JSpinner(new SpinnerNumberModel(DicomPrintOptions.DEF_NUM_COPIES, 1, 100, 1));
        GridBagConstraints gbcNumOfCopiesSpinner = new GridBagConstraints();
        gbcNumOfCopiesSpinner.anchor = GridBagConstraints.WEST;
        gbcNumOfCopiesSpinner.insets = new Insets(0, 0, 5, 0);
        gbcNumOfCopiesSpinner.gridx = 4;
        gbcNumOfCopiesSpinner.gridy = 1;
        this.add(numOfCopiesSpinner, gbcNumOfCopiesSpinner);

        colorPrintCheckBox = new JCheckBox();
        colorPrintCheckBox.setText(Messages.getString("DicomPrintDialog.print_color")); //$NON-NLS-1$
        GridBagConstraints gbcColorPrintCheckBox = new GridBagConstraints();
        gbcColorPrintCheckBox.anchor = GridBagConstraints.WEST;
        gbcColorPrintCheckBox.insets = new Insets(0, 0, 5, 5);
        gbcColorPrintCheckBox.gridx = 1;
        gbcColorPrintCheckBox.gridy = 2;
        this.add(colorPrintCheckBox, gbcColorPrintCheckBox);

        printOptionsSeparator = new JSeparator();
        GridBagConstraints gbcPrintOptionsSeparator = new GridBagConstraints();
        gbcPrintOptionsSeparator.anchor = GridBagConstraints.NORTH;
        gbcPrintOptionsSeparator.fill = GridBagConstraints.HORIZONTAL;
        gbcPrintOptionsSeparator.insets = new Insets(0, 0, 5, 0);
        gbcPrintOptionsSeparator.gridwidth = 5;
        gbcPrintOptionsSeparator.gridx = 0;
        gbcPrintOptionsSeparator.gridy = 3;
        this.add(printOptionsSeparator, gbcPrintOptionsSeparator);

        filmOrientationLabel = new JLabel();
        filmOrientationLabel.setText(Messages.getString("DicomPrintDialog.film_orientation") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcFilmOrientationLabel = new GridBagConstraints();
        gbcFilmOrientationLabel.anchor = GridBagConstraints.EAST;
        gbcFilmOrientationLabel.insets = new Insets(0, 0, 5, 5);
        gbcFilmOrientationLabel.gridx = 0;
        gbcFilmOrientationLabel.gridy = 4;
        this.add(filmOrientationLabel, gbcFilmOrientationLabel);

        filmOrientationComboBox = new JComboBox<>();
        filmOrientationComboBox
            .setModel(new DefaultComboBoxModel<>(new String[] { DicomPrintOptions.DEF_FILM_ORIENTATION, "LANDSCAPE" })); //$NON-NLS-1$
        GridBagConstraints gbcFilmOrientationComboBox = new GridBagConstraints();
        gbcFilmOrientationComboBox.anchor = GridBagConstraints.WEST;
        gbcFilmOrientationComboBox.insets = new Insets(0, 0, 5, 5);
        gbcFilmOrientationComboBox.gridx = 1;
        gbcFilmOrientationComboBox.gridy = 4;
        this.add(filmOrientationComboBox, gbcFilmOrientationComboBox);

        filmSizeIdLabel = new JLabel();
        filmSizeIdLabel.setText(Messages.getString("DicomPrintDialog.size_id") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcFilmSizeIdLabel = new GridBagConstraints();
        gbcFilmSizeIdLabel.anchor = GridBagConstraints.EAST;
        gbcFilmSizeIdLabel.insets = new Insets(0, 0, 5, 5);
        gbcFilmSizeIdLabel.gridx = 3;
        gbcFilmSizeIdLabel.gridy = 4;
        this.add(filmSizeIdLabel, gbcFilmSizeIdLabel);

        filmSizeIdComboBox = new JComboBox<>();
        filmSizeIdComboBox.setModel(new DefaultComboBoxModel<>(FilmSize.values()));
        GridBagConstraints gbcFilmSizeIdComboBox = new GridBagConstraints();
        gbcFilmSizeIdComboBox.anchor = GridBagConstraints.WEST;
        gbcFilmSizeIdComboBox.insets = new Insets(0, 0, 5, 0);
        gbcFilmSizeIdComboBox.gridx = 4;
        gbcFilmSizeIdComboBox.gridy = 4;
        this.add(filmSizeIdComboBox, gbcFilmSizeIdComboBox);

        imageDisplayLabel = new JLabel();
        imageDisplayLabel.setText(Messages.getString("DicomPrintDialog.disp_format") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcImageDisplayLabel = new GridBagConstraints();
        gbcImageDisplayLabel.anchor = GridBagConstraints.EAST;
        gbcImageDisplayLabel.insets = new Insets(0, 0, 5, 5);
        gbcImageDisplayLabel.gridx = 0;
        gbcImageDisplayLabel.gridy = 5;
        this.add(imageDisplayLabel, gbcImageDisplayLabel);

        imageDisplayFormatComboBox = new JComboBox<>();
        imageDisplayFormatComboBox.setMaximumRowCount(10);
        imageDisplayFormatComboBox.setModel(portraitDisplayFormatsModel);
        GridBagConstraints gbcImageDisplayFormatComboBox = new GridBagConstraints();
        gbcImageDisplayFormatComboBox.anchor = GridBagConstraints.WEST;
        gbcImageDisplayFormatComboBox.insets = new Insets(0, 0, 5, 5);
        gbcImageDisplayFormatComboBox.gridx = 1;
        gbcImageDisplayFormatComboBox.gridy = 5;
        this.add(imageDisplayFormatComboBox, gbcImageDisplayFormatComboBox);

        magnificationTypeLabel = new JLabel();
        magnificationTypeLabel.setText(Messages.getString("DicomPrintDialog.magn_type") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcMagnificationTypeLabel = new GridBagConstraints();
        gbcMagnificationTypeLabel.anchor = GridBagConstraints.EAST;
        gbcMagnificationTypeLabel.insets = new Insets(0, 0, 5, 5);
        gbcMagnificationTypeLabel.gridx = 3;
        gbcMagnificationTypeLabel.gridy = 5;
        this.add(magnificationTypeLabel, gbcMagnificationTypeLabel);

        magnificationTypeComboBox = new JComboBox<>();
        magnificationTypeComboBox.setModel(new DefaultComboBoxModel<>(
            new String[] { "REPLICATE", "BILINEAR", DicomPrintOptions.DEF_MAGNIFICATION_TYPE })); //$NON-NLS-1$ //$NON-NLS-2$
                                                                                                  // //$NON-NLS-3$
        GridBagConstraints gbcMagnificationTypeComboBox = new GridBagConstraints();
        gbcMagnificationTypeComboBox.anchor = GridBagConstraints.WEST;
        gbcMagnificationTypeComboBox.insets = new Insets(0, 0, 5, 0);
        gbcMagnificationTypeComboBox.gridx = 4;
        gbcMagnificationTypeComboBox.gridy = 5;
        this.add(magnificationTypeComboBox, gbcMagnificationTypeComboBox);

        smoothingTypeLabel = new JLabel();
        smoothingTypeLabel.setText(Messages.getString("DicomPrintDialog.smooth") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcSmoothingTypeLabel = new GridBagConstraints();
        gbcSmoothingTypeLabel.anchor = GridBagConstraints.EAST;
        gbcSmoothingTypeLabel.insets = new Insets(0, 0, 5, 5);
        gbcSmoothingTypeLabel.gridx = 0;
        gbcSmoothingTypeLabel.gridy = 6;
        this.add(smoothingTypeLabel, gbcSmoothingTypeLabel);

        smoothingTypeComboBox = new JComboBox<>();
        smoothingTypeComboBox.setModel(
            new DefaultComboBoxModel<>(new String[] { DicomPrintOptions.DEF_SMOOTHING_TYPE, "SHARP", "SMOOTH" })); //$NON-NLS-1$ //$NON-NLS-2$
                                                                                                                   // //$NON-NLS-3$
        GridBagConstraints gbcSmoothingTypeComboBox = new GridBagConstraints();
        gbcSmoothingTypeComboBox.fill = GridBagConstraints.HORIZONTAL;
        gbcSmoothingTypeComboBox.insets = new Insets(0, 0, 5, 5);
        gbcSmoothingTypeComboBox.gridx = 1;
        gbcSmoothingTypeComboBox.gridy = 6;
        this.add(smoothingTypeComboBox, gbcSmoothingTypeComboBox);

        borderDensityLabel = new JLabel();
        borderDensityLabel.setText(Messages.getString("DicomPrintDialog.border") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcBorderDensityLabel = new GridBagConstraints();
        gbcBorderDensityLabel.anchor = GridBagConstraints.EAST;
        gbcBorderDensityLabel.insets = new Insets(0, 0, 5, 5);
        gbcBorderDensityLabel.gridx = 3;
        gbcBorderDensityLabel.gridy = 6;
        this.add(borderDensityLabel, gbcBorderDensityLabel);
        borderDensityComboBox = new JComboBox<>();

        borderDensityComboBox
            .setModel(new DefaultComboBoxModel<>(new String[] { "BLACK", DicomPrintOptions.DEF_BORDER_DENSITY })); //$NON-NLS-1$
        GridBagConstraints gbcBorderDensityComboBox = new GridBagConstraints();
        gbcBorderDensityComboBox.anchor = GridBagConstraints.WEST;
        gbcBorderDensityComboBox.insets = new Insets(0, 0, 5, 0);
        gbcBorderDensityComboBox.gridx = 4;
        gbcBorderDensityComboBox.gridy = 6;
        this.add(borderDensityComboBox, gbcBorderDensityComboBox);

        // minDensityLabel = new JLabel();
        // minDensityLabel.setText(Messages.getString("DicomPrintDialog.min_density") + StringUtil.COLON); //$NON-NLS-1$
        // GridBagConstraints gbc_minDensityLabel = new GridBagConstraints();
        // gbc_minDensityLabel.anchor = GridBagConstraints.EAST;
        // gbc_minDensityLabel.insets = new Insets(0, 0, 5, 5);
        // gbc_minDensityLabel.gridx = 0;
        // gbc_minDensityLabel.gridy = 7;
        // this.add(minDensityLabel, gbc_minDensityLabel);
        // minDensitySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 255, 1));
        // // Not used for 8 bits images
        // minDensitySpinner.setEnabled(false);
        // GridBagConstraints gbc_minDensitySpinner = new GridBagConstraints();
        // gbc_minDensitySpinner.anchor = GridBagConstraints.WEST;
        // gbc_minDensitySpinner.insets = new Insets(0, 0, 5, 5);
        // gbc_minDensitySpinner.gridx = 1;
        // gbc_minDensitySpinner.gridy = 7;
        // this.add(minDensitySpinner, gbc_minDensitySpinner);

        // maxDensityLabel = new JLabel();
        // maxDensityLabel.setText(Messages.getString("DicomPrintDialog.max_density") + StringUtil.COLON); //$NON-NLS-1$
        // GridBagConstraints gbc_maxDensityLabel = new GridBagConstraints();
        // gbc_maxDensityLabel.anchor = GridBagConstraints.EAST;
        // gbc_maxDensityLabel.insets = new Insets(0, 0, 5, 5);
        // gbc_maxDensityLabel.gridx = 3;
        // gbc_maxDensityLabel.gridy = 7;
        // this.add(maxDensityLabel, gbc_maxDensityLabel);
        // maxDensitySpinner = new JSpinner(new SpinnerNumberModel(255, 0, 255, 1));
        // // Not used for 8 bits images
        // maxDensitySpinner.setEnabled(false);
        // GridBagConstraints gbc_maxDensitySpinner = new GridBagConstraints();
        // gbc_maxDensitySpinner.anchor = GridBagConstraints.WEST;
        // gbc_maxDensitySpinner.insets = new Insets(0, 0, 5, 0);
        // gbc_maxDensitySpinner.gridx = 4;
        // gbc_maxDensitySpinner.gridy = 7;
        // this.add(maxDensitySpinner, gbc_maxDensitySpinner);

        trimLabel = new JLabel();
        trimLabel.setText(Messages.getString("DicomPrintDialog.trim") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcTrimLabel = new GridBagConstraints();
        gbcTrimLabel.anchor = GridBagConstraints.EAST;
        gbcTrimLabel.insets = new Insets(0, 0, 5, 5);
        gbcTrimLabel.gridx = 0;
        gbcTrimLabel.gridy = 7;
        this.add(trimLabel, gbcTrimLabel);

        trimComboBox = new JComboBox<>();
        trimComboBox.setModel(new DefaultComboBoxModel<>(new String[] { DicomPrintOptions.DEF_TRIM, "YES" })); //$NON-NLS-1$
        GridBagConstraints gbcTrimComboBox = new GridBagConstraints();
        gbcTrimComboBox.anchor = GridBagConstraints.SOUTHWEST;
        gbcTrimComboBox.insets = new Insets(0, 0, 5, 5);
        gbcTrimComboBox.gridx = 1;
        gbcTrimComboBox.gridy = 7;
        this.add(trimComboBox, gbcTrimComboBox);

        labelEmpty = new JLabel();
        labelEmpty.setText(Messages.getString("DicomPrintDialog.empty_density") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcLabel1 = new GridBagConstraints();
        gbcLabel1.anchor = GridBagConstraints.EAST;
        gbcLabel1.insets = new Insets(0, 0, 5, 5);
        gbcLabel1.gridx = 3;
        gbcLabel1.gridy = 7;
        this.add(labelEmpty, gbcLabel1);

        comboBoxEmpty = new JComboBox<>();
        comboBoxEmpty
            .setModel(new DefaultComboBoxModel<>(new String[] { DicomPrintOptions.DEF_EMPTY_DENSITY, "WHITE" })); //$NON-NLS-1$
        GridBagConstraints gbcComboBox = new GridBagConstraints();
        gbcComboBox.anchor = GridBagConstraints.NORTHWEST;
        gbcComboBox.insets = new Insets(0, 0, 5, 0);
        gbcComboBox.gridx = 4;
        gbcComboBox.gridy = 7;
        this.add(comboBoxEmpty, gbcComboBox);

        printAnnotationsCheckBox = new JCheckBox();
        printAnnotationsCheckBox.setText(Messages.getString("PrintDialog.annotate")); //$NON-NLS-1$
        printAnnotationsCheckBox.setSelected(true);
        GridBagConstraints gbcPrintAnnotationsCheckBox = new GridBagConstraints();
        gbcPrintAnnotationsCheckBox.anchor = GridBagConstraints.NORTHWEST;
        gbcPrintAnnotationsCheckBox.insets = new Insets(0, 0, 5, 5);
        gbcPrintAnnotationsCheckBox.gridwidth = 2;
        gbcPrintAnnotationsCheckBox.gridx = 0;
        gbcPrintAnnotationsCheckBox.gridy = 9;
        this.add(printAnnotationsCheckBox, gbcPrintAnnotationsCheckBox);

        chckbxSelctedView = new JCheckBox(Messages.getString("PrintDialog.selected_view")); //$NON-NLS-1$
        GridBagConstraints gbcChckbxSelctedView = new GridBagConstraints();
        gbcChckbxSelctedView.insets = new Insets(0, 0, 0, 5);
        gbcChckbxSelctedView.anchor = GridBagConstraints.NORTHWEST;
        gbcChckbxSelctedView.gridwidth = 2;
        gbcChckbxSelctedView.gridx = 0;
        gbcChckbxSelctedView.gridy = 10;
        this.add(chckbxSelctedView, gbcChckbxSelctedView);

        label = new JLabel();
        label.setText(Messages.getString("DicomPrintDialog.dpi") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcLabel = new GridBagConstraints();
        gbcLabel.anchor = GridBagConstraints.EAST;
        gbcLabel.insets = new Insets(0, 0, 0, 5);
        gbcLabel.gridx = 3;
        gbcLabel.gridy = 10;
        this.add(label, gbcLabel);

        comboBoxDPI = new JComboBox<>();
        GridBagConstraints gbcComboBoxDPI = new GridBagConstraints();
        gbcComboBoxDPI.anchor = GridBagConstraints.NORTHWEST;
        gbcComboBoxDPI.gridx = 4;
        gbcComboBoxDPI.gridy = 10;
        comboBoxDPI.setModel(new DefaultComboBoxModel<>(PrintOptions.DotPerInches.values()));
        this.add(comboBoxDPI, gbcComboBoxDPI);
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
            chckbxSelctedView.setSelected(options.isPrintOnlySelectedView());
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
        options.setPrintOnlySelectedView(chckbxSelctedView.isSelected());
        options.setDpi((PrintOptions.DotPerInches) comboBoxDPI.getSelectedItem());
    }
}
