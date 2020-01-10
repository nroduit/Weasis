/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.editor.image;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Objects;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;

public class CalibrationView extends JPanel {
    private static final long serialVersionUID = -1098044466661041480L;

    private static final Logger LOGGER = LoggerFactory.getLogger(CalibrationView.class);

    private final ViewCanvas<?> view2d;
    private final LineGraphic line;

    private final JComboBox<Unit> jComboBoxUnit;
    private final JPanel jPanelMode = new JPanel();
    private final JFormattedTextField jTextFieldLineWidth = new JFormattedTextField();

    private final JLabel jLabelKnownDist = new JLabel();
    private final BorderLayout borderLayout1 = new BorderLayout();
    private final GridBagLayout gridBagLayout2 = new GridBagLayout();
    private final JLabel lblApplyTo = new JLabel(Messages.getString("CalibrationView.apply") + StringUtil.COLON); //$NON-NLS-1$
    private final JPanel panel = new JPanel();
    private final ButtonGroup ratioGroup = new ButtonGroup();
    private final JRadioButton radioButtonSeries = new JRadioButton(Messages.getString("CalibrationView.series")); //$NON-NLS-1$
    private final JRadioButton radioButtonImage = new JRadioButton(Messages.getString("CalibrationView.current")); //$NON-NLS-1$

    public CalibrationView(LineGraphic line, ViewCanvas<?> view2d, boolean selectSeries) {
        this.line = line;
        this.view2d = view2d;
        List<Unit> units = Unit.getUnitExceptPixel();
        this.jComboBoxUnit = new JComboBox<>(units.toArray(new Unit[units.size()]));
        try {
            jbInit();
            radioButtonSeries.setSelected(selectSeries);
            if (!selectSeries) {
                radioButtonImage.setSelected(true);
            }
            initialize();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    void jbInit() {
        gridBagLayout2.rowWeights = new double[] { 1.0, 0.0 };
        gridBagLayout2.columnWeights = new double[] { 0.0, 1.0, 0.0, 0.0 };
        jPanelMode.setLayout(gridBagLayout2);
        JMVUtils.setPreferredWidth(jTextFieldLineWidth, 170);
        jTextFieldLineWidth.setLocale(LocalUtil.getLocaleFormat());
        jTextFieldLineWidth.setFormatterFactory(DecFormater.setPreciseDoubleFormat(0.000005d, Double.MAX_VALUE));
        jTextFieldLineWidth.setValue(1.0);
        JMVUtils.addCheckAction(jTextFieldLineWidth);

        jLabelKnownDist.setText(Messages.getString("CalibrationView.known") + StringUtil.COLON); //$NON-NLS-1$
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
        panel.add(radioButtonSeries);
        panel.add(radioButtonImage);
    }

    public boolean isApplyingToSeries() {
        return radioButtonSeries.isSelected();
    }

    private void initialize() {
        ImageElement image = view2d.getImage();
        if (image != null) {
            Unit unit = image.getPixelSpacingUnit();
            if (!Unit.PIXEL.equals(unit)) {
                Point2D ptA = line.getStartPoint();
                Point2D ptB = line.getEndPoint();
                if (Objects.nonNull(ptA) && Objects.nonNull(ptB)) {
                    jTextFieldLineWidth.setValue(ptA.distance(ptB) * image.getPixelSize());
                }
            } else {
                GridBagConstraints gbcTextPane = new GridBagConstraints();
                gbcTextPane.gridwidth = 4;
                gbcTextPane.insets = new Insets(0, 0, 5, 5);
                gbcTextPane.fill = GridBagConstraints.HORIZONTAL;
                gbcTextPane.gridx = 0;
                gbcTextPane.gridy = 0;
                gbcTextPane.weightx = 1.0;
                gbcTextPane.weighty = 1.0;
                JScrollPane scroll = new JScrollPane(createArea(Messages.getString("CalibrationView.warn"), //$NON-NLS-1$
                    true, 0));
                scroll.setPreferredSize(new Dimension(300, 75));
                jPanelMode.add(scroll, gbcTextPane);
                unit = Unit.MILLIMETER;
            }

            jComboBoxUnit.setSelectedItem(unit);
        }
    }

    private static JTextArea createArea(String text, boolean lineWrap, int columns) {
        JTextArea area = new JTextArea(text);
        area.setBorder(new CompoundBorder(BorderFactory.createRaisedBevelBorder(), new EmptyBorder(3, 5, 3, 5)));
        area.setLineWrap(lineWrap);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setColumns(columns);
        return area;
    }

    public void removeCalibration() {
        applyCalibration(1.0, Unit.PIXEL);
    }

    private void applyCalibration(double ratio, Unit unit) {
        ImageElement image = view2d.getImage();
        if (image != null) {
            if (radioButtonSeries.isSelected()) {
                MediaSeries<?> seriesList = view2d.getSeries();
                if (Objects.nonNull(seriesList)) {
                    Iterable<?> list = seriesList.getMedias(null, null);
                    synchronized (seriesList) {
                        for (Object media : list) {
                            if (media instanceof ImageElement && media != image) {
                                ImageElement img = (ImageElement) media;
                                img.setPixelSpacingUnit(unit);
                                img.setPixelSize(ratio);
                            }
                        }
                    }
                }
            }
            image.setPixelSize(ratio);
            image.setPixelSpacingUnit(unit);

            if (view2d.getEventManager().getSelectedViewPane() == view2d) {
                ActionState spUnitAction = view2d.getEventManager().getAction(ActionW.SPATIAL_UNIT);
                if (spUnitAction instanceof ComboItemListener) {
                    ((ComboItemListener) spUnitAction).setSelectedItem(unit);
                }
            }
            view2d.getGraphicManager().updateLabels(Boolean.TRUE, view2d);
        }
    }

    public void applyNewCalibration() {
        ImageElement image = view2d.getImage();
        if (image != null) {
            Number inputCalibVal = JMVUtils.getFormattedValue(jTextFieldLineWidth);
            if (inputCalibVal != null) {
                double imgRatio = image.getPixelSize();
                Unit unit = (Unit) jComboBoxUnit.getSelectedItem();
                Unit imgUnit = image.getPixelSpacingUnit();
                if (!Unit.PIXEL.equals(unit)) {
                    image.setPixelSpacingUnit(unit);
                    Double lineLength = null;
                    Point2D ptA = line.getStartPoint();
                    Point2D ptB = line.getEndPoint();
                    if (Objects.nonNull(ptA) && Objects.nonNull(ptB)) {
                        lineLength = ptA.distance(ptB);
                    }

                    if (Objects.isNull(lineLength) || lineLength < 1d) {
                        lineLength = 1.0;
                    }
                    double newRatio = inputCalibVal.doubleValue() / lineLength;
                    if ((Objects.nonNull(imgRatio) && MathUtil.isDifferent(newRatio, imgRatio))
                        || !Objects.equals(unit, imgUnit)) {
                        applyCalibration(newRatio, unit);
                    }
                }
            }
        }
    }
}
