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
import java.awt.geom.Rectangle2D;
import java.awt.image.RenderedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.media.jai.PlanarImage;
import javax.swing.AbstractAction;
import javax.swing.DefaultBoundedRangeModel;
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

public abstract class DefaultView2d<E extends ImageElement> extends GraphicsPane implements PropertyChangeListener,
    FocusListener, Image2DViewer, ImageLayerChangeListener, KeyListener {
    private static final ImageTransferHandler EXPORT_TO_CLIPBOARD = new ImageTransferHandler();

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
    private final AbstractAction exportToClipboardAction = new AbstractAction(
        Messages.getString("DefaultView2d.clipboard")) { //$NON-NLS-1$

            @Override
            public void actionPerformed(ActionEvent e) {
                EXPORT_TO_CLIPBOARD.exportToClipboard(DefaultView2d.this, Toolkit.getDefaultToolkit()
                    .getSystemClipboard(), TransferHandler.COPY);
            }
        };
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
        if (eventManager == null) {
            throw new IllegalArgumentException("EventManager cannot be null"); //$NON-NLS-1$
        }
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
            if (image != null && p.x >= 0 && p.y >= 0 && p.x < image.getWidth() && p.y < image.getHeight()) {
                try {
                    int[] c = { 0, 0, 0 };
                    imageLayer.getReadIterator().getPixel(p.x, p.y, c); // read the pixel

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

        public void startDrag(MouseEvent mouseevent) {
            int i = 0;
            for (int j = childDS.size(); i < j; i++) {
                ((DragSequence) childDS.get(i)).startDrag(mouseevent);
            }
        }

        public void drag(MouseEvent mouseevent) {
            int i = 0;
            for (int j = childDS.size(); i < j; i++) {
                ((DragSequence) childDS.get(i)).drag(mouseevent);
            }
        }

        public boolean completeDrag(MouseEvent mouseevent) {
            int i = 0;
            for (int j = childDS.size(); i < j; i++) {
                ((DragSequence) childDS.get(i)).completeDrag(mouseevent);
            }
            return true;
        }

        private final java.util.List childDS;

        BulkDragSequence(java.util.List list, MouseEvent mouseevent) {
            int i = list.size();
            childDS = new ArrayList(i);
            for (int j = 0; j < i; j++) {
                DragSequence dragsequence = ((AbstractDragGraphic) list.get(j)).createDragSequence(this, mouseevent);
                if (dragsequence != null) {
                    childDS.add(dragsequence);
                }
            }
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
        if (series == null) {
            return;
        }
        boolean open = false;
        synchronized (UIManager.VIEWER_PLUGINS) {
            List<ViewerPlugin> plugins = UIManager.VIEWER_PLUGINS;
            pluginList: for (final ViewerPlugin plugin : plugins) {
                List<MediaSeries> openSeries = plugin.getOpenSeries();
                if (openSeries != null) {
                    for (MediaSeries s : openSeries) {
                        if (series == s) {
                            // The sequence is still open in another view or
                            // plugin
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

    protected void setImage(E img, boolean bestFit) {
        E oldImage = imageLayer.getSourceImage();
        if (img != null && !img.equals(oldImage)) {

            RenderedImage source = img.getImage();
            int width = source == null ? ImageFiler.TILESIZE : source.getWidth();
            int height = source == null ? ImageFiler.TILESIZE : source.getHeight();
            final Rectangle modelArea = new Rectangle(0, 0, width, height);
            DragLayer layer = getLayerModel().getMeasureLayer();
            synchronized (this) {
                GraphicList list = (GraphicList) img.getTagValue(TagW.MeasurementGraphics);
                if (list != null) {
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
                setPreferredSize(modelArea.getSize());
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

    public MediaSeries<E> getSeries() {
        return series;
    }

    public int getCurrentImageIndex() {
        if (series instanceof Series) {
            return ((Series) series).getImageIndex(imageLayer.getSourceImage());
        }
        return 0;
    }

    public E getImage() {
        return imageLayer.getSourceImage();
    }

    public RenderedImage getSourceImage() {
        E image = getImage();
        if (image == null) {
            return null;
        }
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
        if (bound.height < 300 || bound.width < 300) {
            return FontTools.getFont8();
        } else if (bound.height < 500 || bound.width < 500) {
            return FontTools.getFont10();
        } else {
            return FontTools.getFont12();
        }
    }

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
        getLayerModel().setSelectedGraphic(null);
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
        // Set a fix font size for computing shared text areas that need to be repainted in different zoom magnitudes.
        g2d.setFont(FontTools.getFont10());

        imageLayer.drawImage(g2d);
        drawLayers(g2d, affineTransform, inverseTransform);

        g2d.translate(offsetX, offsetY);

        drawPointer(g2d);
        if (infoLayer != null) {
            // Set font size according to the view size
            g2d.setFont(getFont());
            infoLayer.paint(g2d);
            g2d.setFont(FontTools.getFont10());
        }

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
        if (lens == null) {
            return null;
        }
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

    public void propertyChange(PropertyChangeEvent evt) {
        if (series == null) {
            return;
        }
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
            EXPORT_TO_CLIPBOARD.exportToClipboard(DefaultView2d.this, Toolkit.getDefaultToolkit().getSystemClipboard(),
                TransferHandler.COPY);
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
        if (pointerType < 1) {
            return;
        }
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
            Point pView = mouseevent.getPoint();
            Point pModel = getRealCoordinates(pView);
            Rectangle oldBound = infoLayer.getPixelInfoBound();
            String str = getPixelInfo(pModel, imageLayer);
            oldBound.width =
                Math.max(
                    oldBound.width,
                    DefaultView2d.this.getGraphics().getFontMetrics()
                        .stringWidth(Messages.getString("DefaultView2d.pix") + str) + 4); //$NON-NLS-1$
            infoLayer.setPixelInfo(str);
            repaint(oldBound);
        }
    }

    public void focusGained(FocusEvent e) {

    }

    public void focusLost(FocusEvent e) {
    }

    protected class MouseHandler extends MouseActionAdapter {

        @Override
        public void mousePressed(MouseEvent mouseevent) {
            int buttonMask = getButtonMaskEx();
            if ((mouseevent.getModifiersEx() & buttonMask) != 0) {
                if (ds != null) {
                    if (ds.completeDrag(mouseevent)) {
                        ds = null;

                        ActionState drawOnceAction = eventManager.getAction(ActionW.DRAW_ONLY_ONCE);
                        if (drawOnceAction instanceof ToggleButtonListener) {
                            if (((ToggleButtonListener) drawOnceAction).isSelected()) {
                                ActionState measure = eventManager.getAction(ActionW.DRAW_MEASURE);
                                if (measure instanceof ComboItemListener) {
                                    ((ComboItemListener) measure).setSelectedItem(MeasureToolBar.selectionGraphic);
                                }
                            }
                        }
                    }
                    return;
                }
                ds = null;

                AbstractLayerModel showDraws = getLayerModel();
                Point pView = mouseevent.getPoint();
                Point pModel = getRealCoordinates(pView);
                int offsetx = pModel.x - pView.x;
                int offsety = pModel.y - pView.y;
                // convert mouseevent point to real image coordinate point (without geometric transformation)
                mouseevent.translatePoint(offsetx, offsety);

                showDraws.changeCursorDesign(mouseevent);

                java.util.List dragList = showDraws.getSelectedDragableGraphics();
                int j = dragList != null ? dragList.size() : 0;
                // si le curseur change pour le redimensionnement du graphic
                // permet de redimensionner un graphic qui se trouve sous un
                // autre graphic
                boolean shiftDown = mouseevent.isShiftDown();
                boolean ctrlDown = mouseevent.isControlDown();
                if (showDraws.isShapeAction() && !shiftDown) {
                    if (j == 1) {
                        AbstractDragGraphic graph = (AbstractDragGraphic) dragList.get(0);
                        ds = graph.createDragSequence(null, mouseevent);
                    } else if (j > 1) {
                        ds = new BulkDragSequence(dragList, mouseevent);
                    }
                }
                if (ctrlDown || showDraws.getCreateGraphic() == null) { // mode de sélection
                    // avec la flèche
                    Graphic pointedGraphic = showDraws.getFirstGraphicIntersecting(mouseevent);
                    ArrayList selGraph = new ArrayList(showDraws.getSelectedGraphics());
                    if (pointedGraphic == null && shiftDown) {
                        return;
                    }
                    if (pointedGraphic instanceof AbstractDragGraphic) {
                        AbstractDragGraphic graphic1 = (AbstractDragGraphic) pointedGraphic;
                        // si la listes des graphics dragable est sup à 1 et
                        // quelle contient le graphic
                        // alors on crée une séquence de drag de la liste
                        // des graphics
                        if (selGraph.size() > 1 && selGraph.contains(graphic1)) {
                            if (shiftDown) {
                                selGraph.remove(graphic1);
                                showDraws.setSelectedGraphics(selGraph);
                            }
                            // else {
                            ds = new BulkDragSequence(showDraws.getSelectedDragableGraphics(), mouseevent);
                            // }
                        } else {
                            if (!shiftDown) {
                                showDraws.setSelectedGraphics(null);
                                ds = graphic1.createDragSequence(null, mouseevent);
                                showDraws.setSelectedGraphic(graphic1);
                            } else {
                                showDraws.setSelectedGraphics(null);
                                if (!selGraph.contains(graphic1)) {
                                    selGraph.add(graphic1);
                                    showDraws.setSelectedGraphics(selGraph);
                                    if (selGraph.size() > 1) {
                                        ds = new BulkDragSequence(showDraws.getSelectedDragableGraphics(), mouseevent);
                                    }
                                }
                            }
                        }
                    }
                }
                if (ds == null) {
                    AbstractDragGraphic graphic = showDraws.createGraphic(mouseevent);
                    if (graphic != null) {
                        ds = graphic.createDragSequence(null, null);
                        if (ds != null) {
                            if (!shiftDown) {
                                showDraws.setSelectedGraphic(graphic);
                            }
                        }
                    }
                }
                if (ds != null) {
                    ds.startDrag(mouseevent);
                } else {
                    showDraws.setSelectedGraphic(null);
                }
                mouseevent.translatePoint(-offsetx, -offsety);
            }
        }

        @Override
        public void mouseReleased(MouseEvent mouseevent) {
            // Using standard modifiers, ex modifiers are not triggered in mouse released
            int buttonMask = getButtonMask();
            if ((mouseevent.getModifiers() & buttonMask) != 0) {
                // if (colorPicker != null) {
                // colorPicker.setArea(pickView(updateMouseToRealCoord(mouseevent,
                // false).getPoint()), true);
                // imageFrame.getToolsBar().setDrawWithOtherTools(false);
                // colorPicker = null;
                // }
                // if (!imageFrame.getThumbOption().isDrawingsVisible()) {
                // return;
                // }
                if (ds != null) {
                    Point pView = mouseevent.getPoint();
                    Point pModel = getRealCoordinates(pView);
                    int offsetx = pModel.x - pView.x;
                    int offsety = pModel.y - pView.y;
                    // convert mouseevent point to real image coordinate point (without geometric transformation)
                    mouseevent.translatePoint(offsetx, offsety);

                    AbstractLayerModel model = getLayerModel();
                    SelectGraphic selectionGraphic = model.getSelectionGraphic();
                    // Select all graphs inside the selection
                    if (selectionGraphic != null) {
                        List<Graphic> list = model.getSelectedAllGraphicsIntersecting(selectionGraphic.getBounds());
                        list.remove(selectionGraphic);
                        model.setSelectedGraphics(list);
                    }

                    if (ds.completeDrag(mouseevent)) {
                        // Throws to the tool listener the current graphic selection.
                        model.fireGraphicsSelectionChanged(getImage());

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
                    model.changeCursorDesign(mouseevent);
                    mouseevent.translatePoint(-offsetx, -offsety);
                }
                // if (mouseevent.getClickCount() == 2) {
                // int j = showDraws.getSelectedGraphics() != null ? showDraws.getSelectedGraphics().size() : 0;
                // if (j == 1) {
                // ((Graphic) showDraws.getSelectedGraphics().get(0)).showProperties();
                // }
                // }
            }
        }

        @Override
        public void mouseDragged(MouseEvent mouseevent) {
            int buttonMask = getButtonMaskEx();
            if ((mouseevent.getModifiersEx() & buttonMask) != 0) {
                if (ds != null) {
                    Point pView = mouseevent.getPoint();
                    Point pModel = getRealCoordinates(pView);
                    int offsetx = pModel.x - pView.x;
                    int offsety = pModel.y - pView.y;
                    // convert mouseevent point to a 100% zoom position
                    mouseevent.translatePoint(offsetx, offsety);
                    ds.drag(mouseevent);
                    mouseevent.translatePoint(-offsetx, -offsety);
                }
            }
        }

        @Override
        public void mouseMoved(MouseEvent mouseevent) {
            // int buttonMask = getButtonMaskEx();
            // if ((mouseevent.getModifiersEx() & buttonMask) != 0) {
            Point pView = mouseevent.getPoint();
            Point pModel = getRealCoordinates(pView);
            int offsetx = pModel.x - pView.x;
            int offsety = pModel.y - pView.y;
            // convert mouseevent point to real image coordinate point (without geometric transformation)
            mouseevent.translatePoint(offsetx, offsety);
            getLayerModel().changeCursorDesign(mouseevent);
            if (ds != null) {
                switch (mouseevent.getID()) {
                    case MouseEvent.MOUSE_DRAGGED:
                    case MouseEvent.MOUSE_MOVED:
                        ds.drag(mouseevent);
                        break;
                }
            }
            mouseevent.translatePoint(-offsetx, -offsety);
        }
        // }
    }

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
            if (pane == null) {
                return;
            }
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

    public AbstractAction getExportToClipboardAction() {
        return exportToClipboardAction;
    }

    public abstract void enableMouseAndKeyListener(MouseActions mouseActions);

}
