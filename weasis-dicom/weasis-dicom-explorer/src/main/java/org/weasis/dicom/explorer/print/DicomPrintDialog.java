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
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.util.ExportLayout;
import org.weasis.core.ui.util.PrintOptions;
import org.weasis.dicom.explorer.Messages;

/**
 *
 * @author Marcelo Porto (marcelo@animati.com.br)
 */
public class DicomPrintDialog extends JDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomPrintDialog.class);

    private static final int DPI = 200;

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
        A4("A4", convertMM2Inch(210), convertMM2Inch(297)), A3("A3", convertMM2Inch(297), convertMM2Inch(420)); //$NON-NLS-1$ //$NON-NLS-2$

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
            return (size / 25.4);
        }

    }

    private JButton addPrinterButton;
    private JComboBox borderDensityComboBox;
    private JLabel borderDensityLabel;
    private JButton cancelButton;
    private JCheckBox colorPrintCheckBox;
    private JButton deleteButton;
    private JButton editButton;
    private JComboBox filmDestinationComboBox;
    private JLabel filmDestinationLabel;
    private JComboBox filmOrientationComboBox;
    private JLabel filmOrientationLabel;
    private JComboBox filmSizeIdComboBox;
    private JLabel filmSizeIdLabel;
    private JComboBox imageDisplayFormatComboBox;
    private JLabel imageDisplayLabel;
    private JComboBox magnificationTypeComboBox;
    private JLabel magnificationTypeLabel;
    // private JLabel maxDensityLabel;
    // private JSpinner maxDensitySpinner;
    private JComboBox mediumTypeComboBox;
    private JLabel mediumTypeLabel;
    // private JLabel minDensityLabel;
    // private JSpinner minDensitySpinner;
    private JLabel numOfCopiesLabel;
    private JSpinner numOfCopiesSpinner;
    private JCheckBox printAnnotationsCheckBox;
    private JButton printButton;
    private JSeparator printOptionsSeparator;
    private JLabel printerLabel;
    private JComboBox printersComboBox;
    private JComboBox priorityComboBox;
    private JLabel priorityLabel;
    private JComboBox smoothingTypeComboBox;
    private JLabel smoothingTypeLabel;
    private JComboBox trimComboBox;
    private JLabel trimLabel;
    private DefaultComboBoxModel portraitDisplayFormatsModel;
    private JCheckBox chckbxSelctedView;
    private ImageViewerEventManager eventManager;
    private Component horizontalStrut;
    private JPanel footPanel;
    private JLabel label;
    private JComboBox comboBoxDPI;
    private JLabel labelEmpty;
    private JComboBox comboBoxEmpty;

    /** Creates new form DicomPrintDialog */
    public DicomPrintDialog(Window parent, String title, ImageViewerEventManager eventManager) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        portraitDisplayFormatsModel = new DefaultComboBoxModel(new String[] { "STANDARD\\1,1" }); //$NON-NLS-1$

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
        FlowLayout fl_printersCfg = new FlowLayout();
        fl_printersCfg.setAlignment(FlowLayout.LEFT);
        printersCfg.setLayout(fl_printersCfg);

        final JPanel content = new JPanel();
        content.setBorder(new TitledBorder(null, Messages.getString("DicomPrintDialog.option_title"), //$NON-NLS-1$
            TitledBorder.LEADING, TitledBorder.TOP, null, null));
        GridBagLayout gbl_content = new GridBagLayout();
        gbl_content.columnWidths = new int[] { 46, 27, 8, 17, 34, 0 };
        gbl_content.rowHeights = new int[] { 25, 26, 0, 2, 25, 25, 25, 26, 0, 18, 18, 0 };
        gbl_content.columnWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 1.0, Double.MIN_VALUE };
        gbl_content.rowWeights =
            new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
        content.setLayout(gbl_content);
        mediumTypeLabel = new JLabel();

        mediumTypeLabel.setText(Messages.getString("DicomPrintDialog.med_type") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbc_mediumTypeLabel = new GridBagConstraints();
        gbc_mediumTypeLabel.anchor = GridBagConstraints.EAST;
        gbc_mediumTypeLabel.insets = new Insets(0, 0, 5, 5);
        gbc_mediumTypeLabel.gridx = 0;
        gbc_mediumTypeLabel.gridy = 0;
        content.add(mediumTypeLabel, gbc_mediumTypeLabel);
        mediumTypeComboBox = new JComboBox();

        mediumTypeComboBox.setModel(new DefaultComboBoxModel(
            new String[] { "BLUE FILM", "CLEAR FILM", "MAMMO CLEAR FILM", "MAMMO BLUE FILM", "PAPER" })); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        GridBagConstraints gbc_mediumTypeComboBox = new GridBagConstraints();
        gbc_mediumTypeComboBox.anchor = GridBagConstraints.WEST;
        gbc_mediumTypeComboBox.insets = new Insets(0, 0, 5, 5);
        gbc_mediumTypeComboBox.gridx = 1;
        gbc_mediumTypeComboBox.gridy = 0;
        content.add(mediumTypeComboBox, gbc_mediumTypeComboBox);
        priorityLabel = new JLabel();

        priorityLabel.setText(Messages.getString("DicomPrintDialog.priority") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbc_priorityLabel = new GridBagConstraints();
        gbc_priorityLabel.anchor = GridBagConstraints.EAST;
        gbc_priorityLabel.insets = new Insets(0, 0, 5, 5);
        gbc_priorityLabel.gridx = 3;
        gbc_priorityLabel.gridy = 0;
        content.add(priorityLabel, gbc_priorityLabel);
        priorityComboBox = new JComboBox();

        priorityComboBox.setModel(new DefaultComboBoxModel(new String[] { "LOW", "MED", "HIGH" })); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        GridBagConstraints gbc_priorityComboBox = new GridBagConstraints();
        gbc_priorityComboBox.anchor = GridBagConstraints.WEST;
        gbc_priorityComboBox.insets = new Insets(0, 0, 5, 0);
        gbc_priorityComboBox.gridx = 4;
        gbc_priorityComboBox.gridy = 0;
        content.add(priorityComboBox, gbc_priorityComboBox);
        filmDestinationLabel = new JLabel();

        filmDestinationLabel.setText(Messages.getString("DicomPrintDialog.film_dest") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbc_filmDestinationLabel = new GridBagConstraints();
        gbc_filmDestinationLabel.anchor = GridBagConstraints.EAST;
        gbc_filmDestinationLabel.insets = new Insets(0, 0, 5, 5);
        gbc_filmDestinationLabel.gridx = 0;
        gbc_filmDestinationLabel.gridy = 1;
        content.add(filmDestinationLabel, gbc_filmDestinationLabel);
        filmDestinationComboBox = new JComboBox();

        filmDestinationComboBox.setModel(new DefaultComboBoxModel(new String[] { "MAGAZINE", "PROCESSOR" })); //$NON-NLS-1$ //$NON-NLS-2$
        GridBagConstraints gbc_filmDestinationComboBox = new GridBagConstraints();
        gbc_filmDestinationComboBox.fill = GridBagConstraints.VERTICAL;
        gbc_filmDestinationComboBox.anchor = GridBagConstraints.WEST;
        gbc_filmDestinationComboBox.insets = new Insets(0, 0, 5, 5);
        gbc_filmDestinationComboBox.gridx = 1;
        gbc_filmDestinationComboBox.gridy = 1;
        content.add(filmDestinationComboBox, gbc_filmDestinationComboBox);
        numOfCopiesLabel = new JLabel();

        numOfCopiesLabel.setText(Messages.getString("DicomPrintDialog.copies") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbc_numOfCopiesLabel = new GridBagConstraints();
        gbc_numOfCopiesLabel.anchor = GridBagConstraints.EAST;
        gbc_numOfCopiesLabel.insets = new Insets(0, 0, 5, 5);
        gbc_numOfCopiesLabel.gridx = 3;
        gbc_numOfCopiesLabel.gridy = 1;
        content.add(numOfCopiesLabel, gbc_numOfCopiesLabel);
        filmOrientationComboBox = new JComboBox();

        filmOrientationComboBox.setModel(new DefaultComboBoxModel(new String[] { "PORTRAIT", "LANDSCAPE" })); //$NON-NLS-1$ //$NON-NLS-2$

        numOfCopiesSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        GridBagConstraints gbc_numOfCopiesSpinner = new GridBagConstraints();
        gbc_numOfCopiesSpinner.anchor = GridBagConstraints.WEST;
        gbc_numOfCopiesSpinner.insets = new Insets(0, 0, 5, 0);
        gbc_numOfCopiesSpinner.gridx = 4;
        gbc_numOfCopiesSpinner.gridy = 1;
        content.add(numOfCopiesSpinner, gbc_numOfCopiesSpinner);
        colorPrintCheckBox = new JCheckBox();

        colorPrintCheckBox.setText(Messages.getString("DicomPrintDialog.print_color")); //$NON-NLS-1$
        GridBagConstraints gbc_colorPrintCheckBox = new GridBagConstraints();
        gbc_colorPrintCheckBox.anchor = GridBagConstraints.WEST;
        gbc_colorPrintCheckBox.insets = new Insets(0, 0, 5, 5);
        gbc_colorPrintCheckBox.gridx = 1;
        gbc_colorPrintCheckBox.gridy = 2;
        content.add(colorPrintCheckBox, gbc_colorPrintCheckBox);
        printOptionsSeparator = new JSeparator();
        GridBagConstraints gbc_printOptionsSeparator = new GridBagConstraints();
        gbc_printOptionsSeparator.anchor = GridBagConstraints.NORTH;
        gbc_printOptionsSeparator.fill = GridBagConstraints.HORIZONTAL;
        gbc_printOptionsSeparator.insets = new Insets(0, 0, 5, 0);
        gbc_printOptionsSeparator.gridwidth = 5;
        gbc_printOptionsSeparator.gridx = 0;
        gbc_printOptionsSeparator.gridy = 3;
        content.add(printOptionsSeparator, gbc_printOptionsSeparator);
        imageDisplayLabel = new JLabel();

        imageDisplayLabel.setText(Messages.getString("DicomPrintDialog.disp_format") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbc_imageDisplayLabel = new GridBagConstraints();
        gbc_imageDisplayLabel.anchor = GridBagConstraints.EAST;
        gbc_imageDisplayLabel.insets = new Insets(0, 0, 5, 5);
        gbc_imageDisplayLabel.gridx = 0;
        gbc_imageDisplayLabel.gridy = 5;
        content.add(imageDisplayLabel, gbc_imageDisplayLabel);
        imageDisplayFormatComboBox = new JComboBox();

        imageDisplayFormatComboBox.setMaximumRowCount(10);
        imageDisplayFormatComboBox.setModel(portraitDisplayFormatsModel);
        GridBagConstraints gbc_imageDisplayFormatComboBox = new GridBagConstraints();
        gbc_imageDisplayFormatComboBox.anchor = GridBagConstraints.WEST;
        gbc_imageDisplayFormatComboBox.insets = new Insets(0, 0, 5, 5);
        gbc_imageDisplayFormatComboBox.gridx = 1;
        gbc_imageDisplayFormatComboBox.gridy = 5;
        content.add(imageDisplayFormatComboBox, gbc_imageDisplayFormatComboBox);
        filmSizeIdLabel = new JLabel();

        filmSizeIdLabel.setText(Messages.getString("DicomPrintDialog.size_id") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbc_filmSizeIdLabel = new GridBagConstraints();
        gbc_filmSizeIdLabel.anchor = GridBagConstraints.EAST;
        gbc_filmSizeIdLabel.insets = new Insets(0, 0, 5, 5);
        gbc_filmSizeIdLabel.gridx = 3;
        gbc_filmSizeIdLabel.gridy = 4;
        content.add(filmSizeIdLabel, gbc_filmSizeIdLabel);
        filmSizeIdComboBox = new JComboBox();

        filmSizeIdComboBox.setModel(new DefaultComboBoxModel(FilmSize.values()));
        GridBagConstraints gbc_filmSizeIdComboBox = new GridBagConstraints();
        gbc_filmSizeIdComboBox.anchor = GridBagConstraints.WEST;
        gbc_filmSizeIdComboBox.insets = new Insets(0, 0, 5, 0);
        gbc_filmSizeIdComboBox.gridx = 4;
        gbc_filmSizeIdComboBox.gridy = 4;
        content.add(filmSizeIdComboBox, gbc_filmSizeIdComboBox);
        filmOrientationLabel = new JLabel();

        filmOrientationLabel.setText(Messages.getString("DicomPrintDialog.film_orientation") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbc_filmOrientationLabel = new GridBagConstraints();
        gbc_filmOrientationLabel.anchor = GridBagConstraints.EAST;
        gbc_filmOrientationLabel.insets = new Insets(0, 0, 5, 5);
        gbc_filmOrientationLabel.gridx = 0;
        gbc_filmOrientationLabel.gridy = 4;
        content.add(filmOrientationLabel, gbc_filmOrientationLabel);
        GridBagConstraints gbc_filmOrientationComboBox = new GridBagConstraints();
        gbc_filmOrientationComboBox.anchor = GridBagConstraints.WEST;
        gbc_filmOrientationComboBox.insets = new Insets(0, 0, 5, 5);
        gbc_filmOrientationComboBox.gridx = 1;
        gbc_filmOrientationComboBox.gridy = 4;
        content.add(filmOrientationComboBox, gbc_filmOrientationComboBox);
        magnificationTypeLabel = new JLabel();

        magnificationTypeLabel.setText(Messages.getString("DicomPrintDialog.magn_type") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbc_magnificationTypeLabel = new GridBagConstraints();
        gbc_magnificationTypeLabel.anchor = GridBagConstraints.EAST;
        gbc_magnificationTypeLabel.insets = new Insets(0, 0, 5, 5);
        gbc_magnificationTypeLabel.gridx = 3;
        gbc_magnificationTypeLabel.gridy = 5;
        content.add(magnificationTypeLabel, gbc_magnificationTypeLabel);
        magnificationTypeComboBox = new JComboBox();

        magnificationTypeComboBox.setModel(new DefaultComboBoxModel(new String[] { "REPLICATE", "BILINEAR", "CUBIC" })); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        magnificationTypeComboBox.setSelectedIndex(2);
        GridBagConstraints gbc_magnificationTypeComboBox = new GridBagConstraints();
        gbc_magnificationTypeComboBox.anchor = GridBagConstraints.WEST;
        gbc_magnificationTypeComboBox.insets = new Insets(0, 0, 5, 0);
        gbc_magnificationTypeComboBox.gridx = 4;
        gbc_magnificationTypeComboBox.gridy = 5;
        content.add(magnificationTypeComboBox, gbc_magnificationTypeComboBox);
        smoothingTypeLabel = new JLabel();

        smoothingTypeLabel.setText(Messages.getString("DicomPrintDialog.smooth") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbc_smoothingTypeLabel = new GridBagConstraints();
        gbc_smoothingTypeLabel.anchor = GridBagConstraints.EAST;
        gbc_smoothingTypeLabel.insets = new Insets(0, 0, 5, 5);
        gbc_smoothingTypeLabel.gridx = 0;
        gbc_smoothingTypeLabel.gridy = 6;
        content.add(smoothingTypeLabel, gbc_smoothingTypeLabel);
        smoothingTypeComboBox = new JComboBox();

        smoothingTypeComboBox.setModel(new DefaultComboBoxModel(new String[] { "MEDIUM", "SHARP", "SMOOTH" })); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        GridBagConstraints gbc_smoothingTypeComboBox = new GridBagConstraints();
        gbc_smoothingTypeComboBox.fill = GridBagConstraints.HORIZONTAL;
        gbc_smoothingTypeComboBox.insets = new Insets(0, 0, 5, 5);
        gbc_smoothingTypeComboBox.gridx = 1;
        gbc_smoothingTypeComboBox.gridy = 6;
        content.add(smoothingTypeComboBox, gbc_smoothingTypeComboBox);
        borderDensityLabel = new JLabel();

        borderDensityLabel.setText(Messages.getString("DicomPrintDialog.border") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbc_borderDensityLabel = new GridBagConstraints();
        gbc_borderDensityLabel.anchor = GridBagConstraints.EAST;
        gbc_borderDensityLabel.insets = new Insets(0, 0, 5, 5);
        gbc_borderDensityLabel.gridx = 3;
        gbc_borderDensityLabel.gridy = 6;
        content.add(borderDensityLabel, gbc_borderDensityLabel);
        borderDensityComboBox = new JComboBox();

        borderDensityComboBox.setModel(new DefaultComboBoxModel(new String[] { "BLACK", "WHITE" })); //$NON-NLS-1$ //$NON-NLS-2$
        borderDensityComboBox.setSelectedIndex(1);
        GridBagConstraints gbc_borderDensityComboBox = new GridBagConstraints();
        gbc_borderDensityComboBox.anchor = GridBagConstraints.WEST;
        gbc_borderDensityComboBox.insets = new Insets(0, 0, 5, 0);
        gbc_borderDensityComboBox.gridx = 4;
        gbc_borderDensityComboBox.gridy = 6;
        content.add(borderDensityComboBox, gbc_borderDensityComboBox);

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
        GridBagConstraints gbc_trimLabel = new GridBagConstraints();
        gbc_trimLabel.anchor = GridBagConstraints.EAST;
        gbc_trimLabel.insets = new Insets(0, 0, 5, 5);
        gbc_trimLabel.gridx = 0;
        gbc_trimLabel.gridy = 7;
        content.add(trimLabel, gbc_trimLabel);
        trimComboBox = new JComboBox();

        trimComboBox.setModel(new DefaultComboBoxModel(new String[] { "NO", "YES" })); //$NON-NLS-1$ //$NON-NLS-2$
        GridBagConstraints gbc_trimComboBox = new GridBagConstraints();
        gbc_trimComboBox.anchor = GridBagConstraints.SOUTHWEST;
        gbc_trimComboBox.insets = new Insets(0, 0, 5, 5);
        gbc_trimComboBox.gridx = 1;
        gbc_trimComboBox.gridy = 7;
        content.add(trimComboBox, gbc_trimComboBox);

        labelEmpty = new JLabel();
        labelEmpty.setText(Messages.getString("DicomPrintDialog.empty_density") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbc_label_1 = new GridBagConstraints();
        gbc_label_1.anchor = GridBagConstraints.EAST;
        gbc_label_1.insets = new Insets(0, 0, 5, 5);
        gbc_label_1.gridx = 3;
        gbc_label_1.gridy = 7;
        content.add(labelEmpty, gbc_label_1);

        comboBoxEmpty = new JComboBox();
        comboBoxEmpty.setModel(new DefaultComboBoxModel(new String[] { "BLACK", "WHITE" })); //$NON-NLS-1$ //$NON-NLS-2$
        GridBagConstraints gbc_comboBox = new GridBagConstraints();
        gbc_comboBox.anchor = GridBagConstraints.NORTHWEST;
        gbc_comboBox.insets = new Insets(0, 0, 5, 0);
        gbc_comboBox.gridx = 4;
        gbc_comboBox.gridy = 7;
        content.add(comboBoxEmpty, gbc_comboBox);
        printAnnotationsCheckBox = new JCheckBox();

        printAnnotationsCheckBox.setText(Messages.getString("PrintDialog.annotate")); //$NON-NLS-1$
        printAnnotationsCheckBox.setSelected(true);
        GridBagConstraints gbc_printAnnotationsCheckBox = new GridBagConstraints();
        gbc_printAnnotationsCheckBox.anchor = GridBagConstraints.NORTHWEST;
        gbc_printAnnotationsCheckBox.insets = new Insets(0, 0, 5, 5);
        gbc_printAnnotationsCheckBox.gridwidth = 2;
        gbc_printAnnotationsCheckBox.gridx = 0;
        gbc_printAnnotationsCheckBox.gridy = 9;
        content.add(printAnnotationsCheckBox, gbc_printAnnotationsCheckBox);

        chckbxSelctedView = new JCheckBox(Messages.getString("PrintDialog.selected_view")); //$NON-NLS-1$
        GridBagConstraints gbc_chckbxSelctedView = new GridBagConstraints();
        gbc_chckbxSelctedView.insets = new Insets(0, 0, 0, 5);
        gbc_chckbxSelctedView.anchor = GridBagConstraints.NORTHWEST;
        gbc_chckbxSelctedView.gridwidth = 2;
        gbc_chckbxSelctedView.gridx = 0;
        gbc_chckbxSelctedView.gridy = 10;
        content.add(chckbxSelctedView, gbc_chckbxSelctedView);
        rootPane.setLayout(new BorderLayout(0, 0));
        this.getContentPane().add(printersCfg, BorderLayout.NORTH);

        printerLabel = new JLabel();
        printersCfg.add(printerLabel);

        printerLabel.setText(Messages.getString("DicomPrintDialog.printer") + StringUtil.COLON); //$NON-NLS-1$
        printersComboBox = new JComboBox();
        printersCfg.add(printersComboBox);

        printersComboBox.setModel(new DefaultComboBoxModel());
        DicomPrinter.loadPrintersSettings(printersComboBox);

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
                deleteButtonActionPerformed(evt);
            }
        });
        editButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editButtonActionPerformed(evt);
            }
        });
        addPrinterButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addPrinterButtonActionPerformed(evt);
            }
        });
        printersComboBox.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                printersComboBoxActionPerformed(evt);
            }
        });
        this.getContentPane().add(content, BorderLayout.CENTER);

        label = new JLabel();
        label.setText(Messages.getString("DicomPrintDialog.dpi") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbc_label = new GridBagConstraints();
        gbc_label.anchor = GridBagConstraints.EAST;
        gbc_label.insets = new Insets(0, 0, 0, 5);
        gbc_label.gridx = 3;
        gbc_label.gridy = 10;
        content.add(label, gbc_label);

        comboBoxDPI = new JComboBox();
        GridBagConstraints gbc_comboBoxDPI = new GridBagConstraints();
        gbc_comboBoxDPI.anchor = GridBagConstraints.NORTHWEST;
        gbc_comboBoxDPI.gridx = 4;
        gbc_comboBoxDPI.gridy = 10;
        comboBoxDPI.setModel(new DefaultComboBoxModel(DotPerInches.values()));
        comboBoxDPI.setSelectedIndex(1);
        content.add(comboBoxDPI, gbc_comboBoxDPI);

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
                dispose();
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
        dicomPrintOptions.setDicomPrinter((DicomPrinter) printersComboBox.getSelectedItem());

        DicomPrint dicomPrint = new DicomPrint(dicomPrintOptions);
        PrintOptions printOptions = new PrintOptions(printAnnotationsCheckBox.isSelected(), 1.0);
        printOptions.setColor(dicomPrintOptions.isPrintInColor());

        ImageViewerPlugin container = eventManager.getSelectedView2dContainer();

        List<DefaultView2d<ImageElement>> views = container.getImagePanels();
        if (views.isEmpty()) {
            JOptionPane.showMessageDialog(this, Messages.getString("DicomPrintDialog.no_print"), //$NON-NLS-1$
                null, JOptionPane.ERROR_MESSAGE);
            doClose();
            return;
        }
        doClose();

        GridBagLayoutModel layoutModel = container.getLayoutModel();
        if (chckbxSelctedView.isSelected()) {
            final LinkedHashMap<LayoutConstraints, Component> elements =
                new LinkedHashMap<LayoutConstraints, Component>(1);
            layoutModel = new GridBagLayoutModel(elements, "sel_tmp", "", null);
            DefaultView2d val = eventManager.getSelectedViewPane();
            elements.put(new LayoutConstraints(val.getClass().getName(), 0, 0, 0, 1, 1, 1.0, 1.0,
                GridBagConstraints.CENTER, GridBagConstraints.BOTH), val);
        }
        ExportLayout<ImageElement> layout = new ExportLayout<ImageElement>(layoutModel);
        try {
            dicomPrint.printImage(dicomPrint.printImage(layout, printOptions));
        } catch (Exception ex) {
            AuditLog.logError(LOGGER, ex, "DICOM Print Service"); //$NON-NLS-1$
            JOptionPane.showMessageDialog(this, Messages.getString("DicomPrintDialog.error_print"), // $NON-NLS-1$ //$NON-NLS-1$
                Messages.getString("DicomPrintDialog.error"), JOptionPane.ERROR_MESSAGE); // $NON-NLS-1$ //$NON-NLS-1$
        } finally {
            layout.dispose();
        }
    }

    private void doClose() {
        dispose();
    }

    private void addPrinterButtonActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_addPrinterButtonActionPerformed
        PrinterDialog dialog = new PrinterDialog(SwingUtilities.getWindowAncestor(this), "", null, printersComboBox); //$NON-NLS-1$
        JMVUtils.showCenterScreen(dialog, this);
        enableOrDisableColorPrint();
    }

    private void editButtonActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_editButtonActionPerformed
        PrinterDialog dialog = new PrinterDialog(SwingUtilities.getWindowAncestor(this), "", //$NON-NLS-1$
            (DicomPrinter) printersComboBox.getSelectedItem(), printersComboBox);
        JMVUtils.showCenterScreen(dialog, this);
        enableOrDisableColorPrint();
    }

    private void deleteButtonActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_deleteButtonActionPerformed
        int index = printersComboBox.getSelectedIndex();
        if (index >= 0) {
            printersComboBox.removeItemAt(index);
            DicomPrinter.savePrintersSettings(printersComboBox);
            enableOrDisableColorPrint();
        }
    }

    private void printersComboBoxActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_printersComboBoxActionPerformed
        enableOrDisableColorPrint();
    }

    private void enableOrDisableColorPrint() {
        DicomPrinter selectedPrinter = (DicomPrinter) printersComboBox.getSelectedItem();
        if (selectedPrinter != null) {
            boolean color = selectedPrinter.isColorPrintSupported();
            colorPrintCheckBox.setSelected(color);
            colorPrintCheckBox.setEnabled(color);
        }
    }
}
