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
package org.weasis.core.ui.editor.image;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.RenderedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.media.jai.PlanarImage;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.KeyStroke;
import javax.swing.ToolTipManager;
import javax.swing.TransferHandler;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;

import org.weasis.core.api.gui.Image2DViewer;
import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.FilterOperation;
import org.weasis.core.api.image.FlipOperation;
import org.weasis.core.api.image.OperationsManager;
import org.weasis.core.api.image.PseudoColorOperation;
import org.weasis.core.api.image.RotationOperation;
import org.weasis.core.api.image.WindowLevelOperation;
import org.weasis.core.api.image.ZoomOperation;
import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.image.util.KernelData;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.graphic.AbstractDragGraphic;
import org.weasis.core.ui.graphic.DragLayer;
import org.weasis.core.ui.graphic.DragPoint;
import org.weasis.core.ui.graphic.DragPoint.STATE;
import org.weasis.core.ui.graphic.DragSequence;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.ImageLayerChangeListener;
import org.weasis.core.ui.graphic.RenderedImageLayer;
import org.weasis.core.ui.graphic.SelectGraphic;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.graphic.model.DefaultViewModel;
import org.weasis.core.ui.graphic.model.GraphicList;
import org.weasis.core.ui.graphic.model.GraphicsPane;
import org.weasis.core.ui.graphic.model.Tools;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * @author Nicolas Roduit,Benoit Jacquemoud
 */
public abstract class DefaultView2d<E extends ImageElement> extends GraphicsPane implements PropertyChangeListener,
    FocusListener, Image2DViewer, ImageLayerChangeListener, KeyListener {

    protected final FocusHandler focusHandler = new FocusHandler();
    protected final MouseHandler mouseClickHandler = new MouseHandler();

    protected static final Shape[] pointer;
    static {
        pointer = new Shape[5];
        pointer[0] = new Ellipse2D.Double(-27.0, -27.0, 54.0, 54.0);
        pointer[1] = new Line2D.Double(-40.0, 0.0, -5.0, 0.0);
        pointer[2] = new Line2D.Double(5.0, 0.0, 40.0, 0.0);
        pointer[3] = new Line2D.Double(0.0, -40.0, 0.0, -5.0);
        pointer[4] = new Line2D.Double(0.0, 5.0, 0.0, 40.0);
    }

    protected Point highlightedPosition = null;
    private int pointerType = 0;
    private final Color pointerColor1 = Color.black;
    private final Color pointerColor2 = Color.white;
    private final Border normalBorder = new EtchedBorder(BevelBorder.LOWERED, Color.gray, Color.white);
    private final Border focusBorder = new EtchedBorder(BevelBorder.LOWERED, focusColor, focusColor);

    protected int frameIndex;
    protected DragSequence ds;
    protected final RenderedImageLayer<E> imageLayer;
    protected ZoomWin<E> lens;

    protected MediaSeries<E> series = null;
    protected static final Color focusColor = Color.orange;
    protected AnnotationsLayer infoLayer;
    protected int tileOffset;

    protected final ImageViewerEventManager<E> eventManager;

    private final DragPoint startedDragPoint = new DragPoint(STATE.Started);

    public DefaultView2d(ImageViewerEventManager<E> eventManager) {
        this(eventManager, null, null);
    }

    public DefaultView2d(ImageViewerEventManager<E> eventManager, AbstractLayerModel layerModel, ViewModel viewModel) {
        super(layerModel, viewModel);
        if (eventManager == null)
            throw new IllegalArgumentException("EventManager cannot be null"); //$NON-NLS-1$
        this.eventManager = eventManager;
        tileOffset = 0;
        initActionWState();

        imageLayer = new RenderedImageLayer<E>(new OperationsManager(this), true);
        // infoLayer = new InfoLayer(this);

        setBorder(normalBorder);
        setFocusable(true);
        setPreferredSize(new Dimension(1024, 1024));
    }

    public void registerDefaultListeners() {
        addFocusListener(this);
        ToolTipManager.sharedInstance().registerComponent(this);
        imageLayer.addLayerChangeListener(this);
    }

    public void copyActionWState(HashMap<String, Object> actionsInView) {
        actionsInView.putAll(this.actionsInView);
    }

    protected void initActionWState() {
        actionsInView.put(ActionW.ZOOM.cmd(), 0.0);
        actionsInView.put(ActionW.LENS.cmd(), false);
        actionsInView.put(ActionW.ROTATION.cmd(), 0);
        actionsInView.put(ActionW.FLIP.cmd(), false);
        actionsInView.put(ActionW.INVERSELUT.cmd(), false);
        actionsInView.put(ActionW.LUT.cmd(), ByteLut.defaultLUT);
        actionsInView.put(ActionW.INVERSESTACK.cmd(), false);
        actionsInView.put(ActionW.FILTER.cmd(), KernelData.NONE);
        actionsInView.put(ActionW.DRAW.cmd(), true);
        actionsInView.put(ZoomOperation.INTERPOLATION_CMD, eventManager.getZoomSetting().getInterpolation());
    }

    public ImageViewerEventManager<E> getEventManager() {
        return eventManager;
    }

    public String getPixelInfo(Point p, RenderedImageLayer<E> imageLayer) {
        ImageElement imageElement = imageLayer.getSourceImage();
        StringBuffer message = new StringBuffer();
        if (imageElement != null && imageLayer.getReadIterator() != null) {

            PlanarImage image = imageElement.getImage();
            Point realPoint =
                new Point((int) Math.ceil(p.x / imageElement.getRescaleX() - 0.5), (int) Math.ceil(p.y
                    / imageElement.getRescaleY() - 0.5));
            if (image != null && realPoint.x >= 0 && realPoint.y >= 0 && realPoint.x < image.getWidth()
                && realPoint.y < image.getHeight()) {
                try {
                    int[] c = { 0, 0, 0 };
                    imageLayer.getReadIterator().getPixel(realPoint.x, realPoint.y, c); // read the pixel

                    if (image.getSampleModel().getNumBands() == 1) {
                        message.append(c[0]);
                    } else {
                        message.append("R=" + c[0] + " G=" + c[1] + " B=" + c[2]); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    }
                    message.append(" - (" + p.x + "," + p.y + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                } catch (ArrayIndexOutOfBoundsException ex) {
                }
            } else {
                message.append(Messages.getString("DefaultView2d.out")); //$NON-NLS-1$
            }
        }
        return message.toString();
    }

    protected static class BulkDragSequence implements DragSequence {
        private final List<DragSequence> childDS;

        BulkDragSequence(List<AbstractDragGraphic> dragGraphList, MouseEventDouble mouseevent) {
            childDS = new ArrayList<DragSequence>(dragGraphList.size());

            for (AbstractDragGraphic dragGraph : dragGraphList) {
                DragSequence dragsequence = dragGraph.createMoveDrag();
                if (dragsequence != null) {
                    childDS.add(dragsequence);
                }
            }
        }

        @Override
        public void startDrag(MouseEventDouble mouseevent) {
            int i = 0;
            for (int j = childDS.size(); i < j; i++) {
                (childDS.get(i)).startDrag(mouseevent);
            }
        }

        @Override
        public void drag(MouseEventDouble mouseevent) {
            int i = 0;
            for (int j = childDS.size(); i < j; i++) {
                (childDS.get(i)).drag(mouseevent);
            }
        }

        @Override
        public boolean completeDrag(MouseEventDouble mouseevent) {
            int i = 0;
            for (int j = childDS.size(); i < j; i++) {
                (childDS.get(i)).completeDrag(mouseevent);
            }
            return true;
        }

    }

    protected void closeLens() {
        if (lens != null) {
            lens.showLens(false);
            this.remove(lens);
            actionsInView.put(ActionW.LENS.cmd(), false);
        }
    }

    public void setSeries(MediaSeries<E> series) {
        setSeries(series, -1);
    }

    public void setSeries(MediaSeries<E> series, int defaultIndex) {
        MediaSeries<E> oldsequence = this.series;
        this.series = series;
        if (oldsequence != null && oldsequence != series) {
            closingSeries(oldsequence);
        }
        if (series == null) {
            imageLayer.setImage(null);
            getLayerModel().deleteAllGraphics();
            closeLens();
        } else {
            defaultIndex = defaultIndex < 0 || defaultIndex >= series.size() ? 0 : defaultIndex;
            frameIndex = defaultIndex + tileOffset;
            setImage(series.getMedia(frameIndex), true);
            Double val = (Double) actionsInView.get(ActionW.ZOOM.cmd());
            zoom(val == null ? 1.0 : val);
            center();
        }
        // EventManager.getInstance().updateComponentsListener(this);

        // Set the sequence to the state OPEN
        if (series != null && oldsequence != series) {
            series.setOpen(true);
        }
    }

    protected void closingSeries(MediaSeries<E> series) {
        if (series == null)
            return;
        boolean open = false;
        synchronized (UIManager.VIEWER_PLUGINS) {
            List<ViewerPlugin> plugins = UIManager.VIEWER_PLUGINS;
            pluginList: for (final ViewerPlugin plugin : plugins) {
                List<MediaSeries> openSeries = plugin.getOpenSeries();
                if (openSeries != null) {
                    for (MediaSeries s : openSeries) {
                        if (series == s) {
                            // The sequence is still open in another view or plugin
                            open = true;
                            break pluginList;
                        }
                    }
                }
            }
        }
        series.setOpen(open);
        series.setSelected(false, 0);
    }

    private int getImageSize(E img, TagW tag1, TagW tag2) {
        Integer size = (Integer) img.getTagValue(tag1);
        if (size == null) {
            size = (Integer) img.getTagValue(tag2);
        }
        return (size == null) ? ImageFiler.TILESIZE : size;
    }

    protected void setImage(E img, boolean bestFit) {
        E oldImage = imageLayer.getSourceImage();
        if (img != null && !img.equals(oldImage)) {

            RenderedImage source = img.getImage();
            int width =
                source == null || img.getRescaleX() != img.getRescaleY() ? img.getRescaleWidth(getImageSize(img,
                    TagW.ImageWidth, TagW.Columns)) : source.getWidth();
            int height =
                source == null || img.getRescaleX() != img.getRescaleY() ? img.getRescaleHeight(getImageSize(img,
                    TagW.ImageHeight, TagW.Rows)) : source.getHeight();
            final Rectangle modelArea = new Rectangle(0, 0, width, height);
            DragLayer layer = getLayerModel().getMeasureLayer();
            synchronized (this) {
                GraphicList list = (GraphicList) img.getTagValue(TagW.MeasurementGraphics);
                if (list != null) {
                    // TODO handle graphics without shape, ecxlude them!
                    layer.setGraphics(list);
                } else {
                    GraphicList graphics = new GraphicList();
                    img.setTag(TagW.MeasurementGraphics, graphics);
                    layer.setGraphics(graphics);
                }
            }
            setWindowLevel(img);
            Rectangle2D area = getViewModel().getModelArea();
            if (!modelArea.equals(area)) {
                ((DefaultViewModel) getViewModel()).adjustMinViewScaleFromImage(modelArea.width, modelArea.height);
                getViewModel().setModelArea(modelArea);
                // setPreferredSize(modelArea.getSize());
                center();
            }
            if (bestFit) {
                actionsInView.put(ActionW.ZOOM.cmd(), -getBestFitViewScale());
            }
            imageLayer.setImage(img);
        }
    }

    @Override
    public double getBestFitViewScale() {
        double viewScale = super.getBestFitViewScale();
        ActionState zoom = eventManager.getAction(ActionW.ZOOM);
        if (zoom instanceof SliderChangeListener) {
            SliderChangeListener z = (SliderChangeListener) zoom;
            int sliderValue = eventManager.viewScaleToSliderValue(viewScale);
            // Set the value to the slider (the model can cut the value) and then get it again.
            if (eventManager.getSelectedViewPane() == this) {
                z.setValueWithoutTriggerAction(sliderValue);
                viewScale = eventManager.sliderValueToViewScale(z.getValue());

            } else {
                DefaultBoundedRangeModel model = z.getModel();
                if (sliderValue < model.getMinimum()) {
                    sliderValue = model.getMinimum();
                } else if (sliderValue > model.getMaximum()) {
                    sliderValue = model.getMaximum();
                }
                viewScale = eventManager.sliderValueToViewScale(sliderValue);
            }
        }
        return viewScale;
    }

    @Override
    public RenderedImageLayer<E> getImageLayer() {
        return imageLayer;
    }

    public AnnotationsLayer getInfoLayer() {
        return infoLayer;
    }

    public int getTileOffset() {
        return tileOffset;
    }

    public void setTileOffset(int tileOffset) {
        this.tileOffset = tileOffset;
    }

    @Override
    public MediaSeries<E> getSeries() {
        return series;
    }

    public int getCurrentImageIndex() {
        if (series instanceof Series)
            return ((Series) series).getImageIndex(imageLayer.getSourceImage());
        return 0;
    }

    @Override
    public E getImage() {
        return imageLayer.getSourceImage();
    }

    @Override
    public RenderedImage getSourceImage() {
        E image = getImage();
        if (image == null)
            return null;
        return image.getImage();
    }

    public final void center() {
        Rectangle2D bound = getViewModel().getModelArea();
        setCenter(bound.getWidth() / 2.0, bound.getHeight() / 2.0);
    }

    public final void setCenter(double x, double y) {
        double scale = getViewModel().getViewScale();
        setOrigin(x - (getWidth() - 1) / (2.0 * scale), y - (getHeight() - 1) / (2.0 * scale));
    }

    /** Provides panning */
    public final void setOrigin(double x, double y) {
        getViewModel().setModelOffset(x, y);
    }

    /** Provides panning */
    public final void moveOrigin(double x, double y) {
        setOrigin(getViewModel().getModelOffsetX() + x, getViewModel().getModelOffsetY() + y);
    }

    public final void moveOrigin(DragPoint point) {
        if (point != null) {
            if (DragPoint.STATE.Started.equals(point.getState())) {
                startedDragPoint.setLocation(getViewModel().getModelOffsetX(), getViewModel().getModelOffsetY());
            } else {
                setOrigin(startedDragPoint.getX() + point.getX(), startedDragPoint.getY() + point.getY());
            }
        }
    }

    @Override
    public Font getFont() {
        final Rectangle bound = getBounds();
        if (bound.height < 300 || bound.width < 300)
            return FontTools.getFont8();
        else if (bound.height < 500 || bound.width < 500)
            return FontTools.getFont10();
        else
            return FontTools.getFont12();
    }

    @Override
    public int getFrameIndex() {
        return frameIndex;
    }

    public void setActionsInView(String action, Object value) {
        if (action != null) {
            actionsInView.put(action, value);
            repaint();
        }
    }

    public void setSelected(boolean selected) {
        setBorder(selected ? focusBorder : normalBorder);
        // Remove the selection of graphics
        getLayerModel().setSelectedGraphics(null);
    }

    /** paint routine */
    @Override
    public synchronized void paintComponent(Graphics g) {
        if (g instanceof Graphics2D) {
            draw((Graphics2D) g);
        }
    }

    protected void draw(Graphics2D g2d) {
        Stroke oldStroke = g2d.getStroke();
        Paint oldColor = g2d.getPaint();
        double viewScale = getViewModel().getViewScale();
        double offsetX = getViewModel().getModelOffsetX() * viewScale;
        double offsetY = getViewModel().getModelOffsetY() * viewScale;
        // Paint the visible area
        g2d.translate(-offsetX, -offsetY);
        // Set font size for computing shared text areas that need to be repainted in different zoom magnitudes.
        Font defaultFont = eventManager.getViewSetting().getFont();
        g2d.setFont(defaultFont);

        imageLayer.drawImage(g2d);
        drawLayers(g2d, affineTransform, inverseTransform);

        g2d.translate(offsetX, offsetY);

        drawPointer(g2d);
        if (infoLayer != null) {
            // Set font size according to the view size
            g2d.setFont(getFont());
            infoLayer.paint(g2d);
        }
        g2d.setFont(defaultFont);
        g2d.setPaint(oldColor);
        g2d.setStroke(oldStroke);
    }

    @Override
    public void drawLayers(Graphics2D g2d, AffineTransform transform, AffineTransform inverseTransform) {
        if ((Boolean) actionsInView.get(ActionW.DRAW.cmd())) {
            getLayerModel().draw(g2d, transform, inverseTransform);
        }
    }

    @Override
    public void zoom(double viewScale) {
        if (viewScale == 0.0) {
            viewScale = -getBestFitViewScale();
        }
        actionsInView.put(ActionW.ZOOM.cmd(), viewScale);
        super.zoom(Math.abs(viewScale));
        imageLayer.updateImageOperation(ZoomOperation.name);
        updateAffineTransform();
    }

    protected void updateAffineTransform() {
        double viewScale = getViewModel().getViewScale();

        Boolean flip = (Boolean) actionsInView.get(ActionW.FLIP.cmd());
        if (flip != null && flip) {
            // Using only one allows to enable or disable flip with the rotation action

            // case FlipMode.TOP_BOTTOM:
            // at = new AffineTransform(new double[] {1.0,0.0,0.0,-1.0});
            // at.translate(0.0, -imageHt);
            // break;
            // case FlipMode.LEFT_RIGHT :
            // at = new AffineTransform(new double[] {-1.0,0.0,0.0,1.0});
            // at.translate(-imageWid, 0.0);
            // break;
            // case FlipMode.TOP_BOTTOM_LEFT_RIGHT:
            // at = new AffineTransform(new double[] {-1.0,0.0,0.0,-1.0});
            // at.translate(-imageWid, -imageHt);
            affineTransform.setToScale(-viewScale, viewScale);
            affineTransform.translate(-getViewModel().getModelArea().getWidth(), 0.0);
        } else {
            affineTransform.setToScale(viewScale, viewScale);
        }

        Integer rotationAngle = (Integer) actionsInView.get(ActionW.ROTATION.cmd());
        if (rotationAngle != null && rotationAngle > 0) {
            if (flip != null && flip) {
                rotationAngle = 360 - rotationAngle;
            }
            Rectangle2D imageCanvas = getViewModel().getModelArea();
            affineTransform.rotate(rotationAngle * Math.PI / 180.0, imageCanvas.getWidth() / 2.0,
                imageCanvas.getHeight() / 2.0);
        }
        try {
            inverseTransform.setTransform(affineTransform.createInverse());
        } catch (NoninvertibleTransformException e) {
            e.printStackTrace();
        }
    }

    public void setDrawingsVisibility(boolean visible) {
        if ((Boolean) actionsInView.get(ActionW.DRAW.cmd()) != visible) {
            actionsInView.put(ActionW.DRAW.cmd(), visible);
            repaint();
        }
    }

    protected void setWindowLevel(E img) {
        float min = img.getMinValue();
        float max = img.getMaxValue();
        actionsInView.put(ActionW.WINDOW.cmd(), max - min);
        actionsInView.put(ActionW.LEVEL.cmd(), (max - min) / 2.0f + min);
    }

    public Object getLensActionValue(String action) {
        if (lens == null)
            return null;
        return lens.getActionValue(action);
    }

    public void changeZoomInterpolation(int interpolation) {
        Integer val = (Integer) actionsInView.get(ZoomOperation.INTERPOLATION_CMD);
        boolean update = val == null || val != interpolation;
        if (update) {
            actionsInView.put(ZoomOperation.INTERPOLATION_CMD, interpolation);
            if (lens != null) {
                lens.setActionInView(ZoomOperation.INTERPOLATION_CMD, interpolation);
                lens.updateZoom();
            }
            imageLayer.updateImageOperation(ZoomOperation.name);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (series == null)
            return;
        final String command = evt.getPropertyName();
        if (command.equals(ActionW.SCROLL_SERIES.cmd())) {
            Object value = evt.getNewValue();
            AbstractLayer layer = getLayerModel().getLayer(Tools.CROSSLINES.getId());
            if (layer != null) {
                layer.deleteAllGraphic();
            }
            if (value instanceof Double) {
                double location = (Double) value;
                Boolean cutlines = (Boolean) actionsInView.get(ActionW.SYNCH_CROSSLINE.cmd());
                if (cutlines != null && cutlines) {
                    computeCrosslines(location);
                } else {
                    // TODO add a way in GUI to resynchronize series
                    Double offset = (Double) actionsInView.get(ActionW.STACK_OFFSET.cmd());
                    if (offset != null) {
                        location += offset;
                    }
                    frameIndex = series.getNearestIndex(location) + tileOffset;
                }
            } else {
                // TODO recieve Integer and crossline or synch are true
                frameIndex = (Integer) evt.getNewValue() + tileOffset;
            }
            Double val = (Double) actionsInView.get(ActionW.ZOOM.cmd());
            // If zoom has not been defined or was besfit, set image in bestfit zoom mode
            boolean rescaleView = (val == null || val <= 0.0);
            setImage(series.getMedia(frameIndex), rescaleView);
            if (rescaleView) {
                val = (Double) actionsInView.get(ActionW.ZOOM.cmd());
                zoom(val == null ? 1.0 : val);
                center();
            }
        } else if (command.equals(ActionW.WINDOW.cmd())) {
            actionsInView.put(ActionW.WINDOW.cmd(), ((Integer) evt.getNewValue()).floatValue());
            imageLayer.updateImageOperation(WindowLevelOperation.name);
        } else if (command.equals(ActionW.LEVEL.cmd())) {
            actionsInView.put(ActionW.LEVEL.cmd(), ((Integer) evt.getNewValue()).floatValue());
            imageLayer.updateImageOperation(WindowLevelOperation.name);
        } else if (command.equals(ActionW.ROTATION.cmd())) {
            actionsInView.put(ActionW.ROTATION.cmd(), evt.getNewValue());
            imageLayer.updateImageOperation(RotationOperation.name);
            updateAffineTransform();
        } else if (command.equals(ActionW.ZOOM.cmd())) {
            double zoomFactor = (Double) evt.getNewValue();
            zoom(zoomFactor);

        } else if (command.equals(ActionW.LENSZOOM.cmd())) {
            if (lens != null) {
                lens.setActionInView(ActionW.ZOOM.cmd(), evt.getNewValue());
            }

        } else if (command.equals(ActionW.LENS.cmd())) {
            Boolean showLens = (Boolean) evt.getNewValue();
            actionsInView.put(ActionW.LENS.cmd(), showLens);
            if (showLens) {
                if (lens == null) {
                    lens = new ZoomWin(this);
                }
                // resize if to big
                int maxWidth = getWidth() / 3;
                int maxHeight = getHeight() / 3;
                lens.setSize(lens.getWidth() > maxWidth ? maxWidth : lens.getWidth(), lens.getHeight() > maxHeight
                    ? maxHeight : lens.getHeight());
                this.add(lens);
                lens.showLens(true);

            } else {
                closeLens();
            }

        } else if (command.equals(ActionW.PAN.cmd())) {
            Object point = evt.getNewValue();
            // ImageViewerPlugin<E> view = eventManager.getSelectedView2dContainer();
            // if (view != null) {
            // if(!view.getSynchView().isActionEnable(ActionW.ROTATION)){
            //
            // }
            // }
            if (point instanceof DragPoint) {
                moveOrigin((DragPoint) evt.getNewValue());
            } else if (point instanceof Point) {
                Point p = (Point) point;
                moveOrigin(p.getX(), p.getY());
            }

        } else if (command.equals(ActionW.FLIP.cmd())) {
            actionsInView.put(ActionW.FLIP.cmd(), evt.getNewValue());
            imageLayer.updateImageOperation(FlipOperation.name);
            updateAffineTransform();
        } else if (command.equals(ActionW.LUT.cmd())) {
            actionsInView.put(ActionW.LUT.cmd(), evt.getNewValue());
            imageLayer.updateImageOperation(PseudoColorOperation.name);
        } else if (command.equals(ActionW.INVERSELUT.cmd())) {
            actionsInView.put(ActionW.INVERSELUT.cmd(), evt.getNewValue());
            imageLayer.updateImageOperation(PseudoColorOperation.name);
        } else if (command.equals(ActionW.FILTER.cmd())) {
            actionsInView.put(ActionW.FILTER.cmd(), evt.getNewValue());
            imageLayer.updateImageOperation(FilterOperation.name);
        } else if (command.equals(ActionW.PROGRESSION.cmd())) {
            actionsInView.put(ActionW.PROGRESSION.cmd(), evt.getNewValue());
            imageLayer.updateAllImageOperations();
        }
        if (lens != null) {
            // Transmit to the lens the command in case the source image has been freeze (for updating rotation and flip
            // => will keep consistent display)
            lens.setCommandFromParentView(command, evt.getNewValue());
            lens.updateZoom();
        }
    }

    protected void computeCrosslines(double location) {

    }

    @Override
    public void dispose() {
        disableMouseAndKeyListener();
        removeFocusListener(this);
        ToolTipManager.sharedInstance().unregisterComponent(this);
        imageLayer.removeLayerChangeListener(this);
        if (series != null) {
            closingSeries(series);
            series = null;
        }
        super.dispose();
    }

    public synchronized void disableMouseAndKeyListener() {
        MouseListener[] listener = this.getMouseListeners();

        MouseMotionListener[] motionListeners = this.getMouseMotionListeners();
        KeyListener[] keyListeners = this.getKeyListeners();
        MouseWheelListener[] wheelListeners = this.getMouseWheelListeners();
        for (int i = 0; i < listener.length; i++) {
            this.removeMouseListener(listener[i]);
        }
        for (int i = 0; i < motionListeners.length; i++) {
            this.removeMouseMotionListener(motionListeners[i]);
        }
        for (int i = 0; i < keyListeners.length; i++) {
            this.removeKeyListener(keyListeners[i]);
        }
        for (int i = 0; i < wheelListeners.length; i++) {
            this.removeMouseWheelListener(wheelListeners[i]);
        }
        if (lens != null) {
            lens.disableMouseAndKeyListener();
        }
    }

    public synchronized void iniDefaultMouseListener() {
        // focus listener is always on
        this.addMouseListener(focusHandler);
        this.addMouseMotionListener(focusHandler);
    }

    public synchronized void iniDefaultKeyListener() {
        this.addKeyListener(this);
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_C && e.isControlDown()) {
            final ViewTransferHandler imageTransferHandler = new ViewTransferHandler();
            imageTransferHandler.exportToClipboard(DefaultView2d.this,
                Toolkit.getDefaultToolkit().getSystemClipboard(), TransferHandler.COPY);
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE && e.isControlDown()) {
            ImageViewerPlugin<E> view = eventManager.getSelectedView2dContainer();
            if (view != null) {
                ViewerToolBar<E> toolBar = view.getViewerToolBar();
                if (toolBar != null) {
                    String command =
                        ViewerToolBar.getNextCommand(ViewerToolBar.actionsButtons,
                            toolBar.getMouseLeft().getActionCommand()).cmd();
                    changeLeftMouseAction(command);
                }
            }
        } else {
            ActionW action = eventManager.getActionFromkeyEvent(e.getKeyCode());
            if (action != null) {
                changeLeftMouseAction(action.cmd());
            }
        }

    }

    private void changeLeftMouseAction(String command) {
        ImageViewerPlugin<E> view = eventManager.getSelectedView2dContainer();
        if (view != null) {
            ViewerToolBar<E> toolBar = view.getViewerToolBar();
            if (toolBar != null) {
                MouseActions mouseActions = eventManager.getMouseActions();
                if (!command.equals(mouseActions.getAction(MouseActions.LEFT))) {
                    mouseActions.setAction(MouseActions.LEFT, command);
                    if (view != null) {
                        view.setMouseActions(mouseActions);
                    }
                    toolBar.changeButtonState(MouseActions.LEFT, command);
                }
            }
        }
    }

    private void drawPointer(Graphics2D g) {
        if (pointerType < 1)
            return;
        if (pointerType == 1) {
            drawPointer(g, (getWidth() - 1) * 0.5, (getHeight() - 1) * 0.5);
        } else if (pointerType == 3) {
            if (highlightedPosition != null) {
                // plus 0.5 pour être toujours centrer au milieu du pixel (surtout avec un fort zoom)
                drawPointer(g, highlightedPosition.x + 0.5, highlightedPosition.y + 0.5);
            }
        }
    }

    public int getPointerType() {
        return pointerType;
    }

    public void setPointerType(int pointerType) {
        this.pointerType = pointerType;
    }

    public void drawPointer(Graphics2D g, double x, double y) {
        float dash[] = { 5.0f };
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(x, y);
        g.setStroke(new BasicStroke(3.0f));
        g.setPaint(pointerColor1);
        for (int i = 1; i < pointer.length; i++) {
            g.draw(pointer[i]);
        }
        g.setStroke(new BasicStroke(1.0F, 0, 0, 5.0f, dash, 0.0f));
        g.setPaint(pointerColor2);
        for (int i = 1; i < pointer.length; i++) {
            g.draw(pointer[i]);
        }
        g.translate(-x, -y);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_DEFAULT);
    }

    protected void showPixelInfos(MouseEvent mouseevent) {
        if (infoLayer != null) {
            Point2D pModel = getImageCoordinatesFromMouse(mouseevent.getX(), mouseevent.getY());
            Rectangle oldBound = infoLayer.getPixelInfoBound();
            String str =
                getPixelInfo(new Point((int) Math.floor(pModel.getX()), (int) Math.floor(pModel.getY())), imageLayer);
            oldBound.width =
                Math.max(
                    oldBound.width,
                    DefaultView2d.this.getGraphics().getFontMetrics()
                        .stringWidth(Messages.getString("DefaultView2d.pix") + str) + 4); //$NON-NLS-1$
            infoLayer.setPixelInfo(str);
            repaint(oldBound);
        }
    }

    @Override
    public void focusGained(FocusEvent e) {

    }

    @Override
    public void focusLost(FocusEvent e) {
    }

    protected class MouseHandler extends MouseActionAdapter {

        @Override
        public void mousePressed(MouseEvent e) {
            int buttonMask = getButtonMaskEx();

            // Check if extended modifier of mouse event equals the current buttonMask
            // Also asserts that Mouse adapter is not disable
            if ((e.getModifiersEx() & buttonMask) == 0)
                return;

            // Convert mouse event point to real image coordinate point (without geometric transformation)
            MouseEventDouble mouseEvt = new MouseEventDouble(e);
            mouseEvt.setImageCoordinates(getImageCoordinatesFromMouse(e.getX(), e.getY()));

            // Do nothing and return if current dragSequence is not completed
            if (ds != null && !ds.completeDrag(mouseEvt))
                return;

            Cursor newCursor = AbstractLayerModel.DEFAULT_CURSOR;

            // Avoid any dragging on selection when Shift Button is Down
            if (!mouseEvt.isShiftDown()) {

                // Evaluates if mouse is on a dragging position, creates a DragSequence and changes cursor consequently
                List<AbstractDragGraphic> selectedDragGraphList = getLayerModel().getSelectedDragableGraphics();
                Graphic firstGraphicIntersecting = getLayerModel().getFirstGraphicIntersecting(mouseEvt);

                if (firstGraphicIntersecting instanceof AbstractDragGraphic) {
                    AbstractDragGraphic dragGraph = (AbstractDragGraphic) firstGraphicIntersecting;

                    if (selectedDragGraphList != null && selectedDragGraphList.contains(dragGraph)) {

                        if ((selectedDragGraphList.size() > 1)) {
                            ds = new BulkDragSequence(selectedDragGraphList, mouseEvt);
                            newCursor = AbstractLayerModel.MOVE_CURSOR;

                        } else if (selectedDragGraphList.size() == 1) {

                            if (dragGraph.isOnGraphicLabel(mouseEvt)) {
                                ds = dragGraph.createDragLabelSequence();
                                newCursor = AbstractLayerModel.HAND_CURSOR;

                            } else {
                                int handlePtIndex = dragGraph.getHandlePointIndex(mouseEvt);

                                if (handlePtIndex >= 0) {
                                    dragGraph.moveMouseOverHandlePoint(handlePtIndex, mouseEvt);
                                    ds = dragGraph.createResizeDrag(handlePtIndex);
                                    newCursor = AbstractLayerModel.EDIT_CURSOR;

                                } else {
                                    ds = dragGraph.createMoveDrag();
                                    newCursor = AbstractLayerModel.MOVE_CURSOR;
                                }
                            }
                        }
                    } else {
                        if (dragGraph.isOnGraphicLabel(mouseEvt)) {
                            ds = dragGraph.createDragLabelSequence();
                            newCursor = AbstractLayerModel.HAND_CURSOR;

                        } else {
                            ds = dragGraph.createMoveDrag();
                            newCursor = AbstractLayerModel.MOVE_CURSOR;
                        }
                        getLayerModel().setSelectedGraphic(dragGraph);
                    }
                }
            }

            if (ds == null) {
                AbstractDragGraphic dragGraph = getLayerModel().createDragGraphic(mouseEvt);

                if (dragGraph != null) {
                    ds = dragGraph.createResizeDrag();
                    if (dragGraph instanceof SelectGraphic) {
                        getLayerModel().setSelectGraphic((SelectGraphic) dragGraph);
                    } else {
                        getLayerModel().setSelectedGraphic(dragGraph);
                    }
                }
            }

            getLayerModel().setCursor(newCursor);

            if (ds != null) {
                ds.startDrag(mouseEvt);
            } else {
                getLayerModel().setSelectedGraphics(null);
            }

            // Throws to the tool listener the current graphic selection.
            getLayerModel().fireGraphicsSelectionChanged(getImage());

        }

        @Override
        public void mouseReleased(MouseEvent e) {
            int buttonMask = getButtonMask();

            // Check if extended modifier of mouse event equals the current buttonMask
            // Note that extended modifiers are not triggered in mouse released
            // Also asserts that Mouse adapter is not disable
            if ((e.getModifiers() & buttonMask) == 0)
                return;

            // Do nothing and return if no dragSequence exist
            if (ds == null)
                return;

            // Convert mouse event point to real image coordinate point (without geometric transformation)
            MouseEventDouble mouseEvt = new MouseEventDouble(e);
            mouseEvt.setImageCoordinates(getImageCoordinatesFromMouse(e.getX(), e.getY()));

            SelectGraphic selectGraphic = getLayerModel().getSelectGraphic();

            if (selectGraphic != null) {

                AffineTransform transform = getAffineTransform(mouseEvt);
                Rectangle selectionRect = selectGraphic.getBounds(transform);

                // Little size rectangle in selection click is interpreted as a single clic
                boolean isSelectionSingleClic =
                    (selectionRect == null || (selectionRect.width < 5 && selectionRect.height < 5));

                List<Graphic> newSelectedGraphList = null;

                if (!isSelectionSingleClic) {
                    newSelectedGraphList = getLayerModel().getSelectedAllGraphicsIntersecting(selectionRect, transform);
                } else {
                    Graphic selectedGraph = getLayerModel().getFirstGraphicIntersecting(mouseEvt);
                    if (selectedGraph != null) {
                        newSelectedGraphList = new ArrayList<Graphic>(1);
                        newSelectedGraphList.add(selectedGraph);
                    }
                }

                // Add all graphics inside selection rectangle at any level in layers instead in the case of single
                // click where top level first graphic found is removed from list if already selected
                if (mouseEvt.isShiftDown()) {
                    List<Graphic> selectedGraphList = new ArrayList<Graphic>(getLayerModel().getSelectedGraphics());

                    if (selectedGraphList != null && selectedGraphList.size() > 0) {
                        if (newSelectedGraphList == null) {
                            newSelectedGraphList = new ArrayList<Graphic>(selectedGraphList);
                        } else {
                            for (Graphic graphic : selectedGraphList) {
                                if (!newSelectedGraphList.contains(graphic)) {
                                    newSelectedGraphList.add(graphic);
                                } else if (isSelectionSingleClic) {
                                    newSelectedGraphList.remove(graphic);
                                }
                            }
                        }
                    }
                }

                getLayerModel().setSelectedGraphics(newSelectedGraphList);
                getLayerModel().setSelectGraphic(null);
            }

            if (ds.completeDrag(mouseEvt)) {

                ActionState drawOnceAction = eventManager.getAction(ActionW.DRAW_ONLY_ONCE);
                if (drawOnceAction instanceof ToggleButtonListener) {
                    if (((ToggleButtonListener) drawOnceAction).isSelected()) {
                        ActionState measure = eventManager.getAction(ActionW.DRAW_MEASURE);
                        if (measure instanceof ComboItemListener) {
                            ((ComboItemListener) measure).setSelectedItem(MeasureToolBar.selectionGraphic);
                        }
                    }
                }
                ds = null;
            }

            // Throws to the tool listener the current graphic selection.
            getLayerModel().fireGraphicsSelectionChanged(getImage());

            Cursor newCursor = AbstractLayerModel.DEFAULT_CURSOR;

            // TODO below is the same code as this is in mouseMoved, can be a function instead
            // Evaluates if mouse is on a dragging position, and changes cursor image consequently
            List<AbstractDragGraphic> selectedDragGraphList = getLayerModel().getSelectedDragableGraphics();
            Graphic firstGraphicIntersecting = getLayerModel().getFirstGraphicIntersecting(mouseEvt);

            if (firstGraphicIntersecting instanceof AbstractDragGraphic) {
                AbstractDragGraphic dragGraph = (AbstractDragGraphic) firstGraphicIntersecting;

                if (selectedDragGraphList != null && selectedDragGraphList.contains(dragGraph)) {

                    if ((selectedDragGraphList.size() > 1)) {
                        newCursor = AbstractLayerModel.MOVE_CURSOR;

                    } else if (selectedDragGraphList.size() == 1) {

                        if (dragGraph.isOnGraphicLabel(mouseEvt)) {
                            newCursor = AbstractLayerModel.HAND_CURSOR;

                        } else {
                            if (dragGraph.getHandlePointIndex(mouseEvt) >= 0) {
                                newCursor = AbstractLayerModel.EDIT_CURSOR;
                            } else {
                                newCursor = AbstractLayerModel.MOVE_CURSOR;
                            }
                        }
                    }
                } else {
                    if (dragGraph.isOnGraphicLabel(mouseEvt)) {
                        newCursor = AbstractLayerModel.HAND_CURSOR;
                    } else {
                        newCursor = AbstractLayerModel.MOVE_CURSOR;
                    }
                }
            }

            getLayerModel().setCursor(newCursor);

        }

        @Override
        public void mouseDragged(MouseEvent e) {
            int buttonMask = getButtonMaskEx();

            // Check if extended modifier of mouse event equals the current buttonMask
            // Also asserts that Mouse adapter is not disable
            if ((e.getModifiersEx() & buttonMask) == 0)
                return;

            if (ds != null) {
                // Convert mouse event point to real image coordinate point (without geometric transformation)
                MouseEventDouble mouseEvt = new MouseEventDouble(e);
                mouseEvt.setImageCoordinates(getImageCoordinatesFromMouse(e.getX(), e.getY()));

                ds.drag(mouseEvt);
            }
        }

        @Override
        public void mouseMoved(MouseEvent e) {

            // Convert mouse event point to real image coordinate point (without geometric transformation)
            MouseEventDouble mouseEvt = new MouseEventDouble(e);
            mouseEvt.setImageCoordinates(getImageCoordinatesFromMouse(e.getX(), e.getY()));

            if (ds != null) {
                ds.drag(mouseEvt);
            } else {

                Cursor newCursor = AbstractLayerModel.DEFAULT_CURSOR;

                if (!mouseEvt.isShiftDown()) {
                    // Evaluates if mouse is on a dragging position, and changes cursor image consequently
                    List<AbstractDragGraphic> selectedDragGraphList = getLayerModel().getSelectedDragableGraphics();
                    Graphic firstGraphicIntersecting = getLayerModel().getFirstGraphicIntersecting(mouseEvt);

                    if (firstGraphicIntersecting instanceof AbstractDragGraphic) {
                        AbstractDragGraphic dragGraph = (AbstractDragGraphic) firstGraphicIntersecting;

                        if (selectedDragGraphList != null && selectedDragGraphList.contains(dragGraph)) {

                            if ((selectedDragGraphList.size() > 1)) {
                                newCursor = AbstractLayerModel.MOVE_CURSOR;

                            } else if (selectedDragGraphList.size() == 1) {

                                if (dragGraph.isOnGraphicLabel(mouseEvt)) {
                                    newCursor = AbstractLayerModel.HAND_CURSOR;

                                } else {
                                    if (dragGraph.getHandlePointIndex(mouseEvt) >= 0) {
                                        newCursor = AbstractLayerModel.EDIT_CURSOR;
                                    } else {
                                        newCursor = AbstractLayerModel.MOVE_CURSOR;
                                    }
                                }
                            }
                        } else {
                            if (dragGraph.isOnGraphicLabel(mouseEvt)) {
                                newCursor = AbstractLayerModel.HAND_CURSOR;
                            } else {
                                newCursor = AbstractLayerModel.MOVE_CURSOR;
                            }
                        }
                    }
                }
                getLayerModel().setCursor(newCursor);
            }
        }
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    class FocusHandler extends MouseActionAdapter {

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
                ImageViewerPlugin<E> pane = eventManager.getSelectedView2dContainer();
                if (pane != null) {
                    pane.maximizedSelectedImagePane(DefaultView2d.this);
                }
            }
        }

        @Override
        public void mousePressed(MouseEvent mouseevent) {
            ImageViewerPlugin<E> pane = eventManager.getSelectedView2dContainer();
            if (pane == null)
                return;
            if (pane.isContainingView(DefaultView2d.this)) {
                // register all actions of the EventManager with this view waiting the focus gained in some cases is not
                // enough, because others mouseListeners are triggered before the focus event (that means before
                // registering the view in the EventManager)
                pane.setSelectedImagePane(DefaultView2d.this);
            }
            // request the focus even it is the same pane selected
            requestFocusInWindow();
            int modifiers = mouseevent.getModifiersEx();
            MouseActions mouseActions = eventManager.getMouseActions();
            ActionW action = null;
            // left mouse button, always active
            if ((modifiers & InputEvent.BUTTON1_DOWN_MASK) != 0) {
                action = eventManager.getActionFromCommand(mouseActions.getLeft());
            }
            // middle mouse button
            else if ((modifiers & InputEvent.BUTTON2_DOWN_MASK) != 0
                && ((mouseActions.getActiveButtons() & InputEvent.BUTTON2_DOWN_MASK) != 0)) {
                action = eventManager.getActionFromCommand(mouseActions.getMiddle());
            }
            // right mouse button
            else if ((modifiers & InputEvent.BUTTON3_DOWN_MASK) != 0
                && ((mouseActions.getActiveButtons() & InputEvent.BUTTON3_DOWN_MASK) != 0)) {
                action = eventManager.getActionFromCommand(mouseActions.getRight());
            }

            DefaultView2d.this.setCursor(action == null ? AbstractLayerModel.DEFAULT_CURSOR : action.getCursor());
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            showPixelInfos(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            DefaultView2d.this.setCursor(AbstractLayerModel.DEFAULT_CURSOR);
        }
    }

    public List<Action> getExportToClipboardAction() {
        List<Action> list = new ArrayList<Action>();
        AbstractAction exportToClipboardAction = new AbstractAction(Messages.getString("DefaultView2d.clipboard")) { //$NON-NLS-1$

                @Override
                public void actionPerformed(ActionEvent e) {
                    final ImageTransferHandler imageTransferHandler = new ImageTransferHandler();
                    imageTransferHandler.exportToClipboard(DefaultView2d.this, Toolkit.getDefaultToolkit()
                        .getSystemClipboard(), TransferHandler.COPY);
                }
            };

        list.add(exportToClipboardAction);
        exportToClipboardAction = new AbstractAction("Selected View to Clipboard (except demographics)") {

            @Override
            public void actionPerformed(ActionEvent e) {
                final ViewTransferHandler imageTransferHandler = new ViewTransferHandler();
                imageTransferHandler.exportToClipboard(DefaultView2d.this, Toolkit.getDefaultToolkit()
                    .getSystemClipboard(), TransferHandler.COPY);
            }
        };
        exportToClipboardAction.putValue(Action.ACCELERATOR_KEY,
            KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_MASK));
        list.add(exportToClipboardAction);
        return list;
    }

    public abstract void enableMouseAndKeyListener(MouseActions mouseActions);

    public static final AffineTransform getAffineTransform(MouseEvent mouseevent) {
        if (mouseevent != null && mouseevent.getSource() instanceof Image2DViewer)
            return ((Image2DViewer) mouseevent.getSource()).getAffineTransform();
        return null;
    }

}
