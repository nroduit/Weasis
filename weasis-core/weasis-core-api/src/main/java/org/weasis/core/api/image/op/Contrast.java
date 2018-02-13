/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.image.op;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.DataBuffer;
import java.awt.image.renderable.ParameterBlock;
import java.util.Hashtable;

import javax.media.jai.JAI;
import javax.media.jai.PlanarImage;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.util.StringUtil;

public class Contrast extends JPanel {

    public static final String[] sliderLabels = { "Low -127", "0", "High 127" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    public static final String[] gammaLabels = { "0.01", "1", "2" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    private GridBagLayout gridBagLayout1 = new GridBagLayout();
    private JSlider jSliderContrast = new JSlider(-127, 127, 0);
    private JSlider jSliderLum = new JSlider(-127, 127, 0);
    private JSlider jSliderGamma = new JSlider(1, 200, 100);
    private TitledBorder title1 = new TitledBorder(Messages.getString("Contrast.contrast") + StringUtil.COLON); //$NON-NLS-1$
    private TitledBorder title2 = new TitledBorder(Messages.getString("Contrast.lum" + StringUtil.COLON)); //$NON-NLS-1$
    private TitledBorder title3 = new TitledBorder(Messages.getString("Contrast.gamma" + StringUtil.COLON)); //$NON-NLS-1$
    private JButton jButtonReset = new JButton();

    private ChangeListener sliderListener = new ChangeListener() {

        @Override
        public void stateChanged(ChangeEvent e) {
            updateValues();
        }
    };

    public Contrast() {
        try {
            jbInit();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void jbInit() throws Exception {
        this.setLayout(gridBagLayout1);
        Hashtable labels = new Hashtable();
        Hashtable glabels = new Hashtable();
        int[] gammaVal = { 1, 100, 200 };
        for (int label = -127, k = 0; label < 128; label += 127, k++) {
            JLabel aLabel = new JLabel(sliderLabels[k]);
            labels.put(label, aLabel);
            JLabel gLabel = new JLabel(gammaLabels[k]);
            glabels.put(gammaVal[k], gLabel);
        }

        jSliderContrast.setMajorTickSpacing(127);
        jSliderContrast.setPaintTicks(true);
        jSliderContrast.setLabelTable(labels);
        jSliderContrast.setPaintLabels(true);
        jSliderContrast.setBorder(title1);
        JMVUtils.setPreferredWidth(jSliderContrast, 80);
        jSliderContrast.addChangeListener(sliderListener);

        jSliderLum.setMajorTickSpacing(127);
        jSliderLum.setPaintTicks(true);
        jSliderLum.setLabelTable(labels);
        jSliderLum.setPaintLabels(true);
        jSliderLum.setBorder(title2);
        JMVUtils.setPreferredWidth(jSliderLum, 80);
        jSliderLum.addChangeListener(sliderListener);

        jSliderGamma.setMajorTickSpacing(99);
        jSliderGamma.setPaintTicks(true);
        jSliderGamma.setLabelTable(glabels);
        jSliderGamma.setPaintLabels(true);
        jSliderGamma.setBorder(title3);
        JMVUtils.setPreferredWidth(jSliderGamma, 80);
        jSliderGamma.addChangeListener(sliderListener);
        jButtonReset.setText(Messages.getString("Contrast.reset")); //$NON-NLS-1$
        jButtonReset.addActionListener(new java.awt.event.ActionListener() {

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                reset();
            }
        });
        this.add(jSliderContrast, new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL, new Insets(0, 5, 0, 5), 0, 0));
        this.add(jSliderLum, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL, new Insets(10, 5, 0, 5), 0, 0));
        this.add(jSliderGamma, new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL, new Insets(10, 5, 0, 5), 0, 0));
        this.add(jButtonReset, new GridBagConstraints(0, 3, 1, 1, 0.0, 0.0, GridBagConstraints.EAST,
            GridBagConstraints.NONE, new Insets(10, 0, 10, 20), 0, 0));
    }

    public final void reset() {
        setSliderValues(0, 0, 100);
    }

    public void addSilersListener(ChangeListener sliderListener) {
        jSliderContrast.addChangeListener(sliderListener);
        jSliderLum.addChangeListener(sliderListener);
        jSliderGamma.addChangeListener(sliderListener);
    }

    /** returns a 3band byte image */
    public static final PlanarImage getRescaledImage(PlanarImage image, double slope, double y_int) {
        if (image == null) {
            return null;
        }
        PlanarImage dst = null;
        int bands = image.getSampleModel().getNumBands();

        // use a lookup table for rescaling
        double[] slopes = new double[bands];
        double[] y_ints = new double[bands];

        for (int i = 0; i < bands; i++) {
            slopes[i] = slope;
            y_ints[i] = y_int;
        }

        // rescale from xxx to byte range
        ParameterBlock pb = new ParameterBlock();
        pb.addSource(image);
        pb.add(slopes);
        pb.add(y_ints);
        dst = JAI.create("rescale", pb, null); //$NON-NLS-1$

        // produce a byte image
        pb = new ParameterBlock();
        pb.addSource(dst);
        pb.add(DataBuffer.TYPE_BYTE);
        dst = JAI.create("format", pb, null); //$NON-NLS-1$

        return dst;
    }

    private void updateValues() {
        title1.setTitle(Messages.getString("Contrast.contrast") + jSliderContrast.getValue() + " "); //$NON-NLS-1$ //$NON-NLS-2$
        title2.setTitle(Messages.getString("Contrast.lum") + jSliderLum.getValue() + " "); //$NON-NLS-1$ //$NON-NLS-2$
        title3.setTitle(Messages.getString("Contrast.gamma") + jSliderGamma.getValue() / 100f + " "); //$NON-NLS-1$ //$NON-NLS-2$
        jSliderContrast.repaint();
        jSliderLum.repaint();
        jSliderGamma.repaint();
    }

    public PlanarImage updateSlider(PlanarImage image) {
        image = setBrightness(image, jSliderLum.getValue());
        // image = setContrast(image, jSliderContrast.getValue());
        // return setGamma(image, (float) (jSliderGamma.getValue() / 100.0));
        return image;
    }

    public void setSliderValues(int brightness, int contrast, int gamma) {
        jSliderContrast.setValue(contrast);
        jSliderLum.setValue(brightness);
        jSliderGamma.setValue(gamma);
    }

    public static PlanarImage setBrightness(PlanarImage image, int value) {
        if (value == 0) {
            return image;
        }
        double adjConstants[] = { value };
        ParameterBlock pb = new ParameterBlock();
        pb.addSource(image);
        pb.add(adjConstants);
        return JAI.create("addconst", pb, null); //$NON-NLS-1$
    }

    // public static PlanarImage setContrast(PlanarImage image, int value) {
    // if (value == 0) {
    // return image;
    // }
    // byte[] lut = new byte[256];
    // for (int i = 0; i < 256; i++) {
    // double scale = i + (value * Math.sin((0.0245 * i) - 3.14));
    // if (scale > 255.0) {
    // scale = 255.0;
    // }
    // else if (scale < 0.0) {
    // scale = 0.0;
    // }
    //
    // lut[i] = (byte) scale;
    // }
    // return PseudoColorOp.applyLookupTable(image, new LookupTableJAI(lut));
    // }
    //
    // public static PlanarImage setGamma(PlanarImage image, float value) {
    // byte lut[][] = createGammaLUT(value, image.getNumBands());
    // return PseudoColorOp.applyLookupTable(image, new LookupTableJAI(lut));
    // }

    private static byte[][] createGammaLUT(float f, int nbBand) {
        byte lut[][] = new byte[nbBand][256];
        for (int i = 0; i < 256; i++) {
            int j = (int) (Math.pow(i / 255F, f) * 255D);
            if (j > 255) {
                j = 255;
            } else if (j < 0) {
                j = 0;
            }
            for (int k = 0; k < lut.length; k++) {
                lut[k][i] = (byte) j;
            }
        }
        return lut;
    }

}
