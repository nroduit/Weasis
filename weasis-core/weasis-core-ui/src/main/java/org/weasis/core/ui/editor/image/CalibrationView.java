package org.weasis.core.ui.editor.image;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.geom.Point2D;

import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
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
    private final JLabel lblApplyTo = new JLabel("Apply to:");
    private final JPanel panel = new JPanel();
    private final ButtonGroup ratioGroup = new ButtonGroup();
    private final JRadioButton radioButtonSeries = new JRadioButton("Series");
    private final JRadioButton radioButtonImage = new JRadioButton("Current Image");

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
        jPanelMode.setLayout(gridBagLayout2);
        JMVUtils.setPreferredWidth(jTextFieldLineWidth, 170);
        jTextFieldLineWidth.setFormatterFactory(DecFormater.setPreciseDoubleFormat(0.000005d, Double.MAX_VALUE));
        jTextFieldLineWidth.setValue(1.0);
        JMVUtils.addCheckAction(jTextFieldLineWidth);

        jLabelKnownDist.setText("Known distance :");
        this.setLayout(borderLayout1);

        this.add(jPanelMode, BorderLayout.CENTER);
        jPanelMode.add(jComboBoxUnit, new GridBagConstraints(3, 4, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(0, 3, 0, 0), 0, 0));
        jPanelMode.add(jLabelKnownDist, new GridBagConstraints(0, 4, 2, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(0, 10, 0, 5), 0, 0));
        jPanelMode.add(jTextFieldLineWidth, new GridBagConstraints(2, 4, 1, 1, 1.0, 0.0, GridBagConstraints.WEST,
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
                jTextFieldLineWidth.setValue(line.getSegmentLength(image.getPixelSizeX(), image.getPixelSizeY()));
            } else {
                unit = Unit.MILLIMETER;
            }
            jComboBoxUnit.setSelectedItem(unit);
        }
    }

    public void applyNewCalibration() {
        ImageElement image = view2d.getImage();
        if (image != null) {
            Number inputCalibVal = JMVUtils.getFormattedValue(jTextFieldLineWidth);
            if (inputCalibVal != null) {
                double ratioX = image.getPixelSizeX();
                double ratioY = image.getPixelSizeY();

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
                    if (ratioX != ratioY) {
                        // TODO find best to calibrate non-square pixels
                        Point2D p1 = line.getStartPoint();
                        Point2D p2 = line.getEndPoint();
                        double dx = Math.abs(p1.getX() - p2.getX());
                        double dy = Math.abs(p1.getY() - p2.getY());
                        double newRatioX = (inputCalibVal.doubleValue() * unitRatio) / dx;
                        double newRatioY = (inputCalibVal.doubleValue() * unitRatio) / dy;
                        if (dx > dy) {

                        } else {

                        }
                    } else {
                        double newRatio = (inputCalibVal.doubleValue() * unitRatio) / lineLength;
                        if (ratioX != newRatio) {
                            image.setPixelSizeX(newRatio);
                            image.setPixelSizeY(newRatio);
                        }
                    }
                }
            }
        }
    }

    public static double convertDistanceToFactor(double knownDistance, double lineLength, Unit unit) {
        return (knownDistance * unit.getConvFactor()) / lineLength;
    }

}
