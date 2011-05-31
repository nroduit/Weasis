/*******************************************************************************
 * Copyright (c) 2011 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.editor.image;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.LineGraphic;

public class CalibrationView extends JPanel {

    private final DefaultView2d view2d;
    private final LineGraphic line;

    private final JComboBox jComboBoxUnit = new JComboBox(Unit.getUniExceptPixel().toArray());
    private final JPanel jPanelMode = new JPanel();
    private final JFormattedTextField jTextFieldLineWidth = new JFormattedTextField();

    private final JLabel jLabelKnownDist = new JLabel();
    private final BorderLayout borderLayout1 = new BorderLayout();
    private final GridBagLayout gridBagLayout2 = new GridBagLayout();
    private final JLabel lblApplyTo = new JLabel(Messages.getString("CalibrationView.apply")); //$NON-NLS-1$
    private final JPanel panel = new JPanel();
    private final ButtonGroup ratioGroup = new ButtonGroup();
    private final JRadioButton radioButtonSeries = new JRadioButton(Messages.getString("CalibrationView.series")); //$NON-NLS-1$
    private final JRadioButton radioButtonImage = new JRadioButton(Messages.getString("CalibrationView.current")); //$NON-NLS-1$

    public CalibrationView(LineGraphic line, DefaultView2d view2d) {
        this.line = line;
        this.view2d = view2d;
        try {
            jbInit();
            initialize();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    void jbInit() throws Exception {
        gridBagLayout2.rowWeights = new double[] { 1.0, 0.0 };
        gridBagLayout2.columnWeights = new double[] { 0.0, 1.0, 0.0, 0.0 };
        jPanelMode.setLayout(gridBagLayout2);
        JMVUtils.setPreferredWidth(jTextFieldLineWidth, 170);
        jTextFieldLineWidth.setFormatterFactory(DecFormater.setPreciseDoubleFormat(0.000005d, Double.MAX_VALUE));
        jTextFieldLineWidth.setValue(1.0);
        JMVUtils.addCheckAction(jTextFieldLineWidth);

        jLabelKnownDist.setText(Messages.getString("CalibrationView.known")); //$NON-NLS-1$
        this.setLayout(borderLayout1);

        this.add(jPanelMode, BorderLayout.CENTER);

        jPanelMode.add(jComboBoxUnit, new GridBagConstraints(3, 1, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(0, 3, 0, 0), 0, 0));
        jPanelMode.add(jLabelKnownDist, new GridBagConstraints(0, 1, 2, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(0, 10, 0, 5), 0, 0));
        jPanelMode.add(jTextFieldLineWidth, new GridBagConstraints(2, 1, 1, 1, 1.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.HORIZONTAL, new Insets(0, 2, 0, 5), 0, 0));
        FlowLayout flowLayout = (FlowLayout) panel.getLayout();
        flowLayout.setHgap(10);
        flowLayout.setAlignment(FlowLayout.LEFT);

        add(panel, BorderLayout.SOUTH);
        panel.add(lblApplyTo);
        ratioGroup.add(radioButtonSeries);
        ratioGroup.add(radioButtonImage);
        radioButtonSeries.setSelected(true);
        panel.add(radioButtonSeries);
        panel.add(radioButtonImage);

    }

    private void initialize() {
        ImageElement image = view2d.getImage();
        if (image != null) {
            Unit unit = image.getPixelSpacingUnit();
            if (!Unit.PIXEL.equals(unit)) {
                jTextFieldLineWidth.setValue(line.getSegmentLength(image.getPixelSize(), image.getPixelSize()));
            } else {
                GridBagConstraints gbc_textPane = new GridBagConstraints();
                gbc_textPane.gridwidth = 4;
                gbc_textPane.insets = new Insets(0, 0, 5, 5);
                gbc_textPane.fill = GridBagConstraints.HORIZONTAL;
                gbc_textPane.gridx = 0;
                gbc_textPane.gridy = 0;
                gbc_textPane.weightx = 1.0;
                gbc_textPane.weighty = 1.0;
                JScrollPane scroll = new JScrollPane(createArea(Messages.getString("CalibrationView.warn"), //$NON-NLS-1$
                    true, 0));
                scroll.setPreferredSize(new Dimension(300, 75));
                jPanelMode.add(scroll, gbc_textPane);
                unit = Unit.MILLIMETER;
            }

            jComboBoxUnit.setSelectedItem(unit);
        }
    }

    private JTextArea createArea(String text, boolean lineWrap, int columns) {
        JTextArea area = new JTextArea(text);
        area.setBorder(new CompoundBorder(BorderFactory.createRaisedBevelBorder(), new EmptyBorder(3, 5, 3, 5)));
        area.setLineWrap(lineWrap);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setColumns(columns);
        return area;
    }

    public void applyNewCalibration() {
        ImageElement image = view2d.getImage();
        if (image != null) {
            Number inputCalibVal = JMVUtils.getFormattedValue(jTextFieldLineWidth);
            if (inputCalibVal != null) {
                double factor = image.getPixelSize();
                Unit unit = (Unit) jComboBoxUnit.getSelectedItem();
                Unit originalUnit = image.getPixelSpacingUnit();
                if (!Unit.PIXEL.equals(unit)) {
                    double unitRatio = 1.0;
                    if (Unit.PIXEL.equals(originalUnit)) {
                        originalUnit = unit;
                        image.setPixelSpacingUnit(originalUnit);
                    } else {
                        unitRatio = originalUnit.getConversionRatio(unit.getConvFactor());
                    }
                    double lineLength = line.getSegmentLength();
                    if (lineLength < 1.0) {
                        lineLength = 1.0;
                    }
                    double newRatio = (inputCalibVal.doubleValue() * unitRatio) / lineLength;
                    if (factor != newRatio) {
                        if (radioButtonSeries.isSelected()) {
                            MediaSeries seriesList = view2d.getSeries();
                            if (seriesList != null) {
                                for (Object media : seriesList.getMedias()) {
                                    if (media instanceof ImageElement) {
                                        ImageElement img = (ImageElement) media;
                                        img.setPixelSize(newRatio);
                                        updateLabel(img, view2d);
                                    }
                                }
                            }
                        } else {
                            image.setPixelSize(newRatio);
                            updateLabel(image, view2d);
                        }
                        view2d.repaint();
                    }
                }
            }
        }
    }

    private void updateLabel(ImageElement image, DefaultView2d view2d) {
        List<Graphic> list = (List<Graphic>) image.getTagValue(TagW.MeasurementGraphics);
        if (list != null) {
            for (Graphic graphic : list) {
                graphic.updateLabel(image, view2d);
            }
        }
    }
}
