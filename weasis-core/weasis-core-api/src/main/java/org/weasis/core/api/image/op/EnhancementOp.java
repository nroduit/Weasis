/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.image.op;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.DataBuffer;
import java.awt.image.renderable.ParameterBlock;

import javax.media.jai.Histogram;
import javax.media.jai.JAI;
import javax.media.jai.LookupTableJAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.RenderedOp;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.util.JMVUtils;

/**
 * <p>
 * Title: JMicroVision
 * </p>
 * 
 * <p>
 * Description:
 * </p>
 * 
 * <p>
 * Copyright: Copyright (c) 2002 -2004
 * </p>
 * 
 * <p>
 * Company:
 * </p>
 * 
 * @author Nicolas Roduit
 * @version 1.1
 */
public class EnhancementOp extends JPanel {

    public static final String[] histoLabels =
        { "None", "Manual Enhancement", "Automatic Levels", "Equalized Levels", "Background subtraction" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

    private Contrast contrast;
    private JPanel jPanel1 = new JPanel();
    private GridBagLayout gridBagLayout1 = new GridBagLayout();
    private JSlider jSlider1 = new JSlider();
    private TitledBorder title1 = new TitledBorder("Luminosity offset : 0"); //$NON-NLS-1$
    private JButton jButtonArtificial = new JButton();

    private EnhancementOp() {
        try {
            jbInit();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void jbInit() throws Exception {
        jPanel1.setLayout(gridBagLayout1);
        jSlider1.setMinimum(0);
        jSlider1.setMaximum(255);
        jSlider1.setValue(127);
        jSlider1.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                updateValues();
            }
        });
        jSlider1.setBorder(title1);
        JMVUtils.setPreferredWidth(jSlider1, 80);

        jPanel1.add(jSlider1, new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 0, 0));
        jPanel1.add(jButtonArtificial, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(10, 5, 5, 0), 0, 0));
        // jSlider1.addChangeListener(imageFactoryView.getOperatorChangeListener());
    }

    private void updateValues() {
        title1.setTitle("Luminosity offset : " + jSlider1.getValue() + " "); //$NON-NLS-1$ //$NON-NLS-2$
        jSlider1.repaint();
    }

    private Contrast getContrastPanel() {
        if (contrast == null) {
            contrast = new Contrast();
            // contrast.addSilersListener(imageFactoryView.getOperatorChangeListener());
        }
        return contrast;
    }

    public String getHelpEntry() {
        return "Enhancement"; //$NON-NLS-1$
    }

    // public PlanarImage process(PlanarImage source1, PlanarImage source2) {
    // int index = getJComboBoxOpList().getSelectedIndex();
    // if (index == 0 || ImageUtil.isBinary(source1.getSampleModel())) {
    // return source1;
    // }
    // if (index > 1 && index != 4
    // && JMVUtils.getNumberOfInvolvedTiles(source1, imageFactoryView.getRealAreaForWholeImg()) > 9) {
    // imageFactoryView.enableAnotherOperation(false);
    // new Process(index, source1).start();
    // return null;
    // }
    // if (index == 1) {
    // imageFactoryView.getJPanelParam().add(getContrastPanel(), java.awt.BorderLayout.NORTH);
    // source1 = updateManualEnhancement(source1);
    // }
    // else if (index == 2) {
    // source1 = normalizeAllTypeOfImage(source1, imageFactoryView.getArea());
    // }
    // else if (index == 3) {
    // source1 = equalize(source1);
    // }
    // else if (index == 4) {
    // imageFactoryView.getJPanelParam().add(jPanel1, java.awt.BorderLayout.NORTH);
    // imageFactoryView.setSource2ImgDisplay(true);
    // source1 = BackgroundSubstraction(source1, source2, jSlider1.getValue());
    // }
    //
    // return Zone.applyZoneToImage(imageFactoryView.getArea(), source1);
    // }

    public static PlanarImage backgroundSubstraction(PlanarImage image, PlanarImage background, int offset) {
        ParameterBlock pb = new ParameterBlock();
        pb.addSource(image);
        pb.addSource(background);
        pb.add(offset);
        return JAI.create("BackgroundSubstract", pb, null); //$NON-NLS-1$
    }

    public static int[] getHistogram(PlanarImage image) {
        // set up the histogram
        int[] bins = { 256 };
        double[] low = { 0.0D };
        double[] high = { 256.0D };

        ParameterBlock pb = new ParameterBlock();
        pb.addSource(image);
        // pb.add(null);
        pb.add(1);
        pb.add(1);
        pb.add(bins);
        pb.add(low);
        pb.add(high);

        RenderedOp op = JAI.create("histogram", pb, null); //$NON-NLS-1$
        Histogram histogram = (Histogram) op.getProperty("histogram"); //$NON-NLS-1$

        // get histogram contents
        int[] local_array = new int[histogram.getNumBins(0)];
        for (int i = 0; i < histogram.getNumBins(0); i++) {
            local_array[i] = histogram.getBinSize(0, i);
        }
        return local_array;
    }

    // one way to do this (old style)
    // this could also be done with matchcdf
    public PlanarImage equalize(PlanarImage image) {
        int sum = 0;
        byte[] cumulative = new byte[256];
        int array[] = getHistogram(image);

        float scale = 255.0F / (image.getWidth() * image.getHeight());

        for (int i = 0; i < 256; i++) {
            sum += array[i];
            cumulative[i] = (byte) ((sum * scale) + .5F);
        }

        LookupTableJAI lookup = new LookupTableJAI(cumulative);
        ParameterBlock pb = new ParameterBlock();
        pb.addSource(image);
        pb.add(lookup);

        return JAI.create("lookup", pb, null); //$NON-NLS-1$
    }

    // for a single band
    public static PlanarImage normalizeAllTypeOfImage(PlanarImage image) {
        ParameterBlock pb = new ParameterBlock();
        pb.addSource(image);
        PlanarImage dst = JAI.create("extrema", pb, null); //$NON-NLS-1$
        double[][] extrema = (double[][]) dst.getProperty("extrema"); //$NON-NLS-1$

        int numBands = dst.getSampleModel().getNumBands();
        double[] slopes = new double[numBands];
        double[] y_ints = new double[numBands];
        // find the overall min, max (all bands)
        for (int i = 0; i < numBands; i++) {
            double range = extrema[1][i] - extrema[0][i];
            if (range < 1.0) {
                range = 1.0;
            }
            slopes[i] = 255.0D / range;
            y_ints[i] = 255.0D - slopes[i] * extrema[1][i];
        }

        // rescale from xxx to byte range
        pb = new ParameterBlock();
        pb.addSource(image);
        pb.add(slopes);
        pb.add(y_ints);
        dst = JAI.create("rescale", pb, null); //$NON-NLS-1$

        // produce a byte image
        pb = new ParameterBlock();
        pb.addSource(dst);
        pb.add(DataBuffer.TYPE_BYTE);
        return JAI.create("format", pb, null); //$NON-NLS-1$
    }

    public PlanarImage piecewise(PlanarImage image) {
        float[][][] bp = new float[1][2][];
        bp[0][0] = new float[] { 0.0F, 32.0F, 64.0F, 255.0F };
        bp[0][1] = new float[] { 0.0F, 128.0F, 112.0F, 255.0F };

        return JAI.create("piecewise", image, bp); //$NON-NLS-1$
    }

    private PlanarImage updateManualEnhancement(PlanarImage image) {
        return getContrastPanel().updateSlider(image);
    }

    // public void jButtonArtificial_actionPerformed(ActionEvent e) {
    // if (!(imageFactoryView.getCurrentDialog() instanceof ArtificialBackgroundProcessDialog)) {
    // imageFactoryView.openDialog(new ArtificialBackgroundProcessDialog(imageFactoryView));
    // }
    // }
    //
    // private class Process extends JmvThread {
    //
    // private int operation;
    // private PlanarImage source1;
    //
    // public Process(int operation, PlanarImage source1) {
    // super(histoLabels[operation] + "...", true, false);
    // this.operation = operation;
    // this.source1 = source1;
    // }
    //
    // public void doProcessing() {
    // PlanarImage curOp = null;
    // getStatusBar().setUnknownProgress(true);
    // if (operation == 1) {
    // imageFactoryView.getJPanelParam().add(getContrastPanel(), java.awt.BorderLayout.NORTH);
    // curOp = updateManualEnhancement(source1);
    // }
    // else if (operation == 2) {
    // curOp = normalizeAllTypeOfImage(source1, imageFactoryView.getRealAreaForWholeImg());
    // }
    // else if (operation == 3) {
    // curOp = equalize(source1);
    // }
    // curOp = Zone.applyZoneToImage(imageFactoryView.getArea(), curOp);
    // try {
    // curOp = imageFactoryView.getImageFrame().getImageCanvas().displayProcessedImage(curOp, this, null);
    // }
    // catch (Exception ex) {
    // curOp = null;
    // imageFactoryView.enableAnotherOperation(true);
    // throw new RuntimeException();
    // }
    // getStatusBar().setUnknownProgress(false);
    // imageFactoryView.enableAnotherOperation(true);
    // imageFactoryView.setCurImgOp(curOp);
    // getStatusBar().setProgress(100);
    // }
    // }

}
