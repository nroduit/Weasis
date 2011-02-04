package org.weasis.core.ui.editor.image;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.image.util.AbbreviationUnit;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.graphic.LineGraphic;

/**
 * <p>
 * Title: JMicroVision
 * </p>
 * <p>
 * Description: Thin sections analysis
 * </p>
 * <p>
 * Copyright: Copyright (c) 2002
 * </p>
 * <p>
 * Company:
 * </p>
 * 
 * @author Nicolas Roduit
 * @version 1.0
 */

public class CalibrationView extends JPanel {

    private final DefaultView2d<ImageElement> view2d;
    private final LineGraphic line;

    private final JComboBox jComboBoxUnit = new JComboBox(Unit.getAbbreviationUnits().toArray());
    private final JPanel jPanelMode = new JPanel();
    private final JFormattedTextField jTextFieldLineWidth = new JFormattedTextField();

    private final JLabel jLabelKnownDist = new JLabel();
    private final BorderLayout borderLayout1 = new BorderLayout();
    private final GridBagLayout gridBagLayout2 = new GridBagLayout();

    public CalibrationView(LineGraphic line, DefaultView2d<ImageElement> view2d) {
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
        jPanelMode.setLayout(gridBagLayout2);
        JMVUtils.setPreferredWidth(jTextFieldLineWidth, 80);
        jTextFieldLineWidth.setFormatterFactory(DecFormater.setPreciseDoubleFormat(0.000005d, Double.MAX_VALUE));
        jTextFieldLineWidth.setValue(1.0);
        JMVUtils.addCheckAction(jTextFieldLineWidth);

        jLabelKnownDist.setText("Known distance :");
        this.setLayout(borderLayout1);

        this.add(jPanelMode, java.awt.BorderLayout.NORTH);
        jPanelMode.add(jComboBoxUnit, new GridBagConstraints(3, 4, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(0, 3, 0, 5), 0, 0));
        jPanelMode.add(jLabelKnownDist, new GridBagConstraints(0, 4, 2, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(0, 18, 0, 0), 0, 0));
        jPanelMode.add(jTextFieldLineWidth, new GridBagConstraints(2, 4, 1, 1, 1.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.HORIZONTAL, new Insets(0, 2, 0, 0), 0, 0));
    }

    private void initialize() {
        ImageElement image = view2d.getImage();
        if (image != null) {
            Unit unit = image.getPixelSpacingUnit();
            jTextFieldLineWidth.setValue(line.getSegmentLength(image.getPixelSizeX(), image.getPixelSizeY()));
            jComboBoxUnit.setSelectedItem(unit);
        }
    }

    private void applyNewCalibration() {
        ImageElement image = view2d.getImage();
        if (image != null) {
            double valx = image.getPixelSizeX();
            double valy = image.getPixelSizeY();
            Number inputCalibVal = JMVUtils.getFormattedValue(jTextFieldLineWidth);
            if (inputCalibVal != null && inputCalibVal.doubleValue() != valx) {
                double lineLength = line.getSegmentLength();
                if (lineLength < 1.0) {
                    lineLength = 1.0;
                }
                Unit unit = ((AbbreviationUnit) jComboBoxUnit.getSelectedItem()).getUnit();

                // currentRatio = convertDistanceToFactor(inputCalibVal, lineLength, unit);
            }
        }
    }

    public static double convertDistanceToFactor(double knownDistance, double lineLength, Unit unit) {
        return (knownDistance * unit.getConvFactor()) / lineLength;
    }

}
