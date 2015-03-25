/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview.api.graghics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.media.jai.OpImage;
import javax.media.jai.ROIShape;
import javax.media.jai.RenderedOp;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.op.ImageStatistics2Descriptor;
import org.weasis.core.api.image.op.ImageStatisticsDescriptor;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.graphic.GraphicLabel;
import org.weasis.core.ui.graphic.MeasureItem;
import org.weasis.core.ui.graphic.Measurement;
import org.weasis.core.ui.graphic.RectangleGraphic;

import br.com.animati.texture.codec.TextureDicomSeries;
import br.com.animati.texture.mpr3dview.EventPublisher;
import br.com.animati.texture.mpr3dview.ViewTexture;
import br.com.animati.texture.mpr3dview.api.RenderSupport;
import br.com.animati.texture.mpr3dview.api.ViewCore;
import br.com.animati.texturedicom.TextureData;
import br.com.animati.texturedicom.TextureImageCanvas;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 9 Dec
 */
public class DVRectangleGraphic extends RectangleGraphic {
    
    public DVRectangleGraphic(float lineThickness, Color paintColor,
            boolean labelVisible) {
        super(lineThickness, paintColor, labelVisible); 
    }
    
    /** Needed for GraphicsMode.updateAllLabels. */
    @Override
    public void updateLabel(Object source, ViewCanvas view2d, Point2D pos) {      
        if (source instanceof MouseEvent) {
            updateLabelDV((MouseEvent) source, null);
        }
    }
        
    public void updateLabelDV(MouseEvent source, Point2D pos) {
        List<MeasureItem> measList = null;
        String[] labels = null;
        
        if (labelVisible || DVLineGraphic.isMultiSelection(source)) {
            measList = computeMeasurements(DVLineGraphic.getAdapter(source));
            if (computePixelStats(source)) {
                ImageLayer imageLayer = getImageLayer(source);
                if (imageLayer != null) {
                    List<MeasureItem> imageStatistics = getImageStatistics(
                            imageLayer, source.getID() == MouseEvent.MOUSE_RELEASED);
                    if (imageStatistics != null) {
                        measList.addAll(imageStatistics);
                    }
                } else {
                    if (isShapeValid()) {
                        final ViewTexture canvas = 
                                getTextureCanvas(source);
                        List<MeasureItem> imageStatistics =
                                getStatisticsFromTexture(canvas,
                                shape, new ActionListener() {

                                    @Override
                                    public void actionPerformed(ActionEvent e) {
                                        updateLabelDV(new MouseEvent(
                                                canvas, MouseEvent.MOUSE_RELEASED,
                                                0, 0, 0, 0, 0, false), null);
                                    }
                                });
                        if (imageStatistics != null) {
                            measList.addAll(imageStatistics);
                        }
                    }
                }
            }
        }
        
        if (labelVisible && measList != null && measList.size() > 0) {
            labels = DVGraphicLabel.buildLabelsList(measList);
            EventPublisher.getInstance().publish(new PropertyChangeEvent(
                    this, "graphics.data", null, measList));
        }

        setLabel(labels, (Graphics2D) DVLineGraphic.getGraphics(source), pos);
        
    }
    
    public List<MeasureItem> computeMeasurements(MeasurementsAdapter adapter) {

        if (isShapeValid() && adapter != null) {
            ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();
            Rectangle2D rect = new Rectangle2D.Double();
            
            //eHandlePoint nao eh acessivel daqui:
            //NW(0), SE(1), NE(2), SW(3), N(4), S(5), E(6), W(7);
            rect.setFrameFromDiagonal(getHandlePoint(0), getHandlePoint(1));

            double ratio = adapter.getCalibRatio();

            if (TOP_LEFT_POINT_X.isComputed()) {
                measVal.add(new MeasureItem(TOP_LEFT_POINT_X, adapter.getXCalibratedValue(rect.getX()), adapter
                        .getUnit()));
            }
            if (TOP_LEFT_POINT_Y.isComputed()) {
                measVal.add(new MeasureItem(TOP_LEFT_POINT_Y, adapter.getYCalibratedValue(rect.getY()), adapter
                        .getUnit()));
            }
            if (CENTER_X.isComputed()) {
                measVal.add(new MeasureItem(CENTER_X, adapter.getXCalibratedValue(rect.getCenterX()), adapter
                        .getUnit()));
            }
            if (CENTER_Y.isComputed()) {
                measVal.add(new MeasureItem(CENTER_Y, adapter.getYCalibratedValue(rect.getCenterY()), adapter
                        .getUnit()));
            }
            if (WIDTH.isComputed()) {
                measVal.add(new MeasureItem(WIDTH, ratio * rect.getWidth(), adapter.getUnit()));
            }
            if (HEIGHT.isComputed()) {
                measVal.add(new MeasureItem(HEIGHT, ratio * rect.getHeight(), adapter.getUnit()));
            }
            if (AREA.isComputed()) {
                Double val = rect.getWidth() * rect.getHeight() * ratio * ratio;
                String unit = "pix".equals(adapter.getUnit()) ? adapter.getUnit() : adapter.getUnit() + "2";
                measVal.add(new MeasureItem(AREA, val, unit));
            }
            if (PERIMETER.isComputed()) {
                Double val = (rect.getWidth() + rect.getHeight()) * 2 * ratio;
                measVal.add(new MeasureItem(PERIMETER, val, adapter.getUnit()));
            }
            return measVal;
        }
        return null;
    }
    
    public void setLabel(String[] labels, Graphics2D g2d, Point2D pos) {
        GraphicLabel oldLabel = (graphicLabel != null)
                ? ((DVGraphicLabel) graphicLabel).clone() : null;

        if (labels == null || labels.length == 0) {
            graphicLabel = null;
            fireLabelChanged(oldLabel);
        } else {
            if (pos == null) {
                pos = getPositionForLabel();
            }
            if (graphicLabel == null) {
                graphicLabel = new DVGraphicLabel();
            }
            ((DVGraphicLabel) graphicLabel).setLabel(g2d, pos.getX(), pos.getY(), labels);
            fireLabelChanged(oldLabel);
        }
    }
    
    protected Point2D getPositionForLabel() {
        if (shape != null) {
            Rectangle2D rect = shape.getBounds2D();

            double xPos = rect.getX() + rect.getWidth() + 3;
            double yPos = rect.getY() + rect.getHeight() * 0.5;

            return new Point2D.Double(xPos, yPos);
        }
        return null;
    }
    
    /**
     * Overriden so isOnGraphicLabel works woth all ViewCore
     * @param mouseevent
     * @return 
     */
    @Override
    protected AffineTransform getAffineTransform(MouseEvent mouseevent) {
        if (mouseevent != null && mouseevent.getSource() instanceof ViewCore) {
            return ((ViewCore) mouseevent.getSource()).getAffineTransform();
        }
        return null;
    }
    
    protected static List<MeasureItem> getStatisticsFromTexture(
            final ViewTexture canvas, Shape graphShape, ActionListener listener) {
        
        //posso ter um ImageElement, OK
                        
        //Uma rendered Image, ou nada ainda (Listener)
        //Mesmo que já tenha a rendered Image tem que pegar os parametros...
        //image, measShape, excludes,
        //        affineTransform, intercept, slope, unit, suv
        
        if (canvas != null) {
            List<MeasureItem> stats = new ArrayList<MeasureItem>();
            if (canvas.isShowingAcquisitionAxis() && (canvas.mipOption == null
                    || TextureImageCanvas.MipOption.None == canvas.mipOption)) {

                SeriesComparator seriesComparator =
                        (SeriesComparator) canvas.getActionValue(ActionW.SORTSTACK.cmd());
                Comparator sorter = seriesComparator;
                Boolean inverse = (Boolean) canvas.getActionValue(ActionW.INVERSESTACK.cmd());
                if (inverse) {
                    sorter = seriesComparator.getReversOrderComparator();
                }
                int currentSlice = canvas.getCurrentSlice();
                //System.out.println("currentSlice: " + currentSlice);

                MediaSeries series = canvas.getSeries();
                Object media = series.getMedia(currentSlice - 1, null, sorter);
                if (media instanceof ImageElement) {
                    List<MeasureItem> imageStatistics =
                            getImageStatistics((ImageElement) media, graphShape);
                    if (imageStatistics != null) {
                        stats.addAll(imageStatistics);
                    }
                }
            } else {
                //if has controlAxes and is using it; or its Mip
                RenderSupport rs = canvas.getRenderSupport();
                RenderedImage image = rs.getRenderedAsSource();
                if (image != null) {
                    List<MeasureItem> imageStatistics =
                            statisticsFromRendered(canvas, graphShape, image);
                    if (imageStatistics != null) {
                        stats.addAll(imageStatistics);
                    }
                } else {
                    //statr rendering
                    rs.startRendering(listener);
                }
            }
            return stats;
        }
        return null;
    }

    protected static ArrayList<MeasureItem> statisticsFromRendered(
            ViewTexture canvas, final Shape measShape, RenderedImage image) {
        
            final TextureDicomSeries seriesObject = canvas.getSeriesObject();

            //   --------Slope and intercept:
            //Para o modulo de MPR, o weasis assume que estes valores sao
            //constantes dentro da mesma serie, entao:
            Float slopeVal = (Float) seriesObject.getTagValue(TagW.RescaleSlope, 0);
            Float interceptVal = (Float) seriesObject.getTagValue(TagW.RescaleIntercept, 0);
            final double slope = slopeVal == null ? 1.0f : slopeVal.doubleValue();
            
            //Intercept eh sempre zero quando Signed porque a imagem que volta 
            //da placa de video nao tem intercept. Se for signed precisa
            //recolocar os valores negativos.
            double intercept = 0.0f;
            if (TextureData.Format.UnsignedShort.equals(seriesObject.getTextureData().getFormat())
                    && interceptVal != null){
                intercept = interceptVal;
            } 
            //final double intercept = interceptVal == null ? 0.0f : interceptVal.doubleValue();
            final String unit = seriesObject.getPixelValueUnit();
            final Double suv = (Double) seriesObject.getTagValue(TagW.SuvFactor, 0);
            // ______________________

            
            //_________Affine para posicionar e escalar shape
            //Necessario porque a imagem retornada pelo renderizador terah o
            //tamanho do canvas, nao da imagem original.
            double scale = canvas.getActualDisplayZoom();
            final AffineTransform affineTransform = AffineTransform.getScaleInstance(scale, scale);
            Rectangle imageRect = canvas.getUnrotatedImageRect();
            affineTransform.translate(imageRect.getX() / scale, imageRect.getY() / scale);
            //____________________
              
            
            //------------Excludes:
            //TODO: encontrar exemplos em que sejam variados!
             Double excludedMin = null;
            Double excludedMax = null;
            Integer paddingValue = (Integer) seriesObject.getTagValue(
                    TagW.PixelPaddingValue, 0);
             Integer paddingLimit = (Integer) seriesObject.getTagValue(
                    TagW.PixelPaddingRangeLimit, 0);
             
             if (paddingValue != null) {
                if (paddingLimit == null) {
                    paddingLimit = paddingValue;
                } else if (paddingLimit < paddingValue) {
                    int temp = paddingValue;
                    paddingValue = paddingLimit;
                    paddingLimit = temp;
                }
                excludedMin = new Double(paddingValue);
                excludedMax = paddingLimit == null ? null : new Double(paddingLimit);
            }
             final Double[] excludes = new Double[] {excludedMin, excludedMax};
             //System.out.println("Excluded * : " + excludedMin + ", " + excludedMax);
             ////// -----------------
             
            ArrayList<MeasureItem> imageStatistics =
                    getImageStatistics(image, measShape, excludes,
                    affineTransform, intercept, slope, unit, suv);

             return imageStatistics;
        
    }
    
    
    private static ArrayList<MeasureItem> getImageStatistics(RenderedImage image,
            Shape measureShape, Double[] excludes, AffineTransform affineTransform,
            double intercept, double slope, String unit, Double suv) {

         if (image != null) {
         ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();

            if (IMAGE_MIN.isComputed() || IMAGE_MAX.isComputed() || IMAGE_MEAN.isComputed()) {

                Double[] min = null;
                Double[] max = null;
                Double[] mean = null;
                Double[] stdv = null;
                Double[] skew = null;
                Double[] kurtosis = null;

                if (measureShape != null) {
                    ROIShape roi;
                    if (affineTransform != null) {
                        roi = new ROIShape(affineTransform.createTransformedShape(
                            measureShape));
                    } else {
                        roi = new ROIShape(measureShape);
                    }
                    
                    Double excludedMin = excludes[0];
                    Double excludedMax = excludes[1];

                    RenderedOp dst =
                            ImageStatisticsDescriptor.create(
                            image, roi, 1, 1, excludedMin, excludedMax, null);
                    
                    // To ensure this image is not stored in tile cache
                    ((OpImage) dst.getRendering()).setTileCache(null);
                    
                    // For basic statistics, rescale values can be computed afterwards
                    double[][] extrema = (double[][]) dst.getProperty("statistics");
                    if (extrema == null || extrema.length < 1 || extrema[0].length < 1) {
                        return null;
                    }
                    min = new Double[extrema[0].length];
                    max = new Double[extrema[0].length];
                    mean = new Double[extrema[0].length];
                    
                    //System.out.println("Slope/Intercept * : " + slope + ", " + intercept);
                    for (int i = 0; i < extrema[0].length; i++) {
                        min[i] = extrema[0][i] * slope + intercept;
                        max[i] = extrema[1][i] * slope + intercept;
                        mean[i] = extrema[2][i] * slope + intercept;
                    }
                    //System.out.println("Extrema *: min = " + extrema[0][0] + " max = " + extrema[1][0]);
                    if (IMAGE_STD.isComputed() || IMAGE_SKEW.isComputed() || IMAGE_KURTOSIS.isComputed()) {
                        // startTime = System.currentTimeMillis();
                        // Required the mean value (not rescaled), slope and intercept to calculate correctly std,
                        // skew and kurtosis
                        dst =
                            ImageStatistics2Descriptor.create(image, roi, 1, 1, extrema[2][0], excludedMin,
                                excludedMax, slope, intercept, null);
                        // To ensure this image is not stored in tile cache
                        ((OpImage) dst.getRendering()).setTileCache(null);
                        double[][] extrema2 = (double[][]) dst.getProperty("statistics");
                        if (extrema != null && extrema.length > 0 && extrema[0].length > 0) {
                            stdv = new Double[extrema2[0].length];
                            skew = new Double[extrema2[0].length];
                            kurtosis = new Double[extrema2[0].length];

                            for (int i = 0; i < extrema2[0].length; i++) {
                                stdv[i] = extrema2[0][i];
                                skew[i] = extrema2[1][i];
                                kurtosis[i] = extrema2[2][i];
                            }
                        }
                    }
                    
                }
                
                
                if (IMAGE_MIN.isComputed()) {
                    addMeasure(measVal, IMAGE_MIN, min, unit);
                }
                if (IMAGE_MAX.isComputed()) {
                    addMeasure(measVal, IMAGE_MAX, max, unit);
                }
                if (IMAGE_MEAN.isComputed()) {
                    addMeasure(measVal, IMAGE_MEAN, mean, unit);
                }

                if (IMAGE_STD.isComputed()) {
                    addMeasure(measVal, IMAGE_STD, stdv, unit);
                }
                if (IMAGE_SKEW.isComputed()) {
                    addMeasure(measVal, IMAGE_SKEW, skew, unit);
                }
                if (IMAGE_KURTOSIS.isComputed()) {
                    addMeasure(measVal, IMAGE_KURTOSIS, kurtosis, unit);
                }
                
                if (suv != null) {
                    unit = "SUVbw";
                    if (IMAGE_MIN.isComputed()) {
                        measVal.add(new MeasureItem(IMAGE_MIN, min == null
                                || min[0] == null ? null : min[0] * suv,
                                unit));
                    }
                    if (IMAGE_MAX.isComputed()) {
                        measVal.add(new MeasureItem(IMAGE_MAX, max == null
                                || max[0] == null ? null : max[0] * suv,
                                unit));
                    }
                    if (IMAGE_MEAN.isComputed()) {
                        measVal.add(new MeasureItem(IMAGE_MEAN, mean == null
                                || mean[0] == null ? null : mean[0]
                                * suv, unit));
                    }
                }
                
            }
            return measVal;
         }
         return null;
    }

    protected static ViewTexture getTextureCanvas(MouseEvent mouseevent) {
        if (mouseevent != null && mouseevent.getSource() instanceof ViewTexture
                && mouseevent.getID() == MouseEvent.MOUSE_RELEASED) {
             return (ViewTexture) mouseevent.getSource();
        }
        return null;
    }
    
    public static List<MeasureItem> getImageStatistics(ImageElement imageElement,
            Shape graphShape) {
        
        if (imageElement != null && graphShape != null) {
            RenderedImage image = imageElement.getImage(); //TODO: preprocessing? see RenderedImageLayer::94
            
            //As it is not comming from texture, may have diff X and Y scales
            double scaleX = imageElement.getRescaleX();
            double scaleY = imageElement.getRescaleY();
            AffineTransform transform = null;
            if (scaleX != scaleY) {
                transform = AffineTransform.getScaleInstance(1.0 / scaleX, 1.0 / scaleY);
            }
            
            Double excludedMin = null;
            Double excludedMax = null;
            Integer paddingValue = (Integer) imageElement.getTagValue(TagW.PixelPaddingValue);
            Integer paddingLimit = (Integer) imageElement.getTagValue(TagW.PixelPaddingRangeLimit);

            if (paddingValue != null) {
                if (paddingLimit == null) {
                    paddingLimit = paddingValue;
                } else if (paddingLimit < paddingValue) {
                    int temp = paddingValue;
                    paddingValue = paddingLimit;
                    paddingLimit = temp;
                }
                excludedMin = new Double(paddingValue);
                excludedMax = paddingLimit == null ? null : new Double(paddingLimit);
            }
            final Double[] excludes = new Double[] {excludedMin, excludedMax};
            //System.out.println("Excluded * : " + excludedMin + ", " + excludedMax);
            
            Float slopeVal = (Float) imageElement.getTagValue(TagW.RescaleSlope);
            Float interceptVal = (Float) imageElement.getTagValue(TagW.RescaleIntercept);
            double slope = slopeVal == null ? 1.0f : slopeVal.doubleValue();
            double intercept = interceptVal == null ? 0.0f : interceptVal.doubleValue();
            
            String unit = imageElement.getPixelValueUnit();
            Double suv = (Double) imageElement.getTagValue(TagW.SuvFactor);
            
            return getImageStatistics(image, graphShape, excludes, transform,
                    intercept, slope, unit, suv);
        }
        return null;
    }
    
    private static void addMeasure(ArrayList<MeasureItem> measVal, Measurement measure, Double[] val, String unit) {
        if (val == null) {
            measVal.add(new MeasureItem(measure, null, unit));
        } else if (val.length == 1) {
            measVal.add(new MeasureItem(measure, val[0], unit));
        } else {
            for (int i = 0; i < val.length; i++) {
                measVal.add(new MeasureItem(measure, " " + (i + 1), val[i], unit));
            }
        }
    }
    

    public static boolean computePixelStats(MouseEvent source) {
        if (source != null) {
            if (source.getSource() instanceof ViewTexture) {
                return ViewTexture.computePixelStats;
            }
        }
        return false;
    }
    
    public static void showOnFrame(final BufferedImage image,
            AffineTransform transform, Shape mShape) {
        
        //Just for debug:
        Graphics2D graphics = image.createGraphics();
        graphics.setStroke(new BasicStroke(2));
        graphics.setPaint(Color.yellow);
        graphics.draw(transform.createTransformedShape(mShape));
   
        //For debug
        JPanel display = new JPanel() {
            @Override
            public void paintComponent(Graphics g) {
                ((Graphics2D) g).drawImage(image, null, null);
            }
        };
        display.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));

        JFrame frame = new JFrame();
        frame.add(display);
        frame.setVisible(true);
        frame.pack();
    }

}
