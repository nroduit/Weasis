/*******************************************************************************
 * Copyright (c) 2011 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Marcelo Porto - initial API and implementation, Animati Sistemas de Informática Ltda. (http://www.animati.com.br)
 *
 ******************************************************************************/

package org.weasis.dicom.explorer.print;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.util.ExportLayout;
import org.weasis.core.ui.util.PrintOptions;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.pref.node.DicomNodeDialog;
import org.weasis.dicom.explorer.pref.node.DicomNodeEx;

/**
 *
 * @author Marcelo Porto (marcelo@animati.com.br), Nicolas Roduit
 */
public class DicomPrintDialog<I extends ImageElement> extends JDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomPrintDialog.class);

    enum DotPerInches {
        DPI_150(150), DPI_200(200), DPI_300(300);

        private final int dpi;

        private DotPerInches(int dpi) {
            this.dpi = dpi;
        }

        public int getDpi() {
            return dpi;
        }

        @Override
        public String toString() {
            return String.valueOf(dpi);
        }
    }

    enum FilmSize {
        IN8X10("8INX10IN", 8, 10), IN8_5X11("8_5INX11IN", 8.5, 11), IN10X12("10INX12IN", 10, 12), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        IN10X14("10INX14IN", //$NON-NLS-1$
                        10, 14),
        IN11X14("11INX14IN", 11, 14), IN11X17("11INX17IN", 11, 17), IN14X14("14INX14IN", 14, 14), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        IN14X17("14INX17IN", //$NON-NLS-1$
                        14, 17),
        CM24X24("24CMX24CM", convertMM2Inch(240), convertMM2Inch(240)), //$NON-NLS-1$
        CM24X30("24CMX30CM", convertMM2Inch(240), //$NON-NLS-1$
                        convertMM2Inch(300)),
        A4("A4", convertMM2Inch(210), convertMM2Inch(297)), //$NON-NLS-1$
        A3("A3", convertMM2Inch(297), convertMM2Inch(420)); //$NON-NLS-1$

        private final String name;
        private final double width;
        private final double height;

        private FilmSize(String name, double width, double height) {
            this.name = name;
            this.width = width;
            this.height = height;
        }

        @Override
        public String toString() {
            return name;
        }

        public int getWidth(DotPerInches dpi) {
            return getLengthFromInch(width, dpi);
        }

        public int getHeight(DotPerInches dpi) {
            return getLengthFromInch(height, dpi);
        }

        public static int getLengthFromInch(double size, DotPerInches dpi) {
            DotPerInches dpi2 = dpi == null ? DotPerInches.DPI_200 : dpi;
            double val = size * dpi2.getDpi();
            return (int) (val + 0.5);
        }

        public static double convertMM2Inch(int size) {
            return size / 25.4;
        }

    }

    private JButton addPrinterButton;
    private JComboBox<String> borderDensityComboBox;
    private JLabel borderDensityLabel;
    private JButton cancelButton;
    private JCheckBox colorPrintCheckBox;
    private JButton deleteButton;
    private JButton editButton;
    private JComboBox<String> filmDestinationComboBox;
    private JLabel filmDestinationLabel;
    private JComboBox<String> filmOrientationComboBox;
    private JLabel filmOrientationLabel;
    private JComboBox<FilmSize> filmSizeIdComboBox;
    private JLabel filmSizeIdLabel;
    private JComboBox<String> imageDisplayFormatComboBox;
    private JLabel imageDisplayLabel;
    private JComboBox<String> magnificationTypeComboBox;
    private JLabel magnificationTypeLabel;
    // private JLabel maxDensityLabel;
    // private JSpinner maxDensitySpinner;
    private JComboBox<String> mediumTypeComboBox;
    private JLabel mediumTypeLabel;
    // private JLabel minDensityLabel;
    // private JSpinner minDensitySpinner;
    private JLabel numOfCopiesLabel;
    private JSpinner numOfCopiesSpinner;
    private JCheckBox printAnnotationsCheckBox;
    private JButton printButton;
    private JSeparator printOptionsSeparator;
    private JLabel printerLabel;
    private JComboBox<DicomNodeEx> printersComboBox;
    private JComboBox<String> priorityComboBox;
    private JLabel priorityLabel;
    private JComboBox<String> smoothingTypeComboBox;
    private JLabel smoothingTypeLabel;
    private JComboBox<String> trimComboBox;
    private JLabel trimLabel;
    private DefaultComboBoxModel<String> portraitDisplayFormatsModel;
    private JCheckBox chckbxSelctedView;
    private ImageViewerEventManager<I> eventManager;
    private Component horizontalStrut;
    private JPanel footPanel;
    private JLabel label;
    private JComboBox<DotPerInches> comboBoxDPI;
    private JLabel labelEmpty;
    private JComboBox<String> comboBoxEmpty;

    /** Creates new form DicomPrintDialog */
    public DicomPrintDialog(Window parent, String title, ImageViewerEventManager<I> eventManager) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        portraitDisplayFormatsModel = new DefaultComboBoxModel<>(new String[] { "STANDARD\\1,1" }); //$NON-NLS-1$

        this.eventManager = eventManager;
        initComponents();
        enableOrDisableColorPrint();
        pack();
    }

    private void initComponents() {
        final JPanel rootPane = new JPanel();
        rootPane.setBorder(new EmptyBorder(10, 15, 10, 15));
        this.setContentPane(rootPane);

        final JPanel printersCfg = new JPanel();
        printersCfg.setBorder(new TitledBorder(null, Messages.getString("DicomPrintDialog.print_title"), //$NON-NLS-1$
            TitledBorder.LEADING, TitledBorder.TOP, null, null));
        FlowLayout flPrintersCfg = new FlowLayout();
        flPrintersCfg.setAlignment(FlowLayout.LEFT);
        printersCfg.setLayout(flPrintersCfg);

        final JPanel content = new JPanel();
        content.setBorder(new TitledBorder(null, Messages.getString("DicomPrintDialog.option_title"), //$NON-NLS-1$
            TitledBorder.LEADING, TitledBorder.TOP, null, null));
        GridBagLayout gblContent = new GridBagLayout();
        gblContent.columnWidths = new int[] { 46, 27, 8, 17, 34, 0 };
        gblContent.rowHeights = new int[] { 25, 26, 0, 2, 25, 25, 25, 26, 0, 18, 18, 0 };
        gblContent.columnWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 1.0, Double.MIN_VALUE };
        gblContent.rowWeights =
            new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
        content.setLayout(gblContent);
        mediumTypeLabel = new JLabel();

        mediumTypeLabel.setText(Messages.getString("DicomPrintDialog.med_type") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcMediumTypeLabel = new GridBagConstraints();
        gbcMediumTypeLabel.anchor = GridBagConstraints.EAST;
        gbcMediumTypeLabel.insets = new Insets(0, 0, 5, 5);
        gbcMediumTypeLabel.gridx = 0;
        gbcMediumTypeLabel.gridy = 0;
        content.add(mediumTypeLabel, gbcMediumTypeLabel);
        mediumTypeComboBox = new JComboBox<>();

        mediumTypeComboBox.setModel(new DefaultComboBoxModel<>(
            new String[] { "BLUE FILM", "CLEAR FILM", "MAMMO CLEAR FILM", "MAMMO BLUE FILM", "PAPER" })); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        GridBagConstraints gbcMediumTypeComboBox = new GridBagConstraints();
        gbcMediumTypeComboBox.anchor = GridBagConstraints.WEST;
        gbcMediumTypeComboBox.insets = new Insets(0, 0, 5, 5);
        gbcMediumTypeComboBox.gridx = 1;
        gbcMediumTypeComboBox.gridy = 0;
        content.add(mediumTypeComboBox, gbcMediumTypeComboBox);
        priorityLabel = new JLabel();

        priorityLabel.setText(Messages.getString("DicomPrintDialog.priority") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcPriorityLabel = new GridBagConstraints();
        gbcPriorityLabel.anchor = GridBagConstraints.EAST;
        gbcPriorityLabel.insets = new Insets(0, 0, 5, 5);
        gbcPriorityLabel.gridx = 3;
        gbcPriorityLabel.gridy = 0;
        content.add(priorityLabel, gbcPriorityLabel);
        priorityComboBox = new JComboBox<>();

        priorityComboBox.setModel(new DefaultComboBoxModel<>(new String[] { "LOW", "MED", "HIGH" })); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        GridBagConstraints gbcPriorityComboBox = new GridBagConstraints();
        gbcPriorityComboBox.anchor = GridBagConstraints.WEST;
        gbcPriorityComboBox.insets = new Insets(0, 0, 5, 0);
        gbcPriorityComboBox.gridx = 4;
        gbcPriorityComboBox.gridy = 0;
        content.add(priorityComboBox, gbcPriorityComboBox);
        filmDestinationLabel = new JLabel();

        filmDestinationLabel.setText(Messages.getString("DicomPrintDialog.film_dest") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcFilmDestinationLabel = new GridBagConstraints();
        gbcFilmDestinationLabel.anchor = GridBagConstraints.EAST;
        gbcFilmDestinationLabel.insets = new Insets(0, 0, 5, 5);
        gbcFilmDestinationLabel.gridx = 0;
        gbcFilmDestinationLabel.gridy = 1;
        content.add(filmDestinationLabel, gbcFilmDestinationLabel);
        filmDestinationComboBox = new JComboBox<>();

        filmDestinationComboBox.setModel(new DefaultComboBoxModel<>(new String[] { "MAGAZINE", "PROCESSOR" })); //$NON-NLS-1$ //$NON-NLS-2$
        GridBagConstraints gbcFilmDestinationComboBox = new GridBagConstraints();
        gbcFilmDestinationComboBox.fill = GridBagConstraints.VERTICAL;
        gbcFilmDestinationComboBox.anchor = GridBagConstraints.WEST;
        gbcFilmDestinationComboBox.insets = new Insets(0, 0, 5, 5);
        gbcFilmDestinationComboBox.gridx = 1;
        gbcFilmDestinationComboBox.gridy = 1;
        content.add(filmDestinationComboBox, gbcFilmDestinationComboBox);
        numOfCopiesLabel = new JLabel();

        numOfCopiesLabel.setText(Messages.getString("DicomPrintDialog.copies") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcNumOfCopiesLabel = new GridBagConstraints();
        gbcNumOfCopiesLabel.anchor = GridBagConstraints.EAST;
        gbcNumOfCopiesLabel.insets = new Insets(0, 0, 5, 5);
        gbcNumOfCopiesLabel.gridx = 3;
        gbcNumOfCopiesLabel.gridy = 1;
        content.add(numOfCopiesLabel, gbcNumOfCopiesLabel);
        filmOrientationComboBox = new JComboBox<>();

        filmOrientationComboBox.setModel(new DefaultComboBoxModel<>(new String[] { "PORTRAIT", "LANDSCAPE" })); //$NON-NLS-1$ //$NON-NLS-2$

        numOfCopiesSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        GridBagConstraints gbcNumOfCopiesSpinner = new GridBagConstraints();
        gbcNumOfCopiesSpinner.anchor = GridBagConstraints.WEST;
        gbcNumOfCopiesSpinner.insets = new Insets(0, 0, 5, 0);
        gbcNumOfCopiesSpinner.gridx = 4;
        gbcNumOfCopiesSpinner.gridy = 1;
        content.add(numOfCopiesSpinner, gbcNumOfCopiesSpinner);
        colorPrintCheckBox = new JCheckBox();

        colorPrintCheckBox.setText(Messages.getString("DicomPrintDialog.print_color")); //$NON-NLS-1$
        GridBagConstraints gbcColorPrintCheckBox = new GridBagConstraints();
        gbcColorPrintCheckBox.anchor = GridBagConstraints.WEST;
        gbcColorPrintCheckBox.insets = new Insets(0, 0, 5, 5);
        gbcColorPrintCheckBox.gridx = 1;
        gbcColorPrintCheckBox.gridy = 2;
        content.add(colorPrintCheckBox, gbcColorPrintCheckBox);
        printOptionsSeparator = new JSeparator();
        GridBagConstraints gbcPrintOptionsSeparator = new GridBagConstraints();
        gbcPrintOptionsSeparator.anchor = GridBagConstraints.NORTH;
        gbcPrintOptionsSeparator.fill = GridBagConstraints.HORIZONTAL;
        gbcPrintOptionsSeparator.insets = new Insets(0, 0, 5, 0);
        gbcPrintOptionsSeparator.gridwidth = 5;
        gbcPrintOptionsSeparator.gridx = 0;
        gbcPrintOptionsSeparator.gridy = 3;
        content.add(printOptionsSeparator, gbcPrintOptionsSeparator);
        imageDisplayLabel = new JLabel();

        imageDisplayLabel.setText(Messages.getString("DicomPrintDialog.disp_format") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcImageDisplayLabel = new GridBagConstraints();
        gbcImageDisplayLabel.anchor = GridBagConstraints.EAST;
        gbcImageDisplayLabel.insets = new Insets(0, 0, 5, 5);
        gbcImageDisplayLabel.gridx = 0;
        gbcImageDisplayLabel.gridy = 5;
        content.add(imageDisplayLabel, gbcImageDisplayLabel);
        imageDisplayFormatComboBox = new JComboBox<>();

        imageDisplayFormatComboBox.setMaximumRowCount(10);
        imageDisplayFormatComboBox.setModel(portraitDisplayFormatsModel);
        GridBagConstraints gbcImageDisplayFormatComboBox = new GridBagConstraints();
        gbcImageDisplayFormatComboBox.anchor = GridBagConstraints.WEST;
        gbcImageDisplayFormatComboBox.insets = new Insets(0, 0, 5, 5);
        gbcImageDisplayFormatComboBox.gridx = 1;
        gbcImageDisplayFormatComboBox.gridy = 5;
        content.add(imageDisplayFormatComboBox, gbcImageDisplayFormatComboBox);
        filmSizeIdLabel = new JLabel();

        filmSizeIdLabel.setText(Messages.getString("DicomPrintDialog.size_id") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcFilmSizeIdLabel = new GridBagConstraints();
        gbcFilmSizeIdLabel.anchor = GridBagConstraints.EAST;
        gbcFilmSizeIdLabel.insets = new Insets(0, 0, 5, 5);
        gbcFilmSizeIdLabel.gridx = 3;
        gbcFilmSizeIdLabel.gridy = 4;
        content.add(filmSizeIdLabel, gbcFilmSizeIdLabel);
        filmSizeIdComboBox = new JComboBox<>();

        filmSizeIdComboBox.setModel(new DefaultComboBoxModel<>(FilmSize.values()));
        GridBagConstraints gbcFilmSizeIdComboBox = new GridBagConstraints();
        gbcFilmSizeIdComboBox.anchor = GridBagConstraints.WEST;
        gbcFilmSizeIdComboBox.insets = new Insets(0, 0, 5, 0);
        gbcFilmSizeIdComboBox.gridx = 4;
        gbcFilmSizeIdComboBox.gridy = 4;
        content.add(filmSizeIdComboBox, gbcFilmSizeIdComboBox);
        filmOrientationLabel = new JLabel();

        filmOrientationLabel.setText(Messages.getString("DicomPrintDialog.film_orientation") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcFilmOrientationLabel = new GridBagConstraints();
        gbcFilmOrientationLabel.anchor = GridBagConstraints.EAST;
        gbcFilmOrientationLabel.insets = new Insets(0, 0, 5, 5);
        gbcFilmOrientationLabel.gridx = 0;
        gbcFilmOrientationLabel.gridy = 4;
        content.add(filmOrientationLabel, gbcFilmOrientationLabel);
        GridBagConstraints gbcFilmOrientationComboBox = new GridBagConstraints();
        gbcFilmOrientationComboBox.anchor = GridBagConstraints.WEST;
        gbcFilmOrientationComboBox.insets = new Insets(0, 0, 5, 5);
        gbcFilmOrientationComboBox.gridx = 1;
        gbcFilmOrientationComboBox.gridy = 4;
        content.add(filmOrientationComboBox, gbcFilmOrientationComboBox);
        magnificationTypeLabel = new JLabel();

        magnificationTypeLabel.setText(Messages.getString("DicomPrintDialog.magn_type") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcMagnificationTypeLabel = new GridBagConstraints();
        gbcMagnificationTypeLabel.anchor = GridBagConstraints.EAST;
        gbcMagnificationTypeLabel.insets = new Insets(0, 0, 5, 5);
        gbcMagnificationTypeLabel.gridx = 3;
        gbcMagnificationTypeLabel.gridy = 5;
        content.add(magnificationTypeLabel, gbcMagnificationTypeLabel);
        magnificationTypeComboBox = new JComboBox<>();

        magnificationTypeComboBox
            .setModel(new DefaultComboBoxModel<>(new String[] { "REPLICATE", "BILINEAR", "CUBIC" })); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        magnificationTypeComboBox.setSelectedIndex(2);
        GridBagConstraints gbcMagnificationTypeComboBox = new GridBagConstraints();
        gbcMagnificationTypeComboBox.anchor = GridBagConstraints.WEST;
        gbcMagnificationTypeComboBox.insets = new Insets(0, 0, 5, 0);
        gbcMagnificationTypeComboBox.gridx = 4;
        gbcMagnificationTypeComboBox.gridy = 5;
        content.add(magnificationTypeComboBox, gbcMagnificationTypeComboBox);
        smoothingTypeLabel = new JLabel();

        smoothingTypeLabel.setText(Messages.getString("DicomPrintDialog.smooth") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcSmoothingTypeLabel = new GridBagConstraints();
        gbcSmoothingTypeLabel.anchor = GridBagConstraints.EAST;
        gbcSmoothingTypeLabel.insets = new Insets(0, 0, 5, 5);
        gbcSmoothingTypeLabel.gridx = 0;
        gbcSmoothingTypeLabel.gridy = 6;
        content.add(smoothingTypeLabel, gbcSmoothingTypeLabel);
        smoothingTypeComboBox = new JComboBox<>();

        smoothingTypeComboBox.setModel(new DefaultComboBoxModel<>(new String[] { "MEDIUM", "SHARP", "SMOOTH" })); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        GridBagConstraints gbcSmoothingTypeComboBox = new GridBagConstraints();
        gbcSmoothingTypeComboBox.fill = GridBagConstraints.HORIZONTAL;
        gbcSmoothingTypeComboBox.insets = new Insets(0, 0, 5, 5);
        gbcSmoothingTypeComboBox.gridx = 1;
        gbcSmoothingTypeComboBox.gridy = 6;
        content.add(smoothingTypeComboBox, gbcSmoothingTypeComboBox);
        borderDensityLabel = new JLabel();

        borderDensityLabel.setText(Messages.getString("DicomPrintDialog.border") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcBorderDensityLabel = new GridBagConstraints();
        gbcBorderDensityLabel.anchor = GridBagConstraints.EAST;
        gbcBorderDensityLabel.insets = new Insets(0, 0, 5, 5);
        gbcBorderDensityLabel.gridx = 3;
        gbcBorderDensityLabel.gridy = 6;
        content.add(borderDensityLabel, gbcBorderDensityLabel);
        borderDensityComboBox = new JComboBox<>();

        borderDensityComboBox.setModel(new DefaultComboBoxModel<>(new String[] { "BLACK", "WHITE" })); //$NON-NLS-1$ //$NON-NLS-2$
        borderDensityComboBox.setSelectedIndex(1);
        GridBagConstraints gbcBorderDensityComboBox = new GridBagConstraints();
        gbcBorderDensityComboBox.anchor = GridBagConstraints.WEST;
        gbcBorderDensityComboBox.insets = new Insets(0, 0, 5, 0);
        gbcBorderDensityComboBox.gridx = 4;
        gbcBorderDensityComboBox.gridy = 6;
        content.add(borderDensityComboBox, gbcBorderDensityComboBox);

        // minDensityLabel = new JLabel();
        // minDensityLabel.setText(Messages.getString("DicomPrintDialog.min_density") + StringUtil.COLON); //$NON-NLS-1$
        // GridBagConstraints gbc_minDensityLabel = new GridBagConstraints();
        // gbc_minDensityLabel.anchor = GridBagConstraints.EAST;
        // gbc_minDensityLabel.insets = new Insets(0, 0, 5, 5);
        // gbc_minDensityLabel.gridx = 0;
        // gbc_minDensityLabel.gridy = 7;
        // content.add(minDensityLabel, gbc_minDensityLabel);
        // minDensitySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 255, 1));
        // // Not used for 8 bits images
        // minDensitySpinner.setEnabled(false);
        // GridBagConstraints gbc_minDensitySpinner = new GridBagConstraints();
        // gbc_minDensitySpinner.anchor = GridBagConstraints.WEST;
        // gbc_minDensitySpinner.insets = new Insets(0, 0, 5, 5);
        // gbc_minDensitySpinner.gridx = 1;
        // gbc_minDensitySpinner.gridy = 7;
        // content.add(minDensitySpinner, gbc_minDensitySpinner);
        // maxDensityLabel = new JLabel();
        //
        // maxDensityLabel.setText(Messages.getString("DicomPrintDialog.max_density") + StringUtil.COLON); //$NON-NLS-1$
        // GridBagConstraints gbc_maxDensityLabel = new GridBagConstraints();
        // gbc_maxDensityLabel.anchor = GridBagConstraints.EAST;
        // gbc_maxDensityLabel.insets = new Insets(0, 0, 5, 5);
        // gbc_maxDensityLabel.gridx = 3;
        // gbc_maxDensityLabel.gridy = 7;
        // content.add(maxDensityLabel, gbc_maxDensityLabel);
        // maxDensitySpinner = new JSpinner(new SpinnerNumberModel(255, 0, 255, 1));
        // // Not used for 8 bits images
        // maxDensitySpinner.setEnabled(false);
        // GridBagConstraints gbc_maxDensitySpinner = new GridBagConstraints();
        // gbc_maxDensitySpinner.anchor = GridBagConstraints.WEST;
        // gbc_maxDensitySpinner.insets = new Insets(0, 0, 5, 0);
        // gbc_maxDensitySpinner.gridx = 4;
        // gbc_maxDensitySpinner.gridy = 7;
        // content.add(maxDensitySpinner, gbc_maxDensitySpinner);

        trimLabel = new JLabel();
        trimLabel.setText(Messages.getString("DicomPrintDialog.trim") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcTrimLabel = new GridBagConstraints();
        gbcTrimLabel.anchor = GridBagConstraints.EAST;
        gbcTrimLabel.insets = new Insets(0, 0, 5, 5);
        gbcTrimLabel.gridx = 0;
        gbcTrimLabel.gridy = 7;
        content.add(trimLabel, gbcTrimLabel);
        trimComboBox = new JComboBox<>();

        trimComboBox.setModel(new DefaultComboBoxModel<>(new String[] { "NO", "YES" })); //$NON-NLS-1$ //$NON-NLS-2$
        GridBagConstraints gbcTrimComboBox = new GridBagConstraints();
        gbcTrimComboBox.anchor = GridBagConstraints.SOUTHWEST;
        gbcTrimComboBox.insets = new Insets(0, 0, 5, 5);
        gbcTrimComboBox.gridx = 1;
        gbcTrimComboBox.gridy = 7;
        content.add(trimComboBox, gbcTrimComboBox);

        labelEmpty = new JLabel();
        labelEmpty.setText(Messages.getString("DicomPrintDialog.empty_density") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcLabel1 = new GridBagConstraints();
        gbcLabel1.anchor = GridBagConstraints.EAST;
        gbcLabel1.insets = new Insets(0, 0, 5, 5);
        gbcLabel1.gridx = 3;
        gbcLabel1.gridy = 7;
        content.add(labelEmpty, gbcLabel1);

        comboBoxEmpty = new JComboBox<>();
        comboBoxEmpty.setModel(new DefaultComboBoxModel<>(new String[] { "BLACK", "WHITE" })); //$NON-NLS-1$ //$NON-NLS-2$
        GridBagConstraints gbcComboBox = new GridBagConstraints();
        gbcComboBox.anchor = GridBagConstraints.NORTHWEST;
        gbcComboBox.insets = new Insets(0, 0, 5, 0);
        gbcComboBox.gridx = 4;
        gbcComboBox.gridy = 7;
        content.add(comboBoxEmpty, gbcComboBox);
        printAnnotationsCheckBox = new JCheckBox();

        printAnnotationsCheckBox.setText(Messages.getString("PrintDialog.annotate")); //$NON-NLS-1$
        printAnnotationsCheckBox.setSelected(true);
        GridBagConstraints gbcPrintAnnotationsCheckBox = new GridBagConstraints();
        gbcPrintAnnotationsCheckBox.anchor = GridBagConstraints.NORTHWEST;
        gbcPrintAnnotationsCheckBox.insets = new Insets(0, 0, 5, 5);
        gbcPrintAnnotationsCheckBox.gridwidth = 2;
        gbcPrintAnnotationsCheckBox.gridx = 0;
        gbcPrintAnnotationsCheckBox.gridy = 9;
        content.add(printAnnotationsCheckBox, gbcPrintAnnotationsCheckBox);

        chckbxSelctedView = new JCheckBox(Messages.getString("PrintDialog.selected_view")); //$NON-NLS-1$
        GridBagConstraints gbcChckbxSelctedView = new GridBagConstraints();
        gbcChckbxSelctedView.insets = new Insets(0, 0, 0, 5);
        gbcChckbxSelctedView.anchor = GridBagConstraints.NORTHWEST;
        gbcChckbxSelctedView.gridwidth = 2;
        gbcChckbxSelctedView.gridx = 0;
        gbcChckbxSelctedView.gridy = 10;
        content.add(chckbxSelctedView, gbcChckbxSelctedView);
        rootPane.setLayout(new BorderLayout(0, 0));
        this.getContentPane().add(printersCfg, BorderLayout.NORTH);

        printerLabel = new JLabel();
        printersCfg.add(printerLabel);

        printerLabel.setText(Messages.getString("DicomPrintDialog.printer") + StringUtil.COLON); //$NON-NLS-1$
        printersComboBox = new JComboBox<>();
        printersCfg.add(printersComboBox);

        printersComboBox.setModel(new DefaultComboBoxModel<DicomNodeEx>());

        DicomNodeEx.loadDicomNodes(printersComboBox, DicomNodeEx.Type.PRINTER);

        horizontalStrut = Box.createHorizontalStrut(20);
        printersCfg.add(horizontalStrut);
        addPrinterButton = new JButton(Messages.getString("DicomPrintDialog.add")); //$NON-NLS-1$
        printersCfg.add(addPrinterButton);

        editButton = new JButton();
        printersCfg.add(editButton);

        editButton.setText(Messages.getString("DicomPrintDialog.edit")); //$NON-NLS-1$
        deleteButton = new JButton();
        printersCfg.add(deleteButton);

        deleteButton.setText(Messages.getString("DicomPrintDialog.delete")); //$NON-NLS-1$
        deleteButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteButtonActionPerformed();
            }
        });
        editButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editButtonActionPerformed();
            }
        });
        addPrinterButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addPrinterButtonActionPerformed();
            }
        });
        printersComboBox.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableOrDisableColorPrint();
            }
        });
        this.getContentPane().add(content, BorderLayout.CENTER);

        label = new JLabel();
        label.setText(Messages.getString("DicomPrintDialog.dpi") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcLabel = new GridBagConstraints();
        gbcLabel.anchor = GridBagConstraints.EAST;
        gbcLabel.insets = new Insets(0, 0, 0, 5);
        gbcLabel.gridx = 3;
        gbcLabel.gridy = 10;
        content.add(label, gbcLabel);

        comboBoxDPI = new JComboBox<>();
        GridBagConstraints gbcComboBoxDPI = new GridBagConstraints();
        gbcComboBoxDPI.anchor = GridBagConstraints.NORTHWEST;
        gbcComboBoxDPI.gridx = 4;
        gbcComboBoxDPI.gridy = 10;
        comboBoxDPI.setModel(new DefaultComboBoxModel<>(DotPerInches.values()));
        comboBoxDPI.setSelectedIndex(1);
        content.add(comboBoxDPI, gbcComboBoxDPI);

        footPanel = new JPanel();
        FlowLayout flowLayout = (FlowLayout) footPanel.getLayout();
        flowLayout.setVgap(15);
        flowLayout.setAlignment(FlowLayout.RIGHT);
        flowLayout.setHgap(20);
        getContentPane().add(footPanel, BorderLayout.SOUTH);
        printButton = new JButton();
        footPanel.add(printButton);

        printButton.setText(Messages.getString("DicomPrintDialog.print")); //$NON-NLS-1$
        printButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                printButtonActionPerformed(evt);
            }
        });

        getRootPane().setDefaultButton(printButton);
        cancelButton = new JButton(Messages.getString("DicomPrintDialog.cancel")); //$NON-NLS-1$
        footPanel.add(cancelButton);
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                doClose();
            }
        });
    }

    private void printButtonActionPerformed(java.awt.event.ActionEvent evt) {
        DicomPrintOptions dicomPrintOptions = new DicomPrintOptions();
        dicomPrintOptions.setMediumType((String) mediumTypeComboBox.getSelectedItem());
        dicomPrintOptions.setPriority((String) priorityComboBox.getSelectedItem());
        dicomPrintOptions.setFilmDestination((String) filmDestinationComboBox.getSelectedItem());
        dicomPrintOptions.setNumOfCopies((Integer) numOfCopiesSpinner.getValue());
        dicomPrintOptions.setImageDisplayFormat((String) imageDisplayFormatComboBox.getSelectedItem());
        dicomPrintOptions.setFilmSizeId((FilmSize) filmSizeIdComboBox.getSelectedItem());
        dicomPrintOptions.setDpi((DotPerInches) comboBoxDPI.getSelectedItem());
        dicomPrintOptions.setFilmOrientation((String) filmOrientationComboBox.getSelectedItem());
        dicomPrintOptions.setMagnificationType((String) magnificationTypeComboBox.getSelectedItem());
        dicomPrintOptions.setSmoothingType((String) smoothingTypeComboBox.getSelectedItem());
        dicomPrintOptions.setBorderDensity((String) borderDensityComboBox.getSelectedItem());
        dicomPrintOptions.setEmptyDensity((String) comboBoxEmpty.getSelectedItem());
        // dicomPrintOptions.setMinDensity((Integer) minDensitySpinner.getValue());
        // dicomPrintOptions.setMaxDensity((Integer) maxDensitySpinner.getValue());
        dicomPrintOptions.setMinDensity(0);
        dicomPrintOptions.setMaxDensity(255);
        dicomPrintOptions.setTrim((String) trimComboBox.getSelectedItem());
        dicomPrintOptions.setPrintInColor(colorPrintCheckBox.isSelected());
        dicomPrintOptions.setDicomPrinter((DicomNodeEx) printersComboBox.getSelectedItem());

        DicomPrint dicomPrint = new DicomPrint(dicomPrintOptions);
        PrintOptions printOptions = new PrintOptions(printAnnotationsCheckBox.isSelected(), 1.0);
        printOptions.setColor(dicomPrintOptions.isPrintInColor());

        ImageViewerPlugin<I> container = eventManager.getSelectedView2dContainer();

        List<ViewCanvas<I>> views = container.getImagePanels();
        if (views.isEmpty()) {
            JOptionPane.showMessageDialog(this, Messages.getString("DicomPrintDialog.no_print"), //$NON-NLS-1$
                null, JOptionPane.ERROR_MESSAGE);
            doClose();
            return;
        }

        doClose();

        GridBagLayoutModel layoutModel = container.getLayoutModel();
        if (chckbxSelctedView.isSelected()) {
            final Map<LayoutConstraints, Component> elements = new LinkedHashMap<>(1);
            layoutModel = new GridBagLayoutModel(elements, "sel_tmp", "", null);
            ViewCanvas<I> val = eventManager.getSelectedViewPane();
            elements.put(new LayoutConstraints(val.getClass().getName(), 0, 0, 0, 1, 1, 1.0, 1.0,
                GridBagConstraints.CENTER, GridBagConstraints.BOTH), val.getJComponent());
        }
        ExportLayout<I> layout = new ExportLayout<>(layoutModel);
        try {
            dicomPrint.printImage(dicomPrint.printImage(layout, printOptions));
        } catch (Exception e) {
            LOGGER.error("DICOM Print Service", e); //$NON-NLS-1$
            JOptionPane.showMessageDialog(this, Messages.getString("DicomPrintDialog.error_print"), // $NON-NLS-1$ //$NON-NLS-1$
                Messages.getString("DicomPrintDialog.error"), JOptionPane.ERROR_MESSAGE); // $NON-NLS-1$ //$NON-NLS-1$
        } finally {
            layout.dispose();
        }
    }

    private void doClose() {
        dispose();
    }

    private void addPrinterButtonActionPerformed() {
        DicomNodeDialog dialog = new DicomNodeDialog(SwingUtilities.getWindowAncestor(this), "DICOM Node", //$NON-NLS-1$
            null, printersComboBox, DicomNodeEx.Type.PRINTER);
        JMVUtils.showCenterScreen(dialog, this);
        enableOrDisableColorPrint();
    }

    private void editButtonActionPerformed() {
        DicomNodeDialog dialog = new DicomNodeDialog(SwingUtilities.getWindowAncestor(this), "DICOM Node", //$NON-NLS-1$
            (DicomNodeEx) printersComboBox.getSelectedItem(), printersComboBox, DicomNodeEx.Type.PRINTER);
        JMVUtils.showCenterScreen(dialog, this);
        enableOrDisableColorPrint();
    }

    private void deleteButtonActionPerformed() {
        int index = printersComboBox.getSelectedIndex();
        if (index >= 0) {
            int response = JOptionPane.showConfirmDialog(null,
                String.format("Do you really want to delete \"%s\"?", printersComboBox.getSelectedItem()), "DICOM Node", //$NON-NLS-2$
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if (response == 0) {
                printersComboBox.removeItemAt(index);
                DicomNodeEx.saveDicomNodes(printersComboBox, DicomNodeEx.Type.PRINTER);
            }
        }
    }

    private void enableOrDisableColorPrint() {
        DicomNodeEx selectedPrinter = (DicomNodeEx) printersComboBox.getSelectedItem();
        if (selectedPrinter != null) {
            boolean color = selectedPrinter.isColorPrintSupported();
            colorPrintCheckBox.setSelected(color);
            colorPrintCheckBox.setEnabled(color);
        }
    }
}
