/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
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
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.RenderedImage;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.ButtonGroup;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.vecmath.Matrix3d;
import javax.vecmath.Point3d;
import javax.vecmath.Quat4d;
import javax.vecmath.Vector2d;
import javax.vecmath.Vector3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.AnnotationsLayer;
import org.weasis.core.ui.editor.image.CalibrationView;
import org.weasis.core.ui.editor.image.DefaultView2d.ZoomType;
import org.weasis.core.ui.editor.image.GraphicMouseHandler;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.Panner;
import org.weasis.core.ui.editor.image.PannerListener;
import org.weasis.core.ui.editor.image.PixelInfo;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.SynchData.Mode;
import org.weasis.core.ui.editor.image.SynchEvent;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.graphic.AbstractDragGraphic;
import org.weasis.core.ui.graphic.BasicGraphic;
import org.weasis.core.ui.graphic.DragLayer;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.LineGraphic;
import org.weasis.core.ui.graphic.MeasureDialog;
import org.weasis.core.ui.graphic.PanPoint;
import org.weasis.core.ui.graphic.PanPoint.STATE;
import org.weasis.core.ui.graphic.TempLayer;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.graphic.model.DefaultViewModel;
import org.weasis.core.ui.pref.Monitor;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.MouseEventDouble;
import org.weasis.core.ui.util.TitleMenuItem;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.display.PresetWindowLevel;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;

import br.com.animati.texture.codec.ImageSeriesFactory;
import br.com.animati.texture.codec.StaticHelpers;
import br.com.animati.texture.codec.TextureDicomSeries;
import br.com.animati.texture.mpr3dview.api.ActionWA;
import br.com.animati.texture.mpr3dview.api.CanvasTexure;
import br.com.animati.texture.mpr3dview.api.DisplayUtils;
import br.com.animati.texture.mpr3dview.api.GraphicsModel;
import br.com.animati.texture.mpr3dview.api.PixelInfo3d;
import br.com.animati.texture.mpr3dview.api.RenderSupport;
import br.com.animati.texture.mpr3dview.api.TextureMeasurableLayer;
import br.com.animati.texture.mpr3dview.internal.Messages;
import br.com.animati.texturedicom.ColorMask;
import br.com.animati.texturedicom.ControlAxes;
import br.com.animati.texturedicom.ImageSeries;
import br.com.animati.texturedicom.TextureImageCanvas;
import br.com.animati.texturedicom.cl.CLConvolution;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 18 Jul.
 */
public class ViewTexture extends CanvasTexure implements ViewCanvas<DicomImageElement> {

    /** Class logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ViewTexture.class);

    public enum ViewType {
                          AXIAL, CORONAL, SAGITTAL, VOLUME3D
    };

    static final Shape[] pointer;
    static {
        pointer = new Shape[5];
        pointer[0] = new Ellipse2D.Double(-27.0, -27.0, 54.0, 54.0);
        pointer[1] = new Line2D.Double(-40.0, 0.0, -5.0, 0.0);
        pointer[2] = new Line2D.Double(5.0, 0.0, 40.0, 0.0);
        pointer[3] = new Line2D.Double(0.0, -40.0, 0.0, -5.0);
        pointer[4] = new Line2D.Double(0.0, 5.0, 0.0, 40.0);
    }

    private final PanPoint highlightedPosition = new PanPoint(STATE.Center);
    private int pointerType = 0;

    protected final Color pointerColor1 = Color.black;
    protected final Color pointerColor2 = Color.white;

    /** Tolerance to consider an axix as the Acquisition Axis. */
    private double WARNING_TOLERANCE = 0.0001;

    protected final FocusHandler focusHandler = new FocusHandler();
    private final GraphicMouseHandler graphicMouseHandler = new GraphicMouseHandler(this);

    private AnnotationsLayer infoLayer;
    public static boolean computePixelStats = true;

    private final RenderSupport renderSupp = new RenderSupport(this);
    protected final TextureMeasurableLayer measurableLayer;

    private final ContextMenuHandler contextMenuHandler = new ContextMenuHandler();
    private Cross3dListener crosshairAction = new Cross3dListener();

    protected final Border normalBorder = new EtchedBorder(BevelBorder.LOWERED, Color.gray, Color.white);
    protected final Border focusBorder = new EtchedBorder(BevelBorder.LOWERED, focusColor, focusColor);
    protected final Border lostFocusBorder = new EtchedBorder(BevelBorder.LOWERED, lostFocusColor, lostFocusColor);

    private final ImageViewerEventManager<DicomImageElement> eventManager;

    private ViewType viewType;

    public ViewTexture(ImageViewerEventManager<DicomImageElement> eventManager, ImageSeries parentImageSeries) {
        super(parentImageSeries);
        this.eventManager = eventManager;

        measurableLayer = new TextureMeasurableLayer(this);

        infoLayer = new InfoLayer3d(this);

        DragLayer layer = new DragLayer(getLayerModel(), AbstractLayer.CROSSLINES);
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

        setFocusable(true);

        setBorder(normalBorder);
        // WEA-258 Must be larger to the screens to be resize correctly by the container
        setPreferredSize(new Dimension(4096, 4096));
        setMinimumSize(new Dimension(50, 50));
    }

    public ViewType getViewType() {
        return viewType;
    }

    public void setViewType(ViewType viewType) {
        this.viewType = viewType;
    }

    public String getSeriesObjectClassName() {
        return TextureDicomSeries.class.getName();
    }

    public void showPixelInfos(MouseEvent mouseevent) {
        if (infoLayer != null && getParentImageSeries() != null) {
            Point3d pOriginal = getOriginalSystemCoordinatesFromMouse(mouseevent.getX(), mouseevent.getY());

            PixelInfo3d pixInfo = new PixelInfo3d();
            pixInfo.setPosition3d(pOriginal);

            Rectangle oldBound = infoLayer.getPixelInfoBound();
            oldBound.width = Math.max(oldBound.width, this.getGraphics().getFontMetrics(getLayerFont())
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

            drawLayers(g2d, graphsLayer.getAffineTransform(), graphsLayer.getInverseTransform());

            g2d.translate(offsetX, offsetY);

            drawPointer(g2d);
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

    @Override
    public void drawLayers(Graphics2D g2d, AffineTransform transform, AffineTransform inverseTransform) {
        boolean draw = true;
        Object actionValue = getActionValue(ActionW.DRAW.cmd());
        if (actionValue instanceof Boolean) {
            draw = (Boolean) actionValue;
        }
        if (hasContent() && draw) {
            getLayerModel().draw(g2d, transform, inverseTransform,
                new Rectangle2D.Double(graphsLayer.modelToViewLength(graphsLayer.getViewModel().getModelOffsetX()),
                    graphsLayer.modelToViewLength(graphsLayer.getViewModel().getModelOffsetY()), getWidth(),
                    getHeight()));
        }
    }

    private void drawPointer(Graphics2D g) {
        if (pointerType < 1) {
            return;
        }
        if ((pointerType & CENTER_POINTER) == CENTER_POINTER) {
            drawPointer(g, (getWidth() - 1) * 0.5, (getHeight() - 1) * 0.5);
        }
        if ((pointerType & HIGHLIGHTED_POINTER) == HIGHLIGHTED_POINTER && highlightedPosition.isHighlightedPosition()) {
            // Display the position on the center of the pixel (constant position even with a high zoom factor)
            drawPointer(g, modelToViewX(highlightedPosition.getX() + 0.5),
                modelToViewY(highlightedPosition.getY() + 0.5));
        }
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

        if (propertyName.equals(ActionW.SYNCH.cmd())) {
            propertyChange((SynchEvent) evt.getNewValue());
        }

        if (ImageSeriesFactory.TEXTURE_LOAD_COMPLETE.equals(propertyName)
            || ImageSeriesFactory.TEXTURE_DO_DISPLAY.equals(propertyName)) {

            // Invert if necessary
            // In case of PhotometricInterpretationInverse, it has to be corrected here.
            if (isContentPhotometricInterpretationInverse()) {
                inverse = true;
                actionsInView.put(ActionW.INVERT_LUT.cmd(), inverse);
            }

            // Need to force building preset list again: for cases like neuro-MR:
            // (DICOM preset diferent for each image).
            int size = 0;
            ActionState action = eventManager.getAction(ActionW.PRESET);
            if (action instanceof ComboItemListener) {
                Object[] allItem = ((ComboItemListener) action).getAllItem();
                size = allItem.length;

                List<PresetWindowLevel> presetList =
                    ((TextureDicomSeries) getParentImageSeries()).getPresetList(true, true);
                if (presetList.size() > size) {
                    ((ComboItemListener) action).setDataList(presetList.toArray());
                    // Apply to model, and selected view..
                    ((ComboItemListener) action).setSelectedItem(presetList.get(0));
                    // Apply to all viewers!
                    ImageViewerPlugin<DicomImageElement> container =
                        GUIManager.getInstance().getSelectedView2dContainer();
                    ArrayList<ViewCanvas<DicomImageElement>> imagePanels = container.getImagePanels();
                    for (ViewCanvas<DicomImageElement> panel : imagePanels) {
                        if (panel != GUIManager.getInstance().getSelectedViewPane() && panel instanceof ViewTexture) {
                            if (presetList.get(0) instanceof PresetWindowLevel) {
                                ((ViewTexture) panel).setPresetWindowLevel(presetList.get(0));
                            }
                        }
                    }
                }
            }

            repaint();
            eventManager.updateComponentsListener(this);
        } else if (propertyName.startsWith(EventPublisher.VIEWER_DO_ACTION)) {
            String action = propertyName.substring(propertyName.lastIndexOf(".") + 1);
            setActionsInView(action, value, true);
        } else if (evt.getSource() instanceof ControlAxes) {
            if ("rotation".equals(propertyName)) {
                renderSupp.setDirty(true);

                String old = GraphicsModel.getRotationDesc((Quat4d) evt.getOldValue());
                String current = GraphicsModel.getRotationDesc((Quat4d) evt.getNewValue());

                if (old != null && !old.equals(current)) {
                    handleGraphicsLayer(-1);
                }
            } else if (propertyName.startsWith("slice") && controlAxes != null
                && propertyName.endsWith(Integer.toString(controlAxes.getIndex(this)))) {

                renderSupp.setDirty(true);
                int old = (Integer) evt.getOldValue();
                handleGraphicsLayer(old);
            }
        }
    }

    private void propertyChange(final SynchEvent synch) {
        SynchData synchData = (SynchData) actionsInView.get(ActionW.SYNCH_LINK.cmd());
        if (synchData != null && Mode.None.equals(synchData.getMode())) {
            return;
        }

        for (Entry<String, Object> entry : synch.getEvents().entrySet()) {
            String command = entry.getKey();
            final Object val = entry.getValue();
            if (synchData != null && !synchData.isActionEnable(command)) {
                continue;
            }
            if (command.equals(ActionW.SCROLL_SERIES.cmd())) {
                if (getViewType() != viewType.VOLUME3D) { //If its not a volumetric view
                    setSlice((Integer) val);
                }
            } else if (command.equals(ActionW.WINDOW.cmd())) {
                windowingWindow = (Integer) val;
                repaint();
            } else if (command.equals(ActionW.LEVEL.cmd())) {
                windowingLevel = (Integer) val;
                repaint();
            } else if (command.equals(ActionW.PRESET.cmd())) {
                if (val instanceof PresetWindowLevel) {
                    setPresetWindowLevel((PresetWindowLevel) val);
                } else if (val == null) {
                    setActionsInView(ActionW.PRESET.cmd(), val, false);
                }
            } else if (command.equals(ActionW.LUT_SHAPE.cmd())) {
                // TODO lut shape
            } else if (command.equals(ActionW.ROTATION.cmd()) && val instanceof Integer) {
                if (getViewType() != viewType.VOLUME3D) { //If its not a volumetric view
                    actionsInView.put(ActionW.ROTATION.cmd(), val);
                    setRotationOffset(Math.toRadians((Integer) val));
                    updateAffineTransform();
                    repaint();
                }
            } else if (command.equals(ActionW.RESET.cmd())) {
                reset();
            } else if (command.equals(ActionW.ZOOM.cmd())) {
                double value = (Double) val;
                // Special Cases: -200.0 => best fit, -100.0 => real world size
                if (value != -200.0 && value != -100.0) {
                    zoom(value);
                } else {
                    Object zoomType = actionsInView.get(ViewCanvas.zoomTypeCmd);
                    actionsInView.put(ViewCanvas.zoomTypeCmd, value == -100.0 ? ZoomType.REAL : ZoomType.BEST_FIT);
                    zoom(0.0);
                    actionsInView.put(ViewCanvas.zoomTypeCmd, zoomType);
                }
            } else if (command.equals(ActionW.PAN.cmd())) {
                if (val instanceof PanPoint) {
                    moveOrigin((PanPoint) entry.getValue());
                }
            } else if (command.equals(ActionW.FLIP.cmd())) {
                actionsInView.put(ActionW.FLIP.cmd(), val);
                flippedHorizontally = JMVUtils.getNULLtoFalse(val);
                updateAffineTransform();
                repaint();
            } else if (command.equals(ActionW.LUT.cmd())) {
                setActionsInView(ActionW.LUT.cmd(), val, true);
            } else if (command.equals(ActionW.INVERT_LUT.cmd())) {
                boolean res = isContentPhotometricInterpretationInverse();
                if (res) {
                    inverse = !(Boolean) val;
                } else {
                    inverse = (Boolean) val;
                }
                actionsInView.put(ActionW.INVERT_LUT.cmd(), inverse);
                repaint();
            } else if (command.equals(ActionW.FILTER.cmd())) {
                if (val instanceof StaticHelpers.TextureKernel) {
                    actionsInView.put(ActionW.FILTER.cmd(), val);
                    if (val == GUIManager.kernelList.get(0)) {
                        getSeriesObject().getTextureData().destroyReplacementTexture();
                    } else {
                        getSeriesObject().getTextureData().destroyReplacementTexture();
                        new CLConvolution(ViewTexture.this, ((StaticHelpers.TextureKernel) val).getPreset()).work();
                    }
                    repaint();
                }
            } else if (command.equals(ActionW.SPATIAL_UNIT.cmd())) {
                actionsInView.put(command, val);

                // TODO update only measure and limit when selected view share graphics
                List<Graphic> list = this.getLayerModel().getAllGraphics();
                for (Graphic graphic : list) {
                    graphic.updateLabel(true, this);
                }
            } else if (command.equals(ActionWA.MIP_OPTION.cmd())) {
                if (val instanceof TextureImageCanvas.MipOption) {
                    actionsInView.put(ActionWA.MIP_OPTION.cmd(), val);
                    mipOption = (TextureImageCanvas.MipOption) val;
                    renderSupp.setDirty(true);
                    graphsLayer.updateAllLabels(ViewTexture.this);
                    repaint();
                }
            } else if (command.equals(ActionWA.MIP_DEPTH.cmd())) {
                if (val instanceof Integer) {
                    actionsInView.put(ActionWA.MIP_DEPTH.cmd(), val);
                    mipDepth = (Integer) val / (double) getTotalSlices();
                    renderSupp.setDirty(true);
                    graphsLayer.updateAllLabels(ViewTexture.this);
                    repaint();
                }
            } else if (command.equals(ActionWA.VOLUM_QUALITY.cmd())) {
                setActionsInView(ActionWA.VOLUM_QUALITY.cmd(), val, false);
            } else if (command.equals(ActionWA.VOLUM_CENTER_SLICING.cmd())) {
                setActionsInView(ActionWA.VOLUM_CENTER_SLICING.cmd(), val, true);
            } else if (command.equals(ActionWA.VOLUM_LIGHT.cmd())) {
                setActionsInView(ActionWA.VOLUM_LIGHT.cmd(), val, true);
            } else if (command.equals(ActionWA.SMOOTHING.cmd())) {
                setActionsInView(ActionWA.SMOOTHING.cmd(), val, true);
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
     * Action State Suport
     ********************************************/

    protected void initActionWState() {
        // set unit when texture load (middle image)
        actionsInView.put(ActionW.SPATIAL_UNIT.cmd(), Unit.PIXEL);
        actionsInView.put(zoomTypeCmd, ZoomType.BEST_FIT);
        actionsInView.put(ActionW.ZOOM.cmd(), 0.0);
        actionsInView.put(ActionW.LENS.cmd(), false);
        actionsInView.put(ActionW.DRAW.cmd(), true);
        actionsInView.put(ActionW.INVERSESTACK.cmd(), false);
        actionsInView.put(ActionW.FILTERED_SERIES.cmd(), null);

        actionsInView.put(ActionW.INVERT_LUT.cmd(), false);
        actionsInView.put(ActionW.ROTATION.cmd(), 0);
        actionsInView.put(ActionW.FLIP.cmd(), false);

        actionsInView.put(ActionW.FILTER.cmd(), GUIManager.kernelList.get(0));
        actionsInView.put(ActionW.LUT.cmd(), StaticHelpers.LUT_NONE);

        interpolate = true;
        cubeHelperScale = 0;

        actionsInView.put(ActionWA.VOLUM_RENDERING.cmd(), volumetricRendering);
        actionsInView.put(ActionWA.VOLUM_CENTER_SLICING.cmd(), volumetricCenterSlicing);
        actionsInView.put(ActionWA.VOLUM_DITHERING.cmd(), volumetricDithering);
        actionsInView.put(ActionWA.VOLUM_LIGHT.cmd(), volumetricLighting);
        actionsInView.put(ActionWA.VOLUM_QUALITY.cmd(), volumetricQuality);

        actionsInView.put(ActionWA.MIP_OPTION.cmd(), TextureImageCanvas.MipOption.None);
        actionsInView.put(ActionWA.MIP_DEPTH.cmd(), 5);

        actionsInView.put(ActionWA.SMOOTHING.cmd(), true);
    }

    /**
     * Set an Action in this view.
     * 
     * If repaint is true, it also calls "repaint()". This call gets the same result as a call to "display()".
     * 
     * @param action
     *            Action name.
     * @param value
     *            Action value.
     * @param repaint
     *            True if view is to be repainted.
     */
    @Override
    public void setActionsInView(final String action, final Object value, final boolean repaint) {

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
        } else if (ActionW.LUT.cmd().equals(action)) {
            if (value instanceof ColorMask) {
                actionsInView.put(ActionW.LUT.cmd(), value);
                if (value == StaticHelpers.LUT_NONE) {
                    colorMaskEnabled = false;
                } else {
                    setColorMask((ColorMask) value);
                }
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

    private void setPresetWindowLevel(PresetWindowLevel preset) {
        if (preset != null) {
            windowingWindow = preset.getWindow().intValue();
            windowingLevel = preset.getLevel().intValue();
            // TODO preset.getLutShape()
            repaint();
        }
        setActionsInView(ActionW.PRESET.cmd(), preset, false);
    }

    @Override
    public Font getLayerFont() {
        int fontSize = (int) Math
            .ceil(10 / ((this.getGraphics().getFontMetrics(FontTools.getFont12()).stringWidth("0123456789") * 7.0)
                / getWidth()));
        fontSize = fontSize < 6 ? 6 : fontSize > 16 ? 16 : fontSize;
        return new Font("SansSerif", 0, fontSize);
    }

    public void setSeries(TextureDicomSeries series) {
        TextureDicomSeries old = getSeriesObject();

        if (hasContent()) {
            handleGraphicsLayer(getCurrentSlice());

            // Finds out if texture is present in other view. If not,
            // must interrupt factory.
            if (!old.equals(series) && !getSeriesObject().isFactoryDone()) {
                boolean open = false;
                synchronized (UIManager.VIEWER_PLUGINS) {
                    List<ViewerPlugin<?>> plugins = UIManager.VIEWER_PLUGINS;
                    pluginList: for (final ViewerPlugin plugin : plugins) {
                        if (plugin instanceof View3DContainer) {
                            for (ViewCanvas<DicomImageElement> view : ((View3DContainer) plugin).getImagePanels()) {
                                if (view instanceof ViewTexture && !view.equals(this)
                                    && getSeriesObject().equals(((ViewTexture) view).getSeriesObject())) {
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
            final Rectangle modelArea = new Rectangle(0, 0, series.getSliceWidth(), series.getSliceHeight());
            Rectangle2D area = graphsLayer.getViewModel().getModelArea();
            if (!modelArea.equals(area)) {
                ((DefaultViewModel) graphsLayer.getViewModel()).adjustMinViewScaleFromImage(modelArea.width,
                    modelArea.height);
                graphsLayer.getViewModel().setModelArea(modelArea);
            }
            updateSortStackActions(series);

            // internal defaults
            setSlice(0);

            resetAction(null);

            actionsInView.put(ActionW.SPATIAL_UNIT.cmd(), series.getPixelSpacingUnit());

            series.getSeries().setOpen(true);
        }
        if (old != null && (series == null || !old.getSeries().equals(series.getSeries()))) {
            closingSeries(old.getSeries());
        }
    }

    private void updateSortStackActions(TextureDicomSeries series) {
        Comparator seriesSorter = series.getSeriesSorter();
        if (seriesSorter instanceof SeriesComparator) {
            setActionsInView(ActionW.SORTSTACK.cmd(), seriesSorter, false);
            setActionsInView(ActionW.INVERSESTACK.cmd(), false, false);
        } else {
            for (SeriesComparator sorter : SortSeriesStack.getValues()) {
                if (sorter.getReversOrderComparator().equals(seriesSorter)) {
                    setActionsInView(ActionW.SORTSTACK.cmd(), sorter, false);
                    setActionsInView(ActionW.INVERSESTACK.cmd(), true, false);
                }
            }
        }
    }

    public void refreshTexture() {
        MediaSeries series = getSeries();
        if (series != null && getSeriesObject() != null) {
            TextureDicomSeries seriesObject = getSeriesObject();
            try {
                TextureDicomSeries texture =
                    new ImageSeriesFactory().createImageSeries(series, seriesObject.getSeriesSorter(), true);

                setSeries(texture);

                seriesObject.dispose();
                seriesObject.discardTexture();
            } catch (Exception ex) {
                LOGGER.info("Failed creating texture: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    public boolean isContentReadable() {
        return hasContent();
    }

    @Override
    public void disposeView() {
        disableMouseAndKeyListener();
        removeFocusListener(this);
        ToolTipManager.sharedInstance().unregisterComponent(this);

        MediaSeries series = getSeries();
        if (series != null) {
            closingSeries(series);
            series = null;
        }
    }

    protected void closingSeries(MediaSeries mediaSeries) {
        if (mediaSeries == null) {
            return;
        }
        boolean open = false;
        synchronized (UIManager.VIEWER_PLUGINS) {
            List<ViewerPlugin<?>> plugins = UIManager.VIEWER_PLUGINS;
            pluginList: for (final ViewerPlugin<?> plugin : plugins) {
                List<? extends MediaSeries<?>> openSeries = plugin.getOpenSeries();
                if (openSeries != null) {
                    for (MediaSeries<?> s : openSeries) {
                        if (mediaSeries == s) {
                            // The sequence is still open in another view or plugin
                            open = true;
                            break pluginList;
                        }
                    }
                }
            }
        }
        mediaSeries.setOpen(open);
        // TODO setSelected and setFocused must be global to all view as open
        mediaSeries.setSelected(false, null);
        mediaSeries.setFocused(false);
    }

    public void applyProfile(ViewType viewType2, ControlAxes controlAxes) {

        // Clan ControlAxes listener
        if (this.controlAxes != null) {
            this.controlAxes.removePropertyChangeListener(this);
        }

        if (viewType2 != null && controlAxes != null) {

            setActionsInView(ActionWA.SMOOTHING.cmd(), true, false);
            if (ViewType.AXIAL.equals(viewType2)) {
                controlAxes.setControlledCanvas(0, this);
                controlAxes.addPropertyChangeListener(this);
            } else if (ViewType.CORONAL.equals(viewType2)) {
                fixedAxis = TextureImageCanvas.FixedAxis.HorizontalAxis;
                controlAxes.setControlledCanvas(1, this);
                controlAxes.addPropertyChangeListener(this);
            } else if (ViewType.SAGITTAL.equals(viewType2)) {
                fixedAxis = TextureImageCanvas.FixedAxis.VerticalAxis;
                controlAxes.setControlledCanvas(2, this);
                // Works for the axial case!
                setRotationOffset(-Math.PI / 2.0);
                controlAxes.addPropertyChangeListener(this);
            } else if (ViewType.VOLUME3D.equals(viewType2)) {
                controlAxes.addWatchingCanvas(this);
                setActionsInView(ActionWA.VOLUM_RENDERING.cmd(), true, false);
                setActionsInView(ActionWA.VOLUM_CENTER_SLICING.cmd(), false, false);
                setActionsInView(ActionWA.VOLUM_DITHERING.cmd(), true, false);
                setActionsInView(ActionWA.VOLUM_LIGHT.cmd(), true, false);
                setActionsInView(ActionWA.VOLUM_QUALITY.cmd(), 300, false);

                setActionsInView(ActionW.LUT.cmd(), StaticHelpers.LUT_VOLUMETRIC, false);
            }
        } else {
            // Clear profile (may be reusing a view)
            fixedAxis = TextureImageCanvas.FixedAxis.AcquisitionAxis;
            this.controlAxes = null;
            setRotationOffset(0);
            controlAxesToWatch = null;
            setActionsInView(ActionWA.VOLUM_RENDERING.cmd(), false, false);
        }

        repaint();
    }

    @Override
    public MediaSeries getSeries() {
        ImageSeries pis = getParentImageSeries();
        if (pis instanceof TextureDicomSeries) {
            return ((TextureDicomSeries) pis).getSeries();
        }
        return null;
    }

    public double[] getImagePatientOrientation() {
        if (getParentImageSeries() != null) {
            if (controlAxesToWatch != null) {
                Matrix3d mo = controlAxesToWatch.getOrientationForCanvas(this);
                // System.out.println(" mo: " + mo);
                if (mo != null) {
                    return new double[] { mo.m00, mo.m10, mo.m20, mo.m01, mo.m11, mo.m21 };
                }
            }
            if (controlAxes == null) {
                // TODO what if they have diferent orientations?
                return ((TextureDicomSeries) getParentImageSeries()).getOriginalSeriesOrientationPatient();
            } else {
                Matrix3d mo = controlAxes.getOrientationForCanvas(this);
                if (mo != null) {
                    return new double[] { mo.m00, mo.m10, mo.m20, mo.m01, mo.m11, mo.m21 };
                }
            }
        }
        return null;
    }

    public String[] getOrientationStrings() {
        double[] ipo = getImagePatientOrientation();
        if (ipo != null && ipo.length >= 6) {
            double rotation = 0;
            Boolean flip = false;
            // Need to correct for rotation if does not came from controlAxes.
            if (!crosshairAction.isActionEnabled()) {
                Object actionValue = getActionValue(ActionW.ROTATION.cmd());
                if (actionValue instanceof Integer) {
                    rotation = (Integer) actionValue;
                }
            }

            Object flipValue = getActionValue(ActionW.FLIP.cmd());
            if (flipValue instanceof Boolean) {
                flip = (Boolean) flipValue;
            }
            return DisplayUtils.getOrientationFromImgOrPat(ipo, (int) rotation, flip);
        }
        return null;
    }

    public void resetAction(String cmd) {
        // Pan
        if (cmd == null || ActionW.PAN.cmd().equals(cmd)) {
            resetPan();
        }
        // Win/Level and Preset
        if (cmd == null || ActionW.WINLEVEL.cmd().equals(cmd) || ActionW.PRESET.cmd().equals(cmd)) {
            TextureDicomSeries s = getSeriesObject();
            if (s != null) {
                List<PresetWindowLevel> list = s.getPresetList(true, false);
                if (list.isEmpty()) {
                    setActionsInView(ActionW.PRESET.cmd(), null, false);
                } else {
                    setPresetWindowLevel(list.get(0));
                }
            }
        }
        // LUT
        if (cmd == null || ActionW.LUT.cmd().equals(cmd)) {
            setActionsInView(ActionW.LUT.cmd(), GUIManager.colorMaskList.get(0));
            colorMaskEnabled = false;
        }
        // Filter
        if (cmd == null || ActionW.FILTER.cmd().equals(cmd)) {
            setActionsInView(ActionW.FILTER.cmd(), GUIManager.kernelList.get(0));
            getSeriesObject().getTextureData().destroyReplacementTexture();
        }
        // InverseLUT
        if (cmd == null || ActionW.INVERT_LUT.cmd().equals(cmd)) {
            setActionsInView(ActionW.INVERT_LUT.cmd(), false);
            inverse = false;
        }
        if (cmd == null || ActionW.ZOOM.cmd().equals(cmd)) {
            setActionsInView(ActionW.ZOOM.cmd(), -100.0D);
            resetZoom();
        }
        // Smoothing
        if (cmd == null || ActionWA.SMOOTHING.cmd().equals(cmd)) {
            setActionsInView(ActionWA.SMOOTHING.cmd(), true);
        }
        if (cmd == null || ActionW.ROTATION.cmd().equals(cmd)) {
            setActionsInView(ActionW.ROTATION.cmd(), 0);
            setRotationOffset(Math.toRadians(0));
            updateAffineTransform();

        }
        if (cmd == null || ActionW.FLIP.cmd().equals(cmd)) {
            setActionsInView(ActionW.FLIP.cmd(), false);
            flippedHorizontally = false;
            updateAffineTransform();
        }

        // Mip Option
        if (cmd == null || ActionWA.MIP_OPTION.cmd().equals(cmd)) {
            setActionsInView(ActionWA.MIP_OPTION.cmd(), TextureImageCanvas.MipOption.None, true);
            mipOption = TextureImageCanvas.MipOption.None;
            renderSupp.setDirty(true);
            graphsLayer.updateAllLabels(ViewTexture.this);
        }
        // Mip Depth
        if (cmd == null || ActionWA.MIP_DEPTH.cmd().equals(cmd)) {
            setActionsInView(ActionWA.MIP_DEPTH.cmd(), 5, true);
            mipDepth = 5 / (double) getTotalSlices();
            renderSupp.setDirty(true);
            graphsLayer.updateAllLabels(this);
        }

        // ControlAxes
        if ((cmd == null || "resetToAquisition".equals(cmd)) && controlAxes != null){
            controlAxes.reset();
            // must repaint all views
            getParent().repaint();
        }

        if ("resetToAxial".equals(cmd) && controlAxes != null) {
            controlAxes.resetWithOrientation();
            // must repaint all views
            getParent().repaint();

            TextureImageCanvas[] canvases = controlAxes.getCanvases();
            for (TextureImageCanvas canv : canvases) {
                if (canv instanceof ViewTexture) {
                    ((ViewTexture) canv).handleGraphicsLayer(-1);
                }
            }
        }
    }

    private boolean isContentPhotometricInterpretationInverse() {
        TextureDicomSeries seriesObject = getSeriesObject();
        if (seriesObject != null) {
            // Dont know any serie with mixed results here, so keep simple.
            return seriesObject.isPhotometricInterpretationInverse(0);
        }
        return false;
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
     * Content pixel size. Will return 0 if its unknown ot not trustable.
     * 
     * @return Content's pixel size.
     */
    public double getShowingPixelSize() {
        // Se tem o pixelSpacing é consistente na textura
        double acqSpacing = 0;
        if (getParentImageSeries() instanceof TextureDicomSeries) {
            TextureDicomSeries ser = (TextureDicomSeries) getParentImageSeries();
            double[] aps = ser.getAcquisitionPixelSpacing();
            Vector3d dimMultiplier = ser.getDimensionMultiplier();

            if (aps != null && aps.length == 2 && aps[0] == aps[1] && dimMultiplier.x == dimMultiplier.y) {
                acqSpacing = aps[0];// dimMultiplier.x;

                // Acquisition plane?
                if (isShowingAcquisitionAxis() || ser.isSliceSpacingRegular()) {
                    return acqSpacing;
                }
            }

            if (aps == null && (isShowingAcquisitionAxis() || ser.isSliceSpacingRegular())) {
                return 1.0;
            }
        }
        return 0;
    }

    protected MouseActionAdapter getAction(ActionW action) {
        ActionState a = eventManager.getAction(action);
        if (a instanceof MouseActionAdapter) {
            return (MouseActionAdapter) a;
        }
        return null;
    }

    public MouseActionAdapter getMouseAdapter(String action) {
        if (action.equals(ActionW.MEASURE.cmd())) {
            return graphicMouseHandler;
        } else if (action.equals(ActionW.PAN.cmd())) {
            return getAction(ActionW.PAN);
        } else if (action.equals(ActionW.CONTEXTMENU.cmd())) {
            return contextMenuHandler;
        } else if (action.equals(ActionW.WINDOW.cmd())) {
            return getAction(ActionW.WINDOW);
        } else if (action.equals(ActionW.LEVEL.cmd())) {
            return getAction(ActionW.LEVEL);
        } else if (action.equals(ActionW.WINLEVEL.cmd())) {
            return getAction(ActionW.LEVEL);
        } else if (action.equals(ActionW.SCROLL_SERIES.cmd())) {
            return getAction(ActionW.SCROLL_SERIES);
        } else if (action.equals(ActionW.ZOOM.cmd())) {
            return getAction(ActionW.ZOOM);
        } else if (action.equals(ActionW.CROSSHAIR.cmd())) {
            // return getAction(ActionW.CROSSHAIR);
            return crosshairAction;
        } else if (action.equals(ActionW.ROTATION.cmd())) {
            return getAction(ActionW.ROTATION);
        }

        return null;
    }

    public TextureDicomSeries getSeriesObject() {
        return (TextureDicomSeries) getParentImageSeries();
    }

    @Override
    public void moveImageOffset(int width, int height) {
        if (hasContent()) {
            super.moveImageOffset(width, height);
        }
        renderSupp.setDirty(true);
    }

    private void rotateImage(int angle) {
        Object actionValue = getActionValue(ActionW.ROTATION.cmd());
        if (actionValue instanceof Integer) {
            Integer actual = (Integer) actionValue;
            if (angle > 0) {
                setActionsInView(ActionW.ROTATION.cmd(), (actual + angle) % 360);
            } else {
                setActionsInView(ActionW.ROTATION.cmd(), (actual + angle + 360) % 360);
            }
        }
    }

    @Override
    public void registerDefaultListeners() {
        addFocusListener(this);
        ToolTipManager.sharedInstance().registerComponent(this);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Double currentZoom = (Double) actionsInView.get(ActionW.ZOOM.cmd());
                /*
                 * Negative value means a default value according to the zoom type (pixel size, best fit...). Set again
                 * to default value to compute again the position. For instance, the image cannot be center aligned
                 * until the view has been repaint once (because the size is null).
                 */
                if (currentZoom <= 0.0) {
                    zoom(0.0);
                }
                repaint();
            }
        });
    }

    /*
     * ******************************************************************** MOUSE LISTENERS
     * ******************************************************************
     */

    @Override
    public void enableMouseAndKeyListener(MouseActions actions) {
        disableMouseAndKeyListener();
        iniDefaultMouseListener();
        iniDefaultKeyListener();
        // Set the butonMask to 0 of all the actions
        resetMouseAdapter();

        this.setCursor(AbstractLayerModel.DEFAULT_CURSOR);

        addMouseAdapter(actions.getLeft(), InputEvent.BUTTON1_DOWN_MASK); // left mouse button
        if (actions.getMiddle().equals(actions.getLeft())) {
            // If mouse action is already registered, only add the modifier mask
            MouseActionAdapter adapter = getMouseAdapter(actions.getMiddle());
            if (adapter != null) {
                adapter.setButtonMaskEx(adapter.getButtonMaskEx() | InputEvent.BUTTON2_DOWN_MASK);
            }
        } else {
            addMouseAdapter(actions.getMiddle(), InputEvent.BUTTON2_DOWN_MASK);// middle mouse button
        }
        if (actions.getRight().equals(actions.getLeft()) || actions.getRight().equals(actions.getMiddle())) {
            // If mouse action is already registered, only add the modifier mask
            MouseActionAdapter adapter = getMouseAdapter(actions.getRight());
            if (adapter != null) {
                adapter.setButtonMaskEx(adapter.getButtonMaskEx() | InputEvent.BUTTON3_DOWN_MASK);
            }
        } else {
            addMouseAdapter(actions.getRight(), InputEvent.BUTTON3_DOWN_MASK); // right mouse button
        }
        this.addMouseWheelListener(getMouseAdapter(actions.getWheel()));
    }

    protected void resetMouseAdapter() {
        for (ActionState adapter : eventManager.getAllActionValues()) {
            if (adapter instanceof MouseActionAdapter) {
                ((MouseActionAdapter) adapter).setButtonMaskEx(0);
            }
        }
        // reset context menu that is a field of this instance
        contextMenuHandler.setButtonMaskEx(0);
        graphicMouseHandler.setButtonMaskEx(0);
    }

    /**
     * Need to remove all to clear.
     */

    @Override
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
    }

    @Override
    public synchronized void iniDefaultMouseListener() {
        // focus listener is always on
        this.addMouseListener(focusHandler);
        this.addMouseMotionListener(focusHandler);
    }

    @Override
    public synchronized void iniDefaultKeyListener() {
        this.addKeyListener(this);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            if (e.isControlDown()) {
                // Ctrl + Space passa para a proxima action.
                ImageViewerPlugin<DicomImageElement> view = eventManager.getSelectedView2dContainer();
                if (view != null) {
                    ViewerToolBar toolBar = view.getViewerToolBar();
                    if (toolBar != null) {
                        String command = ViewerToolBar
                            .getNextCommand(ViewerToolBar.actionsButtons, toolBar.getMouseLeft().getActionCommand())
                            .cmd();
                        changeLeftMouseAction(command);
                    }
                }
            } else {
                // Liga/desliga informacoes do paciente.
                boolean visible = getInfoLayer().isVisible();
                getInfoLayer().setVisible(!visible);
                repaint();
            }
        } else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_H) {
            // Flip horizontal
            Object actionValue = getActionValue(ActionW.FLIP.cmd());
            boolean val = JMVUtils.getNULLtoFalse(actionValue);
            setActionsInView(ActionW.FLIP.cmd(), !val);
            repaint();
        } else if (e.isAltDown() && e.getKeyCode() == KeyEvent.VK_L) {
            // Rotaciona para a esquerda
            rotateImage(90);
        } else if (e.isAltDown() && e.getKeyCode() == KeyEvent.VK_R) {
            // Rotaciona para a esquerda
            rotateImage(-90);
        } else {
            ActionW action = GUIManager.getInstance().getActionFromkeyEvent(e.getKeyCode(), e.getModifiers());
            if (action != null) {
                changeLeftMouseAction(action.cmd());
            }
        }
    }

    @Override
    public void keyTyped(KeyEvent e) { /* Empty */
    }

    @Override
    public void keyReleased(KeyEvent e) { /* TODO: ctrl + c */
    }

    private void changeLeftMouseAction(String command) {
        ImageViewerPlugin view = eventManager.getSelectedView2dContainer();
        if (view != null) {
            ViewerToolBar toolBar = view.getViewerToolBar();
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

    private void addMouseAdapter(String actionName, int buttonMask) {
        MouseActionAdapter adapter = getMouseAdapter(actionName);
        if (adapter == null) {
            return;
        }
        adapter.setButtonMaskEx(adapter.getButtonMaskEx() | buttonMask);
        if (adapter == graphicMouseHandler) {
            // TODO add key listener
            // this.addKeyListener(drawingsKeyListeners);
        } else if (adapter instanceof PannerListener) {
            ((PannerListener) adapter).reset();
            this.addKeyListener((PannerListener) adapter);
        }

        if (actionName.equals(ActionW.WINLEVEL.cmd())) {
            // For window/level action set window action on x axis
            MouseActionAdapter win = getAction(ActionW.WINDOW);
            if (win != null) {
                win.setButtonMaskEx(win.getButtonMaskEx() | buttonMask);
                win.setMoveOnX(true);
                this.addMouseListener(win);
                this.addMouseMotionListener(win);
            }
            // set level action with inverse progression (move the cursor down will decrease the values)
            adapter.setInverse(true);
        } else if (actionName.equals(ActionW.WINDOW.cmd())) {
            adapter.setMoveOnX(false);
        } else if (actionName.equals(ActionW.LEVEL.cmd())) {
            adapter.setInverse(true);
        }
        this.addMouseListener(adapter);
        this.addMouseMotionListener(adapter);
    }

    public RenderSupport getRenderSupport() {
        return renderSupp;
    }

    public GeometryOfSlice getSliceGeometry() {
        if (hasContent() && isShowingAcquisitionAxis()) {
            return getSeriesObject().getSliceGeometry(getCurrentSlice());
        }
        return null;
    }

    @Override
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
        public void enableAction(boolean enabled) { /* Empty */
        }

        @Override
        public ActionW getActionW() {
            return ActionW.CROSSHAIR;
        }

        @Override
        public boolean registerActionState(Object c) {
            return false;
        }

        @Override
        public void unregisterActionState(Object c) { /* Empty */
        }

        @Override
        public boolean isActionEnabled() {
            return (controlAxes != null || controlAxesToWatch != null);
        }

    }

    @Override
    public ImageViewerEventManager getEventManager() {
        return eventManager;
    }

    @Override
    public void copyActionWState(HashMap actionsInView) {
        actionsInView.putAll(this.actionsInView);
    }

    @Override
    public void updateSynchState() {
        // TODO Auto-generated method stub
    }

    @Override
    public PixelInfo getPixelInfo(Point p) {
        // TODO:
        // p is a point on the measurements coordinates system.
        // It's missing the conversion from the measurement's to the
        // original coordinates system, to be able to do this.

        // Its necessary just for the PixelInfoGraphic.
        return null;
    }

    @Override
    public Panner getPanner() {
        // Not relevant
        return null;
    }

    @Override
    public void setSeries(MediaSeries series) {
        // Used on ImageViewerPlugin.addSeries
        // If it had to be implemented, would have to return RuntimeException in case
        // series is too big to video card...
    }

    @Override
    public void setFocused(boolean focused) {
        MediaSeries series = getSeries();
        if (series != null) {
            series.setFocused(focused);
        }
        if (focused && getBorder() == lostFocusBorder) {
            setBorder(focusBorder);
        } else if (!focused && getBorder() == focusBorder) {
            setBorder(lostFocusBorder);
        }
    }

    @Override
    public double getBestFitViewScale() {
        return adjustViewScale(super.getBestFitViewScale());
    }

    @Override
    public double getRealWorldViewScale() {
        double viewScale = 0.0;
        TextureDicomSeries s = getSeriesObject();
        if (s != null) {
            Window win = SwingUtilities.getWindowAncestor(this);
            if (win != null) {
                GraphicsConfiguration config = win.getGraphicsConfiguration();
                Monitor monitor = MeasureTool.viewSetting.getMonitor(config.getDevice());
                if (monitor != null) {
                    double realFactor = monitor.getRealScaleFactor();
                    if (realFactor > 0.0) {
                        Unit imgUnit = s.getPixelSpacingUnit();
                        double pixSize = getShowingPixelSize();
                        if (!Unit.PIXEL.equals(imgUnit) && pixSize != 0.0) {
                            viewScale = imgUnit.getConvFactor() * pixSize / realFactor;
                            viewScale = -adjustViewScale(viewScale);
                        }
                    }
                }
            }
        }
        return viewScale;
    }

    protected double adjustViewScale(double viewScale) {
        ActionState zoom = eventManager.getAction(ActionW.ZOOM);
        if (zoom instanceof SliderChangeListener) {
            SliderChangeListener z = (SliderChangeListener) zoom;
            // Adjust the best fit value according to the possible range of the model zoom action.
            int sliderValue = ImageViewerEventManager.viewScaleToSliderValue(viewScale);
            if (eventManager.getSelectedViewPane() == this) {
                // Set back the value to UI components as this value cannot be computed early.
                z.setValueWithoutTriggerAction(sliderValue);
                viewScale = ImageViewerEventManager.sliderValueToViewScale(z.getValue());
            } else {
                DefaultBoundedRangeModel model = z.getModel();
                if (sliderValue < model.getMinimum()) {
                    sliderValue = model.getMinimum();
                } else if (sliderValue > model.getMaximum()) {
                    sliderValue = model.getMaximum();
                }
                viewScale = ImageViewerEventManager.sliderValueToViewScale(sliderValue);
            }
        }
        return viewScale;
    }

    @Override
    public int getTileOffset() {
        // Not relevant
        return 0;
    }

    @Override
    public void setTileOffset(int tileOffset) {
        // Not relevant
    }

    @Override
    public void center() {
        setCenter(0, 0);
    }

    @Override
    public void setCenter(double x, double y) {
        setImageOffset(new Vector2d(x, y));
    }

    @Override
    public void setOrigin(double x, double y) {

    }

    @Override
    public void moveOrigin(double x, double y) {
        // TODO Auto-generated method stub

    }

    @Override
    public void moveOrigin(PanPoint point) {
        if (point != null) {
            if (PanPoint.STATE.Dragging.equals(point.getState())) {
                moveImageOffset((int) point.getX(), (int) point.getY());
            }
        }
    }

    @Override
    public Comparator getCurrentSortComparator() {
        if (getParentImageSeries() != null) {
            ((TextureDicomSeries) getParentImageSeries()).getSeriesSorter();
        }
        return null;
    }

    @Override
    public void setActionsInView(String action, Object value) {
        setActionsInView(action, value, false);
    }

    @Override
    public void setSelected(boolean selected) {
        setBorder(selected ? focusBorder : normalBorder);
        // Remove the selection of graphics
        getLayerModel().setSelectedGraphics(null);

        // Throws to the tool listener the current graphic selection.
        getLayerModel().fireGraphicsSelectionChanged(getMeasurableLayer());
    }

    @Override
    public void setDrawingsVisibility(boolean visible) {
        if ((Boolean) actionsInView.get(ActionW.DRAW.cmd()) != visible) {
            actionsInView.put(ActionW.DRAW.cmd(), visible);
            repaint();
        }
    }

    @Override
    public Object getLensActionValue(String action) {
        // Lens not implemented
        return null;
    }

    @Override
    public void changeZoomInterpolation(int interpolation) {
        // TODO Auto-generated method stub

    }

    @Override
    public OpManager getDisplayOpManager() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getPointerType() {
        return pointerType;
    }

    @Override
    public void setPointerType(int pointerType) {
        this.pointerType = pointerType;
    }

    @Override
    public void addPointerType(int i) {
        this.pointerType |= i;
    }

    @Override
    public void resetPointerType(int i) {
        this.pointerType &= ~i;
    }

    @Override
    public Point2D getHighlightedPosition() {
        return highlightedPosition;
    }

    @Override
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

    @Override
    public List getExportToClipboardAction() {
        // TODO Auto-generated method stub
        return null;
    }

    private void resetActions(String[] cmd) {
        if (cmd == null) {
            resetAction(null);
            return;
        }
        for (String string : cmd) {
            resetAction(string);
        }
    }

    @Override
    public void resetZoom() {
        ZoomType type = (ZoomType) actionsInView.get(zoomTypeCmd);
        if (!ZoomType.CURRENT.equals(type)) {
            zoom(0.0);
        }
    }

    @Override
    public void resetPan() {
        center();
    }

    @Override
    public void reset() {
        ImageViewerPlugin pane = eventManager.getSelectedView2dContainer();
        if (pane != null) {
            pane.resetMaximizedSelectedImagePane(this);
        }

        initActionWState();

        String[] resets = new String[] { ActionW.WINLEVEL.cmd(), ActionW.PRESET.cmd(), ActionW.LUT.cmd(),
            // ActionW.LUT_SHAPE.cmd(),
            ActionW.FILTER.cmd(), ActionW.INVERT_LUT.cmd(), "mip-opt", "mip-dep", ActionW.ZOOM.cmd(),
            ActionW.ROTATION.cmd(), ActionW.PAN.cmd(), ActionW.FLIP.cmd(), "interpolate", "resetToAxial" };

        resetActions(resets);
        eventManager.updateComponentsListener(this);
    }

    @Override
    public List getViewButtons() {
        // Not relevant
        return null;
    }

    @Override
    public void closeLens() {
        // Lens not implemented
    }

    @Override
    public void zoom(double viewScale) {
        boolean defSize = viewScale == 0.0;
        ZoomType type = (ZoomType) actionsInView.get(zoomTypeCmd);
        if (defSize) {
            if (ZoomType.BEST_FIT.equals(type)) {
                viewScale = -getBestFitViewScale();
            } else if (ZoomType.REAL.equals(type)) {
                viewScale = -getRealWorldViewScale();
            }

            if (viewScale == 0.0) {
                viewScale = -adjustViewScale(1.0);
            }
        }

        actionsInView.put(ActionW.ZOOM.cmd(), viewScale);
        super.zoom(Math.abs(viewScale));
        // FIXME is that possible that the value is modified in setZoom(Math.abs(viewScale), true). Should keep the sign
        // actionsInView.put(ActionW.ZOOM.cmd(), getActualDisplayZoom());
        if (defSize) {
            /*
             * If the view has not been repainted once (the width and the height of the view is 0), it will be done
             * later and the componentResized event will call again the zoom.
             */
            center();
        }
        updateAffineTransform();
    }

    @Override
    public void focusGained(FocusEvent e) {
        if (!e.isTemporary()) {
            ImageViewerPlugin<DicomImageElement> pane = eventManager.getSelectedView2dContainer();
            if (pane != null && pane.isContainingView(this)) {
                pane.setSelectedImagePaneFromFocus(this);
            }
        }
    }

    @Override
    public void focusLost(FocusEvent e) {
    }

    @Override
    public void handleLayerChanged(ImageLayer layer) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setSeries(MediaSeries<DicomImageElement> newSeries, DicomImageElement selectedMedia) {
        // TODO Auto-generated method stub

    }

    @Override
    public ImageLayer<DicomImageElement> getImageLayer() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public MeasurableLayer getMeasurableLayer() {
        return measurableLayer;
    }

    @Override
    public int getFrameIndex() {
        return getCurrentSlice();
    }

    @Override
    public DicomImageElement getImage() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public RenderedImage getSourceImage() {
        // TODO Auto-generated method stub
        return null;
    }

    protected JPopupMenu buildGraphicContextMenu(final MouseEvent evt, final ArrayList<Graphic> selected) {
        if (selected != null) {
            final JPopupMenu popupMenu = new JPopupMenu();
            TitleMenuItem itemTitle = new TitleMenuItem(Messages.getString("View2d.selection"), popupMenu.getInsets()); //$NON-NLS-1$
            popupMenu.add(itemTitle);
            popupMenu.addSeparator();
            boolean graphicComplete = true;
            if (selected.size() == 1) {
                final Graphic graph = selected.get(0);
                if (graph instanceof AbstractDragGraphic) {
                    final AbstractDragGraphic absgraph = (AbstractDragGraphic) graph;
                    if (!absgraph.isGraphicComplete()) {
                        graphicComplete = false;
                    }
                    if (absgraph.isVariablePointsNumber()) {
                        if (graphicComplete) {
                            /*
                             * Convert mouse event point to real image coordinate point (without geometric
                             * transformation)
                             */
                            final MouseEventDouble mouseEvt = new MouseEventDouble(ViewTexture.this,
                                MouseEvent.MOUSE_RELEASED, evt.getWhen(), 16, 0, 0, 0, 0, 1, true, 1);
                            mouseEvt.setSource(ViewTexture.this);
                            mouseEvt.setImageCoordinates(getImageCoordinatesFromMouse(evt.getX(), evt.getY()));
                            final int ptIndex = absgraph.getHandlePointIndex(mouseEvt);
                            if (ptIndex >= 0) {
                                JMenuItem menuItem = new JMenuItem(Messages.getString("View2d.rmv_pt")); //$NON-NLS-1$
                                menuItem.addActionListener(new ActionListener() {

                                    @Override
                                    public void actionPerformed(ActionEvent e) {
                                        absgraph.removeHandlePoint(ptIndex, mouseEvt);
                                    }
                                });
                                popupMenu.add(menuItem);

                                menuItem = new JMenuItem(Messages.getString("View2d.draw_pt")); //$NON-NLS-1$
                                menuItem.addActionListener(new ActionListener() {

                                    @Override
                                    public void actionPerformed(ActionEvent e) {
                                        absgraph.forceToAddPoints(ptIndex);
                                        MouseEventDouble evt2 = new MouseEventDouble(ViewTexture.this,
                                            MouseEvent.MOUSE_PRESSED, evt.getWhen(), 16, evt.getX(), evt.getY(),
                                            evt.getXOnScreen(), evt.getYOnScreen(), 1, true, 1);
                                        graphicMouseHandler.mousePressed(evt2);
                                    }
                                });
                                popupMenu.add(menuItem);
                                popupMenu.add(new JSeparator());
                            }
                        } else if (graphicMouseHandler.getDragSequence() != null
                            && absgraph.getHandlePointTotalNumber() == BasicGraphic.UNDEFINED) {
                            final JMenuItem item2 = new JMenuItem(Messages.getString("View2d.stop_draw")); //$NON-NLS-1$
                            item2.addActionListener(new ActionListener() {

                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    MouseEventDouble event =
                                        new MouseEventDouble(ViewTexture.this, 0, 0, 16, 0, 0, 0, 0, 2, true, 1);
                                    graphicMouseHandler.getDragSequence().completeDrag(event);
                                    graphicMouseHandler.mouseReleased(event);
                                }
                            });
                            popupMenu.add(item2);
                            popupMenu.add(new JSeparator());
                        }
                    }
                }
            }
            if (graphicComplete) {
                JMenuItem menuItem = new JMenuItem(Messages.getString("View2d.delete_sel")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        ViewTexture.this.getLayerModel().deleteSelectedGraphics(true);
                    }
                });
                popupMenu.add(menuItem);

                menuItem = new JMenuItem(Messages.getString("View2d.cut")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        AbstractLayerModel.GraphicClipboard.setGraphics(selected);
                        ViewTexture.this.getLayerModel().deleteSelectedGraphics(false);
                    }
                });
                popupMenu.add(menuItem);
                menuItem = new JMenuItem(Messages.getString("View2d.copy")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        AbstractLayerModel.GraphicClipboard.setGraphics(selected);
                    }
                });
                popupMenu.add(menuItem);
                popupMenu.add(new JSeparator());
            }
            // TODO separate AbstractDragGraphic and ClassGraphic for properties
            final ArrayList<AbstractDragGraphic> list = new ArrayList<AbstractDragGraphic>();
            for (Graphic graphic : selected) {
                if (graphic instanceof AbstractDragGraphic) {
                    list.add((AbstractDragGraphic) graphic);
                }
            }

            if (selected.size() == 1) {
                final Graphic graph = selected.get(0);
                JMenuItem item = new JMenuItem(Messages.getString("View2d.to_front")); //$NON-NLS-1$
                item.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        graph.toFront();
                    }
                });
                popupMenu.add(item);
                item = new JMenuItem(Messages.getString("View2d.to_back")); //$NON-NLS-1$
                item.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        graph.toBack();
                    }
                });
                popupMenu.add(item);
                popupMenu.add(new JSeparator());

                if (graphicComplete && graph instanceof LineGraphic) {

                    final JMenuItem calibMenu = new JMenuItem(Messages.getString("View2d.chg_calib")); //$NON-NLS-1$
                    calibMenu.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent e) {
                            String title = Messages.getString("View2d.clibration"); //$NON-NLS-1$
                            CalibrationView calibrationDialog =
                                new CalibrationView((LineGraphic) graph, ViewTexture.this);
                            ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(ViewTexture.this);
                            int res = JOptionPane.showConfirmDialog(ColorLayerUI.getContentPane(layer),
                                calibrationDialog, title, JOptionPane.OK_CANCEL_OPTION);
                            if (layer != null) {
                                layer.hideUI();
                            }
                            if (res == JOptionPane.OK_OPTION) {
                                calibrationDialog.applyNewCalibration();
                            }
                        }
                    });
                    popupMenu.add(calibMenu);
                    popupMenu.add(new JSeparator());
                }
            }
            if (list.size() > 0) {
                JMenuItem properties = new JMenuItem(Messages.getString("View2d.draw_prop")); //$NON-NLS-1$
                properties.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(ViewTexture.this);
                        JDialog dialog = new MeasureDialog(ViewTexture.this, list);
                        ColorLayerUI.showCenterScreen(dialog, layer);
                    }
                });
                popupMenu.add(properties);
            }
            return popupMenu;
        }
        return null;
    }

    protected JPopupMenu buildContexMenu(final MouseEvent evt) {
        JPopupMenu popupMenu = new JPopupMenu();
        TitleMenuItem itemTitle = new TitleMenuItem("Left mouse actions" + StringUtil.COLON, popupMenu.getInsets()); //$NON-NLS-1$
        popupMenu.add(itemTitle);
        popupMenu.setLabel(MouseActions.LEFT);
        String action = eventManager.getMouseActions().getLeft();
        ButtonGroup groupButtons = new ButtonGroup();
        int count = popupMenu.getComponentCount();
        ImageViewerPlugin<DicomImageElement> view = eventManager.getSelectedView2dContainer();
        if (view != null) {
            final ViewerToolBar toolBar = view.getViewerToolBar();
            if (toolBar != null) {
                ActionListener leftButtonAction = new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (e.getSource() instanceof JRadioButtonMenuItem) {
                            JRadioButtonMenuItem item = (JRadioButtonMenuItem) e.getSource();
                            toolBar.changeButtonState(MouseActions.LEFT, item.getActionCommand());
                        }
                    }
                };

                List<ActionW> actionsButtons = ViewerToolBar.actionsButtons;
                synchronized (actionsButtons) {
                    for (int i = 0; i < actionsButtons.size(); i++) {
                        ActionW b = actionsButtons.get(i);
                        JRadioButtonMenuItem radio =
                            new JRadioButtonMenuItem(b.getTitle(), b.getIcon(), b.cmd().equals(action));
                        radio.setActionCommand(b.cmd());
                        radio.setAccelerator(KeyStroke.getKeyStroke(b.getKeyCode(), b.getModifier()));
                        // Trigger the selected mouse action
                        radio.addActionListener(toolBar);
                        // Update the state of the button in the toolbar
                        radio.addActionListener(leftButtonAction);
                        popupMenu.add(radio);
                        groupButtons.add(radio);
                    }
                }
            }
        }

        if (count < popupMenu.getComponentCount()) {
            popupMenu.add(new JSeparator());
            count = popupMenu.getComponentCount();
        }

        if (AbstractLayerModel.GraphicClipboard.getGraphics() != null) {
            JMenuItem menuItem = new JMenuItem(Messages.getString("View2d.paste_draw")); //$NON-NLS-1$
            menuItem.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    List<Graphic> graphs = AbstractLayerModel.GraphicClipboard.getGraphics();
                    if (graphs != null) {
                        Rectangle2D area = ViewTexture.this.getViewModel().getModelArea();
                        for (Graphic g : graphs) {
                            if (!g.getBounds(null).intersects(area)) {
                                int option = JOptionPane.showConfirmDialog(ViewTexture.this,
                                    "At least one graphic is outside the image.\n Do you want to continue?"); //$NON-NLS-1$
                                if (option == JOptionPane.YES_OPTION) {
                                    break;
                                } else {
                                    return;
                                }
                            }
                        }
                        for (Graphic g : graphs) {
                            AbstractLayer layer = ViewTexture.this.getLayerModel().getLayer(g.getLayerID());
                            if (layer != null) {
                                Graphic graph = g.deepCopy();
                                if (graph != null) {
                                    graph.updateLabel(true, ViewTexture.this);
                                    layer.addGraphic(graph);
                                }
                            }
                        }
                        // Repaint all because labels are not drawn
                        ViewTexture.this.getLayerModel().repaint();
                    }
                }
            });
            popupMenu.add(menuItem);
        }

        if (count < popupMenu.getComponentCount()) {
            popupMenu.add(new JSeparator());
            count = popupMenu.getComponentCount();
        }

        if (eventManager instanceof GUIManager) {
            GUIManager manager = (GUIManager) eventManager;
            JMVUtils.addItemToMenu(popupMenu, manager.getPresetMenu("weasis.contextmenu.presets"));
            // JMVUtils.addItemToMenu(popupMenu, manager.getLutShapeMenu("weasis.contextmenu.lutShape"));
            JMVUtils.addItemToMenu(popupMenu, manager.getLutMenu("weasis.contextmenu.lut"));
            JMVUtils.addItemToMenu(popupMenu, manager.getLutInverseMenu("weasis.contextmenu.invertLut"));
            JMVUtils.addItemToMenu(popupMenu, manager.getFilterMenu("weasis.contextmenu.filter"));

            if (count < popupMenu.getComponentCount()) {
                popupMenu.add(new JSeparator());
                count = popupMenu.getComponentCount();
            }

            JMVUtils.addItemToMenu(popupMenu, manager.getZoomMenu("weasis.contextmenu.zoom"));
            JMVUtils.addItemToMenu(popupMenu, manager.getOrientationMenu("weasis.contextmenu.orientation"));
            JMVUtils.addItemToMenu(popupMenu, manager.getSortStackMenu("weasis.contextmenu.sortstack"));

            if (count < popupMenu.getComponentCount()) {
                popupMenu.add(new JSeparator());
                count = popupMenu.getComponentCount();
            }

            JMVUtils.addItemToMenu(popupMenu, manager.getResetMenu("weasis.contextmenu.reset"));
        }

        return popupMenu;
    }

    class ContextMenuHandler extends MouseActionAdapter {

        @Override
        public void mousePressed(final MouseEvent evt) {
            showPopup(evt);
        }

        @Override
        public void mouseReleased(final MouseEvent evt) {
            showPopup(evt);
        }

        private void showPopup(final MouseEvent evt) {
            // Context menu
            if ((evt.getModifiersEx() & getButtonMaskEx()) != 0) {
                JPopupMenu popupMenu = null;
                final ArrayList<Graphic> selected =
                    new ArrayList<Graphic>(ViewTexture.this.getLayerModel().getSelectedGraphics());
                if (selected.size() > 0) {
                    popupMenu = ViewTexture.this.buildGraphicContextMenu(evt, selected);
                } else if (ViewTexture.this.getSeries() != null) {
                    popupMenu = ViewTexture.this.buildContexMenu(evt);
                }
                if (popupMenu != null) {
                    popupMenu.show(evt.getComponent(), evt.getX(), evt.getY());
                }
            }
        }
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    class FocusHandler extends MouseActionAdapter {

        @Override
        public void mousePressed(MouseEvent evt) {
            ImageViewerPlugin<DicomImageElement> pane = eventManager.getSelectedView2dContainer();
            if (pane == null) {
                return;
            }

            if (evt.getClickCount() == 2) {
                pane.maximizedSelectedImagePane(ViewTexture.this, evt);
                return;
            }

            if (pane.isContainingView(ViewTexture.this) && pane.getSelectedImagePane() != ViewTexture.this) {
                // register all actions of the EventManager with this view waiting the focus gained in some cases is not
                // enough, because others mouseListeners are triggered before the focus event (that means before
                // registering the view in the EventManager)
                pane.setSelectedImagePane(ViewTexture.this);
            }
            // request the focus even it is the same pane selected
            requestFocusInWindow();

            int modifiers = evt.getModifiersEx();
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

            ViewTexture.this.setCursor(action == null ? AbstractLayerModel.DEFAULT_CURSOR : action.getCursor());
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            showPixelInfos(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            ViewTexture.this.setCursor(AbstractLayerModel.DEFAULT_CURSOR);
        }
    }

}
