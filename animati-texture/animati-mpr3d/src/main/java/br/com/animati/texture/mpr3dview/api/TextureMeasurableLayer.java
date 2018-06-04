/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda. (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview.api;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.nio.ByteBuffer;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.vecmath.Vector2d;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.Graphic;

import br.com.animati.texture.mpr3dview.View3DFactory;
import br.com.animati.texture.mpr3dview.ViewTexture;
import br.com.animati.texturedicom.TextureData;
import br.com.animati.texturedicom.rendering.RenderHelper;
import br.com.animati.texturedicom.rendering.RenderResult;
import br.com.animati.texturedicom.rendering.RenderResultListener;
import org.weasis.dicom.codec.TagD;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2015, 06 May.
 */
public class TextureMeasurableLayer implements MeasurableLayer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ViewTexture.class);

    private final ViewTexture owner;

    private RenderedImage renderedAsSource;
    private volatile boolean dirty = true;

    //Just for debug
    private BufferedImage bufferedImage;
    private volatile boolean bufferedDirty;

    /**
     * Creates a measurable layer for the given view.
     *
     * @param parent Parent view.
     */
    public TextureMeasurableLayer(final ViewTexture parent) {
        owner = parent;
    }

    @Override
    public boolean hasContent() {
        return owner.hasContent();
    }

    /**
     * @param dirty
     *            the dirty to set
     */
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
        bufferedDirty = dirty;
    }

    @Override
    public MeasurementsAdapter getMeasurementAdapter(Unit displayUnit) {
        // TODO: support other displayUnits
        if (hasContent()) {
            String abr = owner.getSeriesObject().getPixelSpacingUnit().getAbbreviation();
            double[] pixelSpacing = owner.getSeriesObject().getAcquisitionPixelSpacing();
            double pixelSize = 1;
            if (pixelSpacing != null) {
                pixelSize = pixelSpacing[0];
            }
            return new MeasurementsAdapter(pixelSize, 0, 0, false, 0, abr);
        }
        return null;
    }

    @Override
    public AffineTransform getShapeTransform() {
        // Only scale !
        if (hasContent()) {
            double scale = owner.getActualDisplayZoom();
            return AffineTransform.getScaleInstance(scale, scale);
        }
        return null;
    }

    @Override
    public Object getSourceTagValue(final TagW tagW) {
        if (hasContent()) {
            Object tagValue = owner.getSeriesObject().getTagValue(tagW);
            // Special case RescaleIntercept (#3743)
            if (TagD.get(Tag.RescaleIntercept).equals(tagW)) {
                if (TextureData.Format.UnsignedShort.equals(owner.getSeriesObject().getTextureData().getFormat())
                        && tagValue != null) {
                    return tagValue;
                } else {
                    return 0.0d;
                }
            }
            if (tagValue == null) {
                tagValue = owner.getSeriesObject().getTagValue(tagW, 0);
            }
            return tagValue;
        }
        return null;
    }

    @Override
    public String getPixelValueUnit() {
        if (hasContent()) {
            return owner.getSeriesObject().getPixelValueUnit();
        }
        return null;
    }

    @Override
    public PlanarImage getSourceRenderedImage() {
        if (dirty) {
            startRendering(true);
        }

        if (View3DFactory.showMeasurementsOnFrame) {
            GuiExecutor.instance().execute(new Runnable() {
                @Override
                public void run() {
                    if (bufferedDirty) {
                        startRendering(false);
                    } else {
                        List<Graphic> list = owner.getGraphicManager().getAllGraphics();
                        if (bufferedImage != null) {
                            showOnFrame(deepCopy(bufferedImage), getShapeTransform(), list);
                        }
                    }
                }
            });
        }

        if (renderedAsSource == null) {
            return null;
        }
        return ImageConversion.toMat(renderedAsSource);
    }

    /**
     * Used for DEBUG.
     *
     * @param image Image to show.
     * @param transform Transform to print graphics layer.
     * @param mShape List of graphics.
     */
    public void showOnFrame(final BufferedImage image, final AffineTransform transform, final List<Graphic> mShape) {

        Graphics2D graphics = (Graphics2D) image.createGraphics();
        graphics.setStroke(new BasicStroke(2));
        graphics.setPaint(Color.yellow);
        for (Graphic shape : mShape) {
            graphics.draw(transform.createTransformedShape(shape.getShape()));
        }

        graphics.setPaint(Color.cyan);
        graphics.draw(transform.createTransformedShape(owner.getViewModel().getModelArea()));

        JPanel display = new JPanel() {
            @Override
            public void paintComponent(final Graphics g) {
                ((Graphics2D) g).drawImage(image, null, null);
            }
        };
        display.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));

        JFrame frame = new JFrame();
        frame.add(display);
        frame.setVisible(true);
        frame.pack();

    }

    /**
     * Makes a copy of a Buffered image.
     *
     * @param bi Image to copy.
     * @return Copy of image.
     */
    public static BufferedImage deepCopy(final BufferedImage bi) {
        ColorModel cm = bi.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = bi.copyData(bi.getRaster().createCompatibleWritableRaster());
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
    }

    private void startRendering(final boolean renderAsRaw) {
        final TextureData.Format format = owner.getParentImageSeries().getTextureData().getFormat();

        // Img needs to correspond to the scaled modelArea.
        Rectangle2D modelArea = owner.getViewModel().getModelArea();
        double scale = owner.getActualDisplayZoom();
        Rectangle2D scaledMA =
            new Rectangle2D.Double(0, 0, modelArea.getWidth() * scale, modelArea.getHeight() * scale);
        Rectangle bounds = scaledMA.getBounds();

        // Has to be final
        final Rectangle imgBounds = bounds;

        RenderHelper helper = new RenderHelper(owner, new RenderResultListener() {
            @Override
            public void onRenderResultReceived(final RenderResult renderResult) {
                try {
                    if (renderAsRaw) {
                        ByteBuffer asByteBuffer = renderResult.asByteBuffer();
                        if (TextureData.Format.Byte.equals(format)) {
                            renderedAsSource = RenderSupport.make8BitsImage(asByteBuffer, imgBounds);
                        } else {
                            renderedAsSource = RenderSupport.makeBufferedImage(asByteBuffer, imgBounds);
                        }
                        dirty = false;

                    } else {
                        BufferedImage asBuff = renderResult.asBufferedImage();
                        bufferedImage = asBuff;

                        bufferedDirty = false;
                    }

                    if (renderAsRaw) {
                        updateGraphics();
                    }

                } catch (Exception ex) {
                    LOGGER.error("Error when receiving rendered result from texture. ", ex);
                }

            }
        }, renderAsRaw);

        helper.getParametersCanvas().setBounds(bounds);

        helper.getParametersCanvas().setImageOffset(new Vector2d(0, 0));
        helper.getParametersCanvas().setRotationOffset(0);
        helper.getParametersCanvas().flippedHorizontally = false;
        helper.getParametersCanvas().flippedVertically = false;
        helper.renderFrame();
    }

    private void updateGraphics() {
        GuiExecutor.instance().execute(new Runnable() {
            @Override
            public void run() {
                List<Graphic> list = owner.getGraphicManager().getAllGraphics();
                for (Graphic graphic : list) {
                    graphic.updateLabel(true, owner);
                }
            }
        });
    }

    @Override
    public Point getOffset() {
        return null; // Offset for measurement serialization: not used here.
    }

    @Override
    public void setOffset(final Point p) {
        // Offset for measurement serialization: not used here.
    }

}
