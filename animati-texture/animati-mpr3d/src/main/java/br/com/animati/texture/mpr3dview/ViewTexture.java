/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview;

import br.com.animati.texture.codec.ImageSeriesFactory;
import br.com.animati.texture.codec.StaticHelpers;
import br.com.animati.texture.codec.TextureDicomSeries;
import br.com.animati.texture.mpr3dview.api.AbstractViewsContainer;
import br.com.animati.texture.mpr3dview.api.ActionDataModel;
import br.com.animati.texture.mpr3dview.api.ActionWA;
import br.com.animati.texture.mpr3dview.api.DVLayerModel;
import br.com.animati.texture.mpr3dview.api.DisplayUtils;
import br.com.animati.texture.mpr3dview.api.GraphicsModel;
import br.com.animati.texture.mpr3dview.api.GridElement;
import br.com.animati.texture.mpr3dview.api.MeasureAdapter;
import br.com.animati.texture.mpr3dview.api.PixelInfo3d;
import br.com.animati.texture.mpr3dview.api.ViewCore;
import br.com.animati.texture.mpr3dview.api.ViewsGrid;
import br.com.animati.texture.mpr3dview.internal.Messages;
import br.com.animati.texturedicom.ColorMask;
import br.com.animati.texturedicom.ControlAxes;
import br.com.animati.texturedicom.ImageSeries;
import br.com.animati.texturedicom.TextureImageCanvas;
import br.com.animati.texturedicom.cl.CLConvolution;
import br.com.animati.texture.mpr3dview.api.RenderSupport;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.util.HashMap;
import java.util.List;
import javax.swing.BoundedRangeModel;
import javax.vecmath.Matrix3d;
import javax.vecmath.Point2i;
import javax.vecmath.Point3d;
import javax.vecmath.Quat4d;
import javax.vecmath.Vector2d;
import javax.vecmath.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.AnnotationsLayer;
import org.weasis.core.ui.editor.image.PixelInfo;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.graphic.DragLayer;
import org.weasis.core.ui.graphic.TempLayer;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.graphic.model.DefaultViewModel;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.display.PresetWindowLevel;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 18 Jul.
 */
public class ViewTexture extends TextureImageCanvas
        implements ViewCore<TextureDicomSeries> {
    
    /** Class logger. */
    private static final Logger LOGGER =
            LoggerFactory.getLogger(ViewTexture.class);
    
    /** Tolerance to consider an axix as the Acquisition Axis. */
    private double WARNING_TOLERANCE = 0.0001;

    protected final HashMap<String, Object> actionsInView =
            new HashMap<String, Object>();
    
    private MouseActionAdapter measureAdapter = new MeasureAdapter(this);
    protected final GraphicsModel graphsLayer;
    private AnnotationsLayer infoLayer;
    public static boolean computePixelStats = true;
    
    private final RenderSupport renderSupp = new RenderSupport(this);
    
    private static List<ColorMask> colorMaskList;
    private static List<StaticHelpers.TextureKernel> kernelList;
    
    private Cross3dListener crosshairAction = new Cross3dListener();
    
    private SliderChangeListener windowAction = new SliderChangeListener(
            ActionW.WINDOW, WINDOW_SMALLEST, WINDOW_LARGEST, WINDOW_DEFAULT,
            true, 1.25) {
        @Override
        public void stateChanged(BoundedRangeModel model) {            
            windowingWindow = (Integer) model.getValue();
            repaint();
            setActionsInView(ActionW.PRESET.cmd(), null, false);
            //publish
            EventPublisher.getInstance().publish(new PropertyChangeEvent(
                    ViewTexture.this, EventPublisher.VIEWER_ACTION_CHANGED
                    + ActionW.WINDOW.cmd(), null, model.getValue()));
        }
    };
    
    private SliderChangeListener levelAction = new SliderChangeListener(
            ActionW.LEVEL, LEVEL_SMALLEST, LEVEL_LARGEST, LEVEL_DEFAULT,
            true, 1.25) {
        @Override
        public void stateChanged(BoundedRangeModel model) {            
            windowingLevel = (Integer) model.getValue();
            repaint();
            setActionsInView(ActionW.PRESET.cmd(), null, false);
            EventPublisher.getInstance().publish(new PropertyChangeEvent(
                    ViewTexture.this, EventPublisher.VIEWER_ACTION_CHANGED
                    + ActionW.LEVEL.cmd(), null, model.getValue()));
        }
    };
    
    private SliderChangeListener zoomAction =
        new SliderChangeListener(ActionW.ZOOM, ZOOM_SLIDER_MIN,
            ZOOM_SLIDER_MAX, 1, true, 0.1) {

        @Override
        public void stateChanged(BoundedRangeModel model) { 
            double oldZoom = (Double) getActionValue(ActionW.ZOOM.cmd());
            double zoomVal =
                    GridViewUI.sliderValueToViewScale(model.getValue());            
            setActionsInView(ActionW.ZOOM.cmd(), zoomVal, true);   
            
            EventPublisher.getInstance().publish(new PropertyChangeEvent(
                        ViewTexture.this, EventPublisher.VIEWER_ACTION_CHANGED
                        + ActionW.ZOOM.cmd(), oldZoom, zoomVal));
        }
    };
      
    private SliderChangeListener scrollSeriesAction =
            new SliderChangeListener(
            ActionW.SCROLL_SERIES, 1, 100, 1, true, 0.1) {
        @Override
        public void stateChanged(BoundedRangeModel model) {
            setSlice(model.getValue());
            if (hasContent()) {
                EventPublisher.getInstance().publish(new PropertyChangeEvent(
                    ViewTexture.this, EventPublisher.VIEWER_ACTION_CHANGED
                    + ActionW.SCROLL_SERIES.cmd(), null, getCurrentSlice()));
            }
        }
        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            setValue(getValue() + e.getWheelRotation());
        }
    };

    private SliderChangeListener rotationAction =
            new SliderChangeListener(
            ActionW.ROTATION, 0, 360, 0, true, 0.25) {
        @Override
        public void stateChanged(BoundedRangeModel model) {
            setRotationOffset(Math.toRadians(model.getValue()));
            updateAffineTransform();
            repaint();
            EventPublisher.getInstance().publish(new PropertyChangeEvent(
                    ViewTexture.this, EventPublisher.VIEWER_ACTION_CHANGED
                    + ActionW.ROTATION.cmd(), null, model.getValue()));
        }
    }; 
    
    public ViewTexture(ImageSeries parentImageSeries) throws Exception {
        super(parentImageSeries);
        
        graphsLayer = new GraphicsModel(
                new DVLayerModel(this), new TextureViewModel());
     
        infoLayer = new InfoLayer3d(this);
        
        DragLayer layer = new DragLayer(getLayerModel(),
                AbstractLayer.CROSSLINES);
        layer.setLocked(true);
        getLayerModel().addLayer(layer);
        layer = new DragLayer(getLayerModel(), AbstractLayer.MEASURE) {
            @Override
            protected AffineTransform getAffineTransform() {
                return graphsLayer.getAffineTransform();
            }
        };
        getLayerModel().addLayer(layer);
        TempLayer layerTmp = new TempLayer(getLayerModel()) {
            @Override
            protected AffineTransform getAffineTransform() {
                return graphsLayer.getAffineTransform();
            }
        };
        getLayerModel().addLayer(layerTmp);
        
        initActionWState();

        //Defaults
        cubeHelperScale = 0;
        interpolate = true;
        
        setFocusable(true);
        
        // WEA-258
        // Must be larger to the screens to be resize correctly by the container
        setPreferredSize(new Dimension(4096, 4096));
        setMinimumSize(new Dimension(50, 50));
        
        enableActions();
    }   
        
    private void enableActions() {
        zoomAction.enableAction(true);
        windowAction.enableAction(true);
        levelAction.enableAction(true);
        rotationAction.enableAction(true);
        scrollSeriesAction.enableAction(true);
    }
    
    /* ViewCore implementation */
	
    @Override
    public void fixPosition() {
        //Center
        setImageOffset(new Vector2d(0, 0));
        EventPublisher.getInstance().publish(
            new PropertyChangeEvent(ViewTexture.this, 
            EventPublisher.VIEWER_ACTION_CHANGED 
            + ActionW.PAN.cmd(), null, "center"));

        repaint();

    }
    
    /** @return the colorMaskList */
    public List<ColorMask> getColorMaskList() {
        if (colorMaskList == null) {
            colorMaskList = StaticHelpers.buildColorMaskList();
        }
        return colorMaskList;
    }

    /** @return the kernelList. */
    public List<StaticHelpers.TextureKernel> getKernelList() {
        if (kernelList == null) {
            kernelList = StaticHelpers.buildKernelList();
        }
        return kernelList;
    }
    
    @Override
    public String getSeriesObjectClassName() {
        return TextureDicomSeries.class.getName();
    }
    
    /**
     * Converts the geometric point to text information.
     * @param point Geometric point
     * @param position Position on view (to decide if its in or out of image.
     * @return Text information.
     */
    public PixelInfo getPixelInfo(final Point3d point, final Point2i position) {
        PixelInfo3d pixelInfo = new PixelInfo3d();
        if (isShowingAcquisitionAxis()) {
            pixelInfo.setPosition(new Point((int) point.x, (int) point.y));
        } else {
            pixelInfo.setPosition3d(point);
        }
        
        return pixelInfo;
    }
    
    @Override
    public void showPixelInfos(MouseEvent mouseevent) {
        if (infoLayer != null && getParentImageSeries() != null) {
            Point2i position = new Point2i(mouseevent.getX(), mouseevent.getY());
            Rectangle oldBound = infoLayer.getPixelInfoBound();
            Vector3d coordinatesTexture = getTextureCoordinatesForPosition(position);
            Vector3d imageSize = getParentImageSeries().getImageSize();
            Point3d pModel =
                    getImageCoordinatesFromMouse(coordinatesTexture, imageSize);            
            PixelInfo pixInfo =
                getPixelInfo(pModel, position);
            oldBound.width = Math.max(oldBound.width,
                    this.getGraphics().getFontMetrics(getLayerFont())
                    .stringWidth(Messages.getString("InfoLayer3d.pixel") + pixInfo) + 4);
            infoLayer.setPixelInfo(pixInfo);
            repaint(oldBound);
        }
    }
    
    /** required when used getGraphics().getFont() in GraphicLabel. */
    @Override
    public Font getFont() {
        return MeasureTool.viewSetting.getFont();
    }
    
    @Override
    protected void paintComponent(Graphics graphs) {
        try {
            super.paintComponent(graphs);
            Graphics2D g2d = (Graphics2D) graphs;
            Font oldFont = g2d.getFont();

            Rectangle imageRect = getUnrotatedImageRect();
            double offsetX = -imageRect.getX();
            double offsetY = -imageRect.getY();
            // Paint the visible area
            g2d.translate(-offsetX, -offsetY);

            Font defaultFont = getFont();
            g2d.setFont(defaultFont);

            drawLayers(g2d, graphsLayer.getAffineTransform(),
                    graphsLayer.getInverseTransform());

            g2d.translate(offsetX, offsetY);

            if (infoLayer != null) {
                 g2d.setFont(getLayerFont());
                infoLayer.paint(g2d);
            }
            g2d.setFont(oldFont);
            
        } catch (Exception ex) {
            LOGGER.error("Cant paint component!");
            ex.printStackTrace();
        }
    }
    
    private void drawLayers(Graphics2D g2d, AffineTransform transform, AffineTransform inverseTransform) {
        boolean draw = true;
        Object actionValue = getActionValue(ActionW.DRAW.cmd());
        if (actionValue instanceof Boolean) {
            draw = (Boolean) actionValue;
        }
        if (hasContent() && draw) {
            getLayerModel().draw(
                g2d,
                transform,
                inverseTransform,
                new Rectangle2D.Double(graphsLayer.modelToViewLength(
                    graphsLayer.getViewModel().getModelOffsetX()),
                    graphsLayer.modelToViewLength(
                    graphsLayer.getViewModel().getModelOffsetY()),
                    getWidth(), getHeight()));
        }
    }
    
    /**
     * Get ImageRectangle without rotation.
     * @return 
     */
    public Rectangle getUnrotatedImageRect() {
        double currentRO = getRotationOffset();
        setRotationOffset(0);
        Rectangle imageRect = getImageRect(true);
        setRotationOffset(currentRO);
        
        return imageRect;
    }
    
    public void forceResize() {
        int width = getWidth();
        reshape(getX(), getY(), width - 1, getHeight());
        reshape(getX(), getY(), width, getHeight());
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();
        Object value = evt.getNewValue();
        
        if (propertyName == null) {
            return;
        }
        
        if ("texture.loadComplete".equals(propertyName)
                || "texture.doDisplay".equals(propertyName)) {

            //Invert if necessary
            setActionsInView(ActionW.INVERT_LUT.cmd(), false, false);

            Object actionData = getActionData(ActionW.PRESET.cmd());
            int size = 0;
            if (actionData instanceof List) {
                size = ((List) actionData).size();
                List<PresetWindowLevel> presetList =
                        ((TextureDicomSeries) getParentImageSeries())
                        .getPresetList(true, true);
                if (presetList.size() > size) {
                    Container parent = getParent();
                    if (parent instanceof ViewsGrid) {
                        List<GridElement> views = ((ViewsGrid) parent).getViews();
                        for (GridElement gridElement : views) {
                            if (gridElement.getComponent() instanceof ViewTexture) {
                                ((ViewTexture) gridElement.getComponent())
                                        .updateWindowLevelActions(
                                        (TextureDicomSeries) getParentImageSeries());
                            }
                        }
                    }  
                }
            }
            
            if (getParentImageSeries() != null && levelAction.getMax() != getParentImageSeries().windowingMaxInValue) {
                updateWindowLevelLimits((TextureDicomSeries) getParentImageSeries());
            }
            repaint();
        } else if (propertyName.endsWith("RefreshTexture")) {
            refreshTexture();
        
        } else if (propertyName.startsWith(EventPublisher.VIEWER_DO_ACTION)) {
            String action = propertyName.substring(
                        propertyName.lastIndexOf(".") + 1);
            setActionsInView(action, value, true);
        } else if (evt.getSource() instanceof ControlAxes) {
            if ("rotation".equals(propertyName)) {
                scrollSeriesAction.setMinMaxValue(1, getTotalSlices(),
                        getCurrentSlice());
                renderSupp.setDirty(true);

                String old = GraphicsModel.getRotationDesc(
                        (Quat4d) evt.getOldValue());
                String current = GraphicsModel.getRotationDesc(
                        (Quat4d) evt.getNewValue());
                
                if (old != null && !old.equals(current)) {
                    handleGraphicsLayer(-1);
                }
            } else if (propertyName.startsWith("slice")
                    && controlAxes != null
                    && propertyName.endsWith(
                    Integer.toString(controlAxes.getIndex(this)))) {

                renderSupp.setDirty(true);
                int old = (Integer) evt.getOldValue();
                handleGraphicsLayer(old);

                scrollSeriesAction.setValueWithoutTriggerAction(getCurrentSlice());
            }
        } 
    }
    
    @Override
    public void setSlice(final int slice) {
        if (hasContent()) {
            int old = getCurrentSlice();
            super.setSlice(slice);
            
            if (controlAxes == null) {
                renderSupp.setDirty(true);
                handleGraphicsLayer(old);
            }
        }
    }
    
    /*******************************************
     *      Action State Suport
     ********************************************/
    
    protected void initActionWState() {
        
        initWindowLevelActions();
        
        actionsInView.put(ActionWA.VOLUM_RENDERING.cmd(), volumetricRendering);
        actionsInView.put(ActionWA.VOLUM_CENTER_SLICING.cmd(), volumetricCenterSlicing);
        actionsInView.put(ActionWA.VOLUM_DITHERING.cmd(), volumetricDithering);
        actionsInView.put(ActionWA.VOLUM_LIGHT.cmd(), volumetricLighting);        
        actionsInView.put(ActionWA.VOLUM_QUALITY.cmd(), volumetricQuality);
        
        actionsInView.put(ActionWA.SMOOTHING.cmd(), new ActionDataModel(
                ActionWA.SMOOTHING, null, true) {
                    @Override
                    public void setActionValue(Object value) {
                        super.setActionValue(value);
                        interpolate = (Boolean) value;
                        
                        EventPublisher.getInstance().publish(new PropertyChangeEvent(
                                ViewTexture.this, EventPublisher.VIEWER_ACTION_CHANGED
                                + ActionWA.SMOOTHING.cmd(), null, interpolate));
                    }
                });
        
        actionsInView.put(ActionW.FLIP.cmd(), new ActionDataModel(
                ActionW.FLIP, null, false) {
                    @Override
                    public void setActionValue(Object value) {
                        super.setActionValue(value);
                        flippedHorizontally = (Boolean) value;
                        updateAffineTransform();
                        EventPublisher.getInstance().publish(new PropertyChangeEvent(
                            ViewTexture.this, EventPublisher.VIEWER_ACTION_CHANGED
                            + ActionW.FLIP.cmd(), null, flippedHorizontally));                
                    }                    
                });
        
        actionsInView.put(ActionW.CROSSHAIR.cmd(),
                new ActionDataModel(ActionW.CROSSHAIR, null, null) {
                    public boolean isEnabled() {
                        return (controlAxes != null || controlAxesToWatch != null);
                    }
                });
        
        actionsInView.put(ActionW.SORTSTACK.cmd(), new ActionDataModel(
                ActionW.SORTSTACK, SortSeriesStack.getValues(),
                SortSeriesStack.instanceNumber));
        actionsInView.put(ActionW.INVERSESTACK.cmd(),
                new ActionDataModel(ActionW.INVERSESTACK, null, false));
        actionsInView.put(ActionW.ZOOM.cmd(), new ActionDataModel(
                ActionW.ZOOM, null, -1) {
            @Override
            public Object getActionData() {
                return zoomAction.getModel();
            }
            @Override
            public void setActionValue(final Object value) {
                if (value instanceof Double) {
                    double zoomVal = (Double) value;
                    if ((Double) value <= 0.0) {                        
                        zoomVal = graphsLayer.getBestFitViewScale(
                                new Dimension(getWidth(), getHeight()));
                        setActionsInView(ActionWA.BEST_FIT.cmd(), true, false);
                    } else {
                        setActionsInView(ActionWA.BEST_FIT.cmd(), false, false);
                    }
                    setZoom(zoomVal, true);
                    if (zoomVal != GridViewUI.sliderValueToViewScale(
                            zoomAction.getValue()) && hasContent()) {
                        zoomAction.setValueWithoutTriggerAction(
                               GridViewUI.viewScaleToSliderValue(
                                getActualDisplayZoom()));
                    }
                    updateAffineTransform();
                    fixPosition();
                }
            }
            @Override
            public Object getActionValue() {  
                if (hasContent()) {
                    return getActualDisplayZoom();
                }
                return 1;
            }
        });
        actionsInView.put(ActionWA.BEST_FIT.cmd(), true);
        
        actionsInView.put(ActionW.SCROLL_SERIES.cmd(), new ActionDataModel(
                ActionW.SCROLL_SERIES, null, 1) {
            @Override
            public Object getActionValue() {
                if (hasContent()) {
                    return getCurrentSlice();
                }
                return null;
            }
            @Override
            public Object getActionData() {
                return scrollSeriesAction.getModel();
            }
        });
        actionsInView.put(ActionW.ROTATION.cmd(), new ActionDataModel(
                ActionW.ROTATION, rotationAction.getModel(), 0) {
            @Override
            public Object getActionValue() {
                return (int) Math.round(
                        Math.toDegrees(getRotationOffset()));
            }
            @Override
            public Object getActionData() {
                return rotationAction.getModel();
            }
            @Override
            public void setActionValue(Object value) {
                if (value instanceof Integer) {
                    rotationAction.setValue((Integer) value);
                }
            }
        });
        actionsInView.put(ActionW.DRAW.cmd(), true);
        
        //MIP Actions:
        final TextureImageCanvas.MipOption[] options = new TextureImageCanvas.MipOption[] {
            TextureImageCanvas.MipOption.None, TextureImageCanvas.MipOption.Minimum,
            TextureImageCanvas.MipOption.Average, TextureImageCanvas.MipOption.Maximum};
        actionsInView.put(ActionWA.MIP_OPTION.cmd(),
                new ActionDataModel(ActionWA.MIP_OPTION,
                options, mipOption) {
            @Override
            public void setActionValue(Object value) {
                if (value instanceof TextureImageCanvas.MipOption) {                    
                    super.setActionValue(value);
                    mipOption = (TextureImageCanvas.MipOption) value;
                    renderSupp.setDirty(true);
                    graphsLayer.updateAllLabels(ViewTexture.this);
                    repaint();
                    
                    EventPublisher.getInstance().publish(
                        new PropertyChangeEvent(ViewTexture.this, 
                        EventPublisher.VIEWER_ACTION_CHANGED 
                        + ActionWA.MIP_OPTION.cmd(), null, mipOption));
                }
            }
        });
        actionsInView.put(ActionWA.MIP_DEPTH.cmd(),
                new ActionDataModel(ActionWA.MIP_DEPTH, null, mipDepth) {
            @Override
            public void setActionValue(Object value) {
                if (value instanceof Double) {
                    super.setActionValue(value);
                    mipDepth = (Double) value; 
                    renderSupp.setDirty(true);
                    graphsLayer.updateAllLabels(ViewTexture.this);
                    repaint();
                 }
            }
            @Override
            public Object getActionData() {
                if (hasContent()) {
                    return getTotalSlices();
                }
                return 1;
            }
        });
        actionsInView.put(ActionWA.LOCATION.cmd(), new ActionDataModel(
                ActionWA.LOCATION, null, null) {
            @Override
            public Object getActionValue() {
                if (hasContent() && isShowingAcquisitionAxis()) {
                    int currentSlice = getCurrentSlice();
                    double[] val = (double[]) getSeriesObject().getTagValue(
                            TagW.SlicePosition, currentSlice - 1);
                    if (val != null) {
                        return (val[0] + val[1] + val[2]);
                    }
                }
                return null;
            }
            @Override
            public void setActionValue(Object value) {                   
                if (value instanceof Double && hasContent()
                        && isShowingAcquisitionAxis()) {
                    
                    int index = getSeriesObject().getNearestSliceIndex(
                            (Double) value);
                    if (index >= 0) {
                        //Texture is 0 to N-1; action is 1 to N
                        scrollSeriesAction.setValue(index + 1);
                    }
                }
            }    
        });
        
    }
    
    @Override
    public Object getActionValue(String action) {
        if (action == null) {
            return null;
        }
        Object get = actionsInView.get(action);
        if (get instanceof ActionDataModel) {
            return ((ActionDataModel) get).getActionValue();
        }
        return get;
    }
        
    /**
     * Set an Action in this view.
     * 
     * If repaint is true, it also calls "repaint()". This call gets the same
     * result as a call to "display()".
     * 
     * @param action Action name.
     * @param value Action value.
     * @param repaint True if view is to be repainted.
     */
    public void setActionsInView(final String action, final Object value,
            final boolean repaint) {

        Object get = actionsInView.get(action);
        if (get instanceof ActionDataModel) {
            ((ActionDataModel) get).setActionValue(value);
        } else {
            actionsInView.put(action, value);

            if (ActionWA.VOLUM_QUALITY.cmd().equals(action)) {
                volumetricQuality = (Integer) value;
            } else if (ActionWA.VOLUM_CENTER_SLICING.cmd().equals(action)) {
                volumetricCenterSlicing = (Boolean) value;
            } else if (ActionWA.VOLUM_RENDERING.cmd().equals(action)) {
                volumetricRendering = (Boolean) value;
            } else if (ActionWA.VOLUM_DITHERING.cmd().equals(action)) {
                volumetricDithering = (Boolean) value;
            } else if (ActionWA.VOLUM_LIGHT.cmd().equals(action)) {
                volumetricLighting = (Boolean) value;
            } else if (ActionWA.SMOOTHING.cmd().equals(action)) {
                interpolate = (Boolean) value;
            }
        }

        if (repaint) {
            repaint();
        }

    }
    
    private void updateAffineTransform() {
        Boolean flip = (Boolean) getActionValue(ActionW.FLIP.cmd());  
        Integer rotationAngle = (Integer) getActionValue(ActionW.ROTATION.cmd());
        graphsLayer.updateAffineTransform(rotationAngle, flip);
        
        renderSupp.setDirty(true);
    }

    @Override
    public Font getLayerFont() {
        int fontSize = (int) Math.ceil(10 / ((this.getGraphics().getFontMetrics(
                FontTools.getFont12()).stringWidth("0123456789") * 7.0)
                / getWidth()));
        fontSize = fontSize < 6 ? 6 : fontSize > 16 ? 16 : fontSize;
        return new Font("SansSerif", 0, fontSize);
    }
    
    /**
     * Converts a position on texture`s coordinates system (0 to 1) to
     * image`s coordinates system.
     * X e Y: 0 to n-1; (number of pixels)
     * Z: 1 to n. (n = number of slices)
     * 
     * @param coordinatesTexture position on texture`s coord. system.
     * @param imageSize Size or images and stack.
     * @return Position on image`s coordinates System.
     */
    public Point3d getImageCoordinatesFromMouse(
            final Vector3d coordinatesTexture, final Vector3d imageSize) {
        Point3d p3 = new Point3d(
                (Math.round(coordinatesTexture.x * imageSize.x)),
                (Math.round(coordinatesTexture.y * imageSize.y)),
                (Math.round((coordinatesTexture.z * imageSize.z) + 1)));
        return p3;
    }
    
    @Override
    public void setSeries(TextureDicomSeries series) {
        if (hasContent()) {
            handleGraphicsLayer(getCurrentSlice());
            
            //Finds out if texture is present in other view. If not,
            //must interrupt factory.
            if (!getSeriesObject().equals(series)
                    && !getSeriesObject().isFactoryDone()) { 
                boolean open = false;
                synchronized (UIManager.VIEWER_PLUGINS) {
                    List<ViewerPlugin<?>> plugins = UIManager.VIEWER_PLUGINS;
                    pluginList: for (final ViewerPlugin plugin : plugins) {
                        if (plugin instanceof AbstractViewsContainer) {
                            List<GridElement> views =
                                    ((AbstractViewsContainer) plugin)
                                    .getViewsGrid().getViews();
                            for (GridElement gridElement : views) {
                                if (gridElement.getComponent() instanceof ViewTexture
                                        && !gridElement.getComponent().equals(this)
                                        && getSeriesObject().equals(
                                        ((ViewTexture) gridElement.getComponent()).getSeriesObject())) {
                                    open = true;
                                    break pluginList;
                                }
                            }
                        }
                    }
                }
                if (!open) {
                    getSeriesObject().interruptFactory();
                }
            }
        }
        
        setImageSeries(series);
        if (series != null) {
            final Rectangle modelArea = new Rectangle(0, 0,
                    series.getSliceWidth(), series.getSliceHeight());
            Rectangle2D area = graphsLayer.getViewModel().getModelArea();
            if (!modelArea.equals(area)) {
                ((DefaultViewModel) graphsLayer.getViewModel())
                        .adjustMinViewScaleFromImage(
                        modelArea.width, modelArea.height);
                graphsLayer.getViewModel().setModelArea(modelArea);
            }            
            
            //internal defaults 
            setSlice(0);
            scrollSeriesAction.setMinMaxValueWithoutTriggerAction(
                    1, getTotalSlices(), getCurrentSlice());
            updateWindowLevelActions(series);
            
            series.getSeries().setOpen(true);
        }
    }
        
    private void refreshTexture() {
        MediaSeries series = getSeries();
        if (series != null && getSeriesObject() != null) {
            TextureDicomSeries seriesObject = getSeriesObject();
            try {
                TextureDicomSeries texture = new ImageSeriesFactory().createImageSeries(
                series, seriesObject.getSeriesComparator(), true);
                
                setSeries(texture);
                
                seriesObject.dispose();
                seriesObject.discardTexture();
            } catch (Exception ex) {
                LOGGER.info(
                        "Failed creating texture: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    @Override
    public boolean hasContent() {
        return (getParentImageSeries() != null);
    }
    
    @Override
    public boolean isContentReadable() {
        return hasContent();
    }

    @Override
    public void disposeView() {
        graphsLayer.dispose();
        super.dispose();
    }

    public void applyProfile(String profile, ControlAxes controlAxes) {
        
        //Clan ControlAxes listener
        if (this.controlAxes != null) {
            this.controlAxes.removePropertyChangeListener(this);
        }
        
        if (profile != null && profile.startsWith("MPR")
                && controlAxes != null) {
            
            setActionsInView(ActionWA.SMOOTHING.cmd(), true, false);            
            if (profile.endsWith("AXIAL")) {
                controlAxes.setControlledCanvas(0, this);
                controlAxes.addPropertyChangeListener(this);
            } else if (profile.endsWith("CORONAL")) {
                fixedAxis = TextureImageCanvas.FixedAxis.HorizontalAxis;
                controlAxes.setControlledCanvas(1, this);
                controlAxes.addPropertyChangeListener(this);
            } else if (profile.endsWith("SAGITTAL")) {
                fixedAxis = TextureImageCanvas.FixedAxis.VerticalAxis;
                controlAxes.setControlledCanvas(2, this);
                //Works for the axial case!
                setRotationOffset(-Math.PI / 2.0);
                controlAxes.addPropertyChangeListener(this);
            } else if (profile.endsWith("3D")) {
                controlAxes.addWatchingCanvas(this);
                setActionsInView(ActionWA.VOLUM_RENDERING.cmd(), true, false);
                setActionsInView(ActionWA.VOLUM_CENTER_SLICING.cmd(), false, false);
                setActionsInView(ActionWA.VOLUM_DITHERING.cmd(), true, false);
                setActionsInView(ActionWA.VOLUM_LIGHT.cmd(), true, false);
                setActionsInView(ActionWA.VOLUM_QUALITY.cmd(), 300, false);
                
                setActionsInView(ActionW.LUT.cmd(),
                        StaticHelpers.LUT_VOLUMETRIC, false);
            }
            
            scrollSeriesAction.setMinMaxValueWithoutTriggerAction(
                    1, getTotalSlices(), getCurrentSlice());           
            repaint();
        } else {
            //Clear profile (may be reusing a view)
            fixedAxis = TextureImageCanvas.FixedAxis.AcquisitionAxis;
            this.controlAxes = null;
            setRotationOffset(0);
            controlAxesToWatch = null;
            setActionsInView(ActionWA.VOLUM_RENDERING.cmd(), false, false);
            
            if (getParentImageSeries() != null) {
                scrollSeriesAction.setMinMaxValueWithoutTriggerAction(
                        1, getTotalSlices(), getCurrentSlice());  
            }
            repaint();
        }
    }

    @Override
    public MediaSeries getSeries() {
        ImageSeries pis= getParentImageSeries();
        if (pis instanceof TextureDicomSeries) {
            return ((TextureDicomSeries) pis).getSeries();
        }
        return null;
    }

    @Override
    public double[] getImagePatientOrientation() {
        if (getParentImageSeries() != null) {
            if (controlAxesToWatch != null) {
                return null; //Means it's a volumetric view
            }
            if (controlAxes == null) {
                //TODO what if they have diferent orientations?
                return ((TextureDicomSeries) getParentImageSeries())
                        .getOriginalSeriesOrientationPatient();
            } else {
                Matrix3d mo = controlAxes.getOrientationForCanvas(this);
                if (mo != null) {
                    return new double[]{
                                mo.m00, mo.m10, mo.m20, mo.m01, mo.m11, mo.m21};
                }
            }
        }
        return null;
    }
    
    @Override
    public String[] getOrientationStrings() {
        double[] ipo = getImagePatientOrientation();
        if (ipo != null && ipo.length >= 6) {
            double rotation = 0;
            Boolean flip = false;
            //Need to correct for rotation if does not came from controlAxes.
            if (!isActionEnabled(ActionW.CROSSHAIR.cmd())) {
                Object actionValue = getActionValue(ActionW.ROTATION.cmd());
                if (actionValue instanceof Integer) {
                    rotation = (Integer) actionValue;
                }
            }
            
            Object flipValue = getActionValue(ActionW.FLIP.cmd());
            if (flipValue instanceof Boolean) {
                flip = (Boolean) flipValue;
            }
            return DisplayUtils.getOrientationFromImgOrPat(
                    ipo, (int) rotation, flip);
        }
        return null;
    }
    
    public void resetAction(String cmd) {    

        // Mip Option
        if (cmd == null || ActionWA.MIP_OPTION.cmd().equals(cmd)) {
            setActionsInView(ActionWA.MIP_OPTION.cmd(), TextureImageCanvas.MipOption.None, true);
        }
        // Mip Depth
        if (cmd == null || ActionWA.MIP_DEPTH.cmd().equals(cmd)) {
            setActionsInView(ActionWA.MIP_DEPTH.cmd(), 0.3, true);            
            Object data = getActionData(ActionWA.MIP_DEPTH.cmd());
                    
            if (data instanceof Integer) {                
                Object[] values = {data, mipDepth};
                EventPublisher.getInstance().publish(
                    new PropertyChangeEvent(ViewTexture.this, 
                    EventPublisher.VIEWER_ACTION_CHANGED 
                    + ActionWA.MIP_DEPTH.cmd(), null, values));
            }
        }

        // ControlAxes
        if ((cmd == null || "resetToAquisition".equals(cmd))
                && controlAxes != null) {
            controlAxes.reset();
            //must repaint all views
            getParent().repaint();
        }
        
        if ("resetToAxial".equals(cmd) && controlAxes != null) {
            controlAxes.resetWithOrientation();
            //must repaint all views
            getParent().repaint();
            
            TextureImageCanvas[] canvases = controlAxes.getCanvases();
            for (TextureImageCanvas canv : canvases) {
                if (canv instanceof ViewTexture) {
                    ((ViewTexture) canv).handleGraphicsLayer(-1);
                }
            }
        }
    }

    @Override
    public Object getActionData(String command) {
        Object get = actionsInView.get(command);
        if (get instanceof ActionDataModel) {
            return ((ActionDataModel) get).getActionData();
        }
        return null;
    }

    @Override
    public boolean isActionEnabled(String command) {
        if (ActionW.CROSSHAIR.cmd().equals(command)) {
            return controlAxes != null;
        }
        Object get = actionsInView.get(command);
        if (get instanceof ActionDataModel) {
            return ((ActionDataModel) get).isActionEnabled();
        } 
        return false;
    }


    private boolean isContentPhotometricInterpretationInverse() {
        TextureDicomSeries seriesObject = getSeriesObject();
        if (seriesObject != null) {
            //Dont know any serie with mixed results here, so keep simple.
            return seriesObject.isPhotometricInterpretationInverse(0);
        }
        return false;
    }

    private void updateWindowLevelActions(TextureDicomSeries series) {
        updateWindowLevelLimits(series);
        
        //Presets:
        Object get = actionsInView.get(ActionW.PRESET.cmd());
        if (get instanceof ActionDataModel) {
            ActionDataModel model = (ActionDataModel) get;
            if (model.getActionData() instanceof List) {
                model.setActionValue(((List) model.getActionData()).get(0));
            }
            EventPublisher.getInstance().publish(new PropertyChangeEvent(
                    this, EventPublisher.VIEWER_ACTION_CHANGED
                    + ActionW.PRESET.cmd(), null,
                    model.getActionData()));
            
        }
    }
    
    private void updateWindowLevelLimits(TextureDicomSeries series) {
        int fullDynamicWidth = series.windowingMaxInValue
                    - series.windowingMinInValue;
        windowAction.setMinMax(1, fullDynamicWidth);
        levelAction.setMinMax(series.windowingMinInValue,
                series.windowingMaxInValue);
    }
    
    public boolean isShowingAcquisitionAxis() {
        double proximity = getProximityToOriginalAxis();
        if (TextureImageCanvas.FixedAxis.AcquisitionAxis.equals(fixedAxis)) {
            if (Math.abs(proximity) < (1 - WARNING_TOLERANCE)) {
                return false;
            }
        } else if (TextureImageCanvas.FixedAxis.VerticalAxis.equals(fixedAxis)
                || TextureImageCanvas.FixedAxis.HorizontalAxis.equals(fixedAxis)) {
            if (Math.abs(proximity) > WARNING_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    /**
     * Content pixel size.
     * Will return 0 if its unknown ot not trustable.
     * @return Content's pixel size.
     */
    public double getShowingPixelSize() {
        //Se tem o pixelSpacing é consistente na textura
        double acqSpacing = 0;
        if (getParentImageSeries() instanceof TextureDicomSeries) {
            TextureDicomSeries ser = (TextureDicomSeries) getParentImageSeries();
            double[] aps = ser.getAcquisitionPixelSpacing();
            Vector3d dimMultiplier = ser.getDimensionMultiplier();

            if (aps != null && aps.length == 2 && aps[0] == aps[1]
                    && dimMultiplier.x == dimMultiplier.y) {
                acqSpacing = aps[0];// dimMultiplier.x;
                
                //Acquisition plane?
                if (isShowingAcquisitionAxis() || ser.isSliceSpacingRegular()) {
                    return acqSpacing;
                }
            }

            if (aps == null &&  (isShowingAcquisitionAxis() || ser.isSliceSpacingRegular())) {
                return 1;
            }
        }
        return 0;
    }


    @Override
    public Component getComponent() {
        return this;
    }

    public MouseActionAdapter getMouseAdapter(String action) {
        if (action.equals(ActionW.CROSSHAIR.cmd())) {
            return crosshairAction;
        } else if (action.equals(ActionW.WINDOW.cmd())) {
            return windowAction;
        } else if (action.equals(ActionW.LEVEL.cmd())) {
            return levelAction;
        } else if (action.equals(ActionW.WINLEVEL.cmd())) {
            return levelAction;
        } else if (action.equals(ActionW.ZOOM.cmd())) {
            return zoomAction;
        } else if (action.equals(ActionW.SCROLL_SERIES.cmd())) {
            return scrollSeriesAction;
        } else if (action.equals(ActionW.ROTATION.cmd())) {
            return rotationAction;
        } else if (action.equals(ActionW.MEASURE.cmd())
                && controlAxesToWatch == null) {
            return measureAdapter;
        }
        return null;
    }

    @Override
    public TextureDicomSeries getSeriesObject() {
        return (TextureDicomSeries) getParentImageSeries();
    }

    private void initWindowLevelActions() {
        actionsInView.put(ActionW.PRESET.cmd(), new ActionDataModel(
            ActionW.PRESET, null, null) {
                @Override
                public Object getActionData() {
                    if (getParentImageSeries() instanceof TextureDicomSeries) {
                        return ((TextureDicomSeries) getParentImageSeries())
                                .getPresetList(true, false);
                    }
                    return null;
                }
                @Override
                public void setActionValue(Object value) {
                    Object oldVal = getActionValue();
                    if (value instanceof PresetWindowLevel) {
                        PresetWindowLevel preset = (PresetWindowLevel) value;
                        windowAction.setValue(preset.getWindow().intValue());
                        levelAction.setValue(preset.getLevel().intValue());
                        repaint();
                    }
                    super.setActionValue(value);
                    
                    if (oldVal != value) {
                        EventPublisher.getInstance().publish(
                                new PropertyChangeEvent(ViewTexture.this,
                                EventPublisher.VIEWER_ACTION_CHANGED
                                + ActionW.PRESET.cmd(), oldVal, value));
                    }
                }
            });

        actionsInView.put(ActionW.WINDOW.cmd(), new ActionDataModel(
            ActionW.WINDOW, null, null) {
                @Override
                public Object getActionData() {
                    return windowAction.getModel();
                }
                @Override
                public Object getActionValue() {
                    return (float) windowAction.getValue();
                }
                @Override
                public void setActionValue(Object value) {
                    if (value instanceof Integer) {                        
                        windowAction.setValue((Integer) value);
                    } else if (value instanceof Float) {
                        windowAction.setValue((Integer) Math.round((Float) value));
                    }
                }
            });
        actionsInView.put(ActionW.LEVEL.cmd(), new ActionDataModel(
            ActionW.LEVEL, null, null) {
                @Override
                public Object getActionData() {
                    return levelAction.getModel();
                }
                @Override
                public Object getActionValue() {
                    return (float) levelAction.getValue();
                }
                @Override
                public void setActionValue(Object value) {
                    if (value instanceof Integer) {                        
                        levelAction.setValue((Integer) value);        
                    } else if (value instanceof Float) {
                        levelAction.setValue((Integer) Math.round((Float) value));
                    }
                }
            });
        actionsInView.put(ActionW.INVERT_LUT.cmd(),
                new ActionDataModel(ActionW.INVERT_LUT, null, false) {
            public void setActionValue(Object value) {
                super.setActionValue(value);
                boolean res = isContentPhotometricInterpretationInverse();
                if (res) {
                    inverse = !(Boolean) value;
                } else {
                    inverse = (Boolean) value;
                }
                //publish
                EventPublisher.getInstance().publish(
                    new PropertyChangeEvent(ViewTexture.this,
                    EventPublisher.VIEWER_ACTION_CHANGED
                    + ActionW.INVERT_LUT.cmd(), null, (Boolean) value));
            }
        });
        actionsInView.put(ActionW.LUT.cmd(), new ActionDataModel(ActionW.LUT,
                getColorMaskList(), StaticHelpers.LUT_NONE) {
        @Override
        public void setActionValue(Object value) {
            super.setActionValue(value);
            if (value instanceof ColorMask) {
                if (value == StaticHelpers.LUT_NONE) {
                    colorMaskEnabled = false;
                } else {
                    setColorMask((ColorMask) value);
                }
                EventPublisher.getInstance().publish(
                    new PropertyChangeEvent(ViewTexture.this,
                    EventPublisher.VIEWER_ACTION_CHANGED
                    + ActionW.LUT.cmd(), null, value));
            }
        }   
     });
        actionsInView.put(ActionW.FILTER.cmd(),
                new ActionDataModel(ActionW.FILTER,
                        getKernelList(), getKernelList().get(0)) {
                @Override
                public void setActionValue(Object value) {
                    super.setActionValue(value);
                    if (value instanceof StaticHelpers.TextureKernel) {
                        if (value == getKernelList().get(0)) {
                            getSeriesObject().getTextureData().destroyReplacementTexture();
                        } else {
                            getSeriesObject().getTextureData().destroyReplacementTexture();
                            new CLConvolution(ViewTexture.this,
                                    ((StaticHelpers.TextureKernel) value).getPreset()).work();
                        }
                        repaint();
                        
                        EventPublisher.getInstance().publish(
                            new PropertyChangeEvent(ViewTexture.this,
                            EventPublisher.VIEWER_ACTION_CHANGED
                            + ActionW.FILTER.cmd(), null, value));
                    }
                }
             });
    }
    
    @Override
    public void moveImageOffset(int width, int height) {
        if (hasContent()) {
            super.moveImageOffset(width, height);
        }
        renderSupp.setDirty(true);
    }

    @Override
    public ViewModel getViewModel() {
        return graphsLayer.getViewModel();
    }

    @Override
    public AbstractLayerModel getLayerModel() {
        return graphsLayer.getLayerModel();
    }

    @Override
    public Point2D getImageCoordinatesFromMouse(int x, int y) {
        return graphsLayer.getImageCoordinatesFromMouse(x, y);
    }

    @Override
    public AffineTransform getAffineTransform() {
        return graphsLayer.getAffineTransform();
    }

    public MeasurementsAdapter getMeasurementsAdapter() {
        if (hasContent()) {
            String abr = getSeriesObject().getPixelSpacingUnit().getAbbreviation();
            double[] pixelSpacing = getSeriesObject().getAcquisitionPixelSpacing();
            double pixelSize = 1;
            if (pixelSpacing != null) {
                pixelSize = pixelSpacing[0];
            }
            return new MeasurementsAdapter(pixelSize, 0, 0, false, 0, abr);
        }
        return null;
    }

    @Override
    public void registerDefaultListeners() {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) { 
                if ((Boolean) getActionValue(ActionWA.BEST_FIT.cmd())) {
                    setActionsInView(ActionW.ZOOM.cmd(), 0.0, false); 
                }
                fixPosition();
                updateAffineTransform();
                repaint();
            }
        });
    }

    public RenderSupport getRenderSupport() {
        return renderSupp;
    }

    @Override
    public GeometryOfSlice getSliceGeometry() {
        if (hasContent() && isShowingAcquisitionAxis()) {
            return getSeriesObject().getSliceGeometry(getCurrentSlice());
        }
        return null;
    }


    public AnnotationsLayer getInfoLayer() {
        return infoLayer;
    }


    private void handleGraphicsLayer(int old) {
        if (old != getCurrentSlice()) {
            graphsLayer.removeGraphics();
        }
    }

    
    public class Cross3dListener extends MouseActionAdapter implements ActionState {
        @Override
        public void mousePressed(final MouseEvent evt) {
            if (!evt.isConsumed() && (evt.getModifiersEx() & getButtonMaskEx()) != 0) {
                mouseDragReset(evt.getPoint());
            }
        }
        @Override
        public void mouseEntered(MouseEvent e) {
            onMouseMoved(e.getPoint());
        }
        @Override
        public void mouseExited(MouseEvent e) {
            onMouseMoved(e.getPoint());
        }
        @Override
        public void mouseMoved(MouseEvent e) {
            onMouseMoved(e.getPoint());
        }
        @Override
        public void mouseDragged(MouseEvent e) {
            if (!e.isConsumed() && (e.getModifiersEx() & getButtonMaskEx()) != 0) {
                onMouseDragged(e.getPoint());
            }
        }
        @Override
        public void enableAction(boolean enabled) { /*Empty*/ }
        @Override
        public ActionW getActionW() {return ActionW.CROSSHAIR;}

        @Override
        public boolean registerActionState(Object c) { return false; }

        @Override
        public void unregisterActionState(Object c) { /*Empty*/ }

        @Override
        public boolean isActionEnabled() { return true; }
        
    }

    /**
     * Uses DefaultViewModel to reuse the Listerer system, but all the
     * information cames from the TextureImageCanvas state.
     */
    private class TextureViewModel extends DefaultViewModel {

        @Override
        public double getModelOffsetX() {
            if (hasContent()) {
                return -(getUnrotatedImageRect().getX() / getActualDisplayZoom());
            }
            return 0;
        }

        @Override
        public double getModelOffsetY() {
            if (hasContent()) {
                return -(getUnrotatedImageRect().getY() / getActualDisplayZoom());
            }
            return 0;
        }
        
        @Override
        public double getViewScale() {
            if (hasContent()) {
                double displayZoom = ViewTexture.this.getActualDisplayZoom();
                if (displayZoom <= 0) { //avoid error trying to generate inverseAffine
                    displayZoom = 1;
                }
                return displayZoom;
            }
            return 1;
        }
    }
    
}
