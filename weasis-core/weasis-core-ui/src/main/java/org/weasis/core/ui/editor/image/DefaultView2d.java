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
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
import java.awt.image.SampleModel;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.media.jai.PlanarImage;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JPopupMenu;
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
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.FilterOp;
import org.weasis.core.api.image.FlipOp;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.PseudoColorOp;
import org.weasis.core.api.image.RotationOp;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.image.util.KernelData;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.image.SynchData.Mode;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.graphic.AbstractDragGraphic;
import org.weasis.core.ui.graphic.DragSequence;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.ImageLayerChangeListener;
import org.weasis.core.ui.graphic.PanPoint;
import org.weasis.core.ui.graphic.PanPoint.STATE;
import org.weasis.core.ui.graphic.RenderedImageLayer;
import org.weasis.core.ui.graphic.SelectGraphic;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.graphic.model.DefaultViewModel;
import org.weasis.core.ui.graphic.model.GraphicList;
import org.weasis.core.ui.graphic.model.GraphicsPane;
import org.weasis.core.ui.util.MouseEventDouble;
import org.weasis.core.ui.util.TitleMenuItem;

/**
 * @author Nicolas Roduit, Benoit Jacquemoud
 */
public abstract class DefaultView2d<E extends ImageElement> extends GraphicsPane implements PropertyChangeListener,
    FocusListener, Image2DViewer, ImageLayerChangeListener<E>, KeyListener {
    public enum ZoomType {
        CURRENT, BEST_FIT, PIXEL_SIZE, REAL
    };

    public static final String zoomTypeCmd = "zoom.type";
    public static final ImageIcon SYNCH_ICON = new ImageIcon(DefaultView2d.class.getResource("/icon/22x22/synch.png"));
    public static final int CENTER_POINTER = 1 << 1;
    public static final int HIGHLIGHTED_POINTER = 1 << 2;
    static final Shape[] pointer;
    static {
        pointer = new Shape[5];
        pointer[0] = new Ellipse2D.Double(-27.0, -27.0, 54.0, 54.0);
        pointer[1] = new Line2D.Double(-40.0, 0.0, -5.0, 0.0);
        pointer[2] = new Line2D.Double(5.0, 0.0, 40.0, 0.0);
        pointer[3] = new Line2D.Double(0.0, -40.0, 0.0, -5.0);
        pointer[4] = new Line2D.Double(0.0, 5.0, 0.0, 40.0);
    }
    protected static final Color focusColor = Color.orange;
    protected static final Color lostFocusColor = new Color(255, 224, 178);

    protected final FocusHandler focusHandler = new FocusHandler();
    protected final MouseHandler mouseClickHandler = new MouseHandler();

    private final PanPoint highlightedPosition = new PanPoint(STATE.Center);
    private final PanPoint startedDragPoint = new PanPoint(STATE.DragStart);
    private int pointerType = 0;

    protected final Color pointerColor1 = Color.black;
    protected final Color pointerColor2 = Color.white;
    protected final Border normalBorder = new EtchedBorder(BevelBorder.LOWERED, Color.gray, Color.white);
    protected final Border focusBorder = new EtchedBorder(BevelBorder.LOWERED, focusColor, focusColor);
    protected final Border lostFocusBorder = new EtchedBorder(BevelBorder.LOWERED, lostFocusColor, lostFocusColor);

    protected DragSequence ds;
    protected final RenderedImageLayer<E> imageLayer;
    protected Panner<?> panner;
    protected ZoomWin<E> lens;
    private final List<ViewButton> viewButtons;
    protected ViewButton synchButton;

    protected MediaSeries<E> series = null;
    protected AnnotationsLayer infoLayer;
    protected int tileOffset;

    protected final ImageViewerEventManager<E> eventManager;

    public DefaultView2d(ImageViewerEventManager<E> eventManager) {
        this(eventManager, null, null);
    }

    public DefaultView2d(ImageViewerEventManager<E> eventManager, AbstractLayerModel layerModel, ViewModel viewModel) {
        super(layerModel, viewModel);
        if (eventManager == null) {
            throw new IllegalArgumentException("EventManager cannot be null"); //$NON-NLS-1$
        }
        this.eventManager = eventManager;
        this.viewButtons = new ArrayList<ViewButton>();
        this.tileOffset = 0;

        imageLayer = new RenderedImageLayer<E>(true);
        initActionWState();
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

    protected void buildPanner() {
        if (panner == null) {
            panner = new Panner(this);
        }
    }

    public void copyActionWState(HashMap<String, Object> actionsInView) {
        actionsInView.putAll(this.actionsInView);
    }

    protected void initActionWState() {
        actionsInView.put(ActionW.SPATIAL_UNIT.cmd(), Unit.PIXEL);
        actionsInView.put(zoomTypeCmd, ZoomType.BEST_FIT);
        actionsInView.put(ActionW.ZOOM.cmd(), 0.0);
        actionsInView.put(ActionW.LENS.cmd(), false);
        actionsInView.put(ActionW.DRAW.cmd(), true);
        actionsInView.put(ActionW.INVERSESTACK.cmd(), false);
        actionsInView.put(ActionW.FILTERED_SERIES.cmd(), null);

        OpManager disOp = getDisplayOpManager();
        disOp.setParamValue(ZoomOp.OP_NAME, ZoomOp.P_INTERPOLATION, eventManager.getZoomSetting().getInterpolation());
        disOp.setParamValue(RotationOp.OP_NAME, RotationOp.P_ROTATE, 0);
        disOp.setParamValue(FlipOp.OP_NAME, FlipOp.P_FLIP, false);
        disOp.setParamValue(FilterOp.OP_NAME, FilterOp.P_KERNEL_DATA, KernelData.NONE);
        disOp.setParamValue(PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT, ByteLut.defaultLUT);
        disOp.setParamValue(PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT_INVERSE, false);
    }

    public ImageViewerEventManager<E> getEventManager() {
        return eventManager;
    }

    public void updateSynchState() {
        if (getActionValue(ActionW.SYNCH_LINK.cmd()) != null) {
            if (synchButton == null) {
                synchButton = new ViewButton(new ShowPopup() {

                    @Override
                    public void showPopup(Component invoker, int x, int y) {
                        final SynchData synch = (SynchData) getActionValue(ActionW.SYNCH_LINK.cmd());
                        if (synch == null) {
                            return;
                        }

                        JPopupMenu popupMenu = new JPopupMenu();
                        TitleMenuItem itemTitle = new TitleMenuItem(ActionW.SYNCH.getTitle(), popupMenu.getInsets());
                        popupMenu.add(itemTitle);
                        popupMenu.addSeparator();

                        for (Entry<String, Boolean> a : synch.getActions().entrySet()) {
                            JCheckBoxMenuItem menuItem = new JCheckBoxMenuItem(a.getKey(), a.getValue());
                            menuItem.addActionListener(new ActionListener() {

                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    if (e.getSource() instanceof JCheckBoxMenuItem) {
                                        JCheckBoxMenuItem item = (JCheckBoxMenuItem) e.getSource();
                                        synch.getActions().put(item.getText(), item.isSelected());
                                    }
                                }
                            });
                            popupMenu.add(menuItem);
                        }
                        popupMenu.show(invoker, x, y);

                    }
                }, SYNCH_ICON);
                synchButton.setVisible(true);
                synchButton.setPosition(GridBagConstraints.SOUTHEAST);
            }
            if (!getViewButtons().contains(synchButton)) {
                getViewButtons().add(synchButton);
            }
            SynchData synch = (SynchData) getActionValue(ActionW.SYNCH_LINK.cmd());
            synchButton.setVisible(!SynchData.Mode.None.equals(synch.getMode()));
        } else {
            getViewButtons().remove(synchButton);
        }
    }

    public String getPixelInfo(Point p, RenderedImageLayer<E> imageLayer) {
        ImageElement imageElement = imageLayer.getSourceImage();
        StringBuffer message = new StringBuffer(" "); //$NON-NLS-1$
        if (imageElement != null && imageLayer.getReadIterator() != null) {
            PlanarImage image = imageElement.getImage((OpManager) actionsInView.get(ActionW.PREPROCESSING.cmd()));
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

    public Panner getPanner() {
        return panner;
    }

    protected void closeLens() {
        if (lens != null) {
            lens.showLens(false);
            this.remove(lens);
            actionsInView.put(ActionW.LENS.cmd(), false);
        }
    }

    public void setSeries(MediaSeries<E> series) {
        setSeries(series, null);
    }

    public void setSeries(MediaSeries<E> newSeries, E selectedMedia) {
        MediaSeries<E> oldsequence = this.series;
        this.series = newSeries;

        if ((oldsequence != null && oldsequence.equals(newSeries)) || (oldsequence == null && newSeries == null)) {
            return;
        }

        closingSeries(oldsequence);
        getLayerModel().deleteAllGraphics();
        initActionWState();

        if (newSeries == null) {
            imageLayer.setImage(null, null);
            closeLens();
        } else {
            E media = selectedMedia;
            if (selectedMedia == null) {
                media =
                    newSeries.getMedia(tileOffset < 0 ? 0 : tileOffset,
                        (Filter<E>) actionsInView.get(ActionW.FILTERED_SERIES.cmd()), getCurrentSortComparator());
            }
            imageLayer.fireOpEvent(new ImageOpEvent(ImageOpEvent.OpEvent.SeriesChange, series, media, null));
            setImage(media);
        }

        eventManager.updateComponentsListener(this);

        // Set the sequence to the state OPEN
        if (newSeries != null) {
            newSeries.setOpen(true);
        }
    }

    protected void closingSeries(MediaSeries<E> mediaSeries) {
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

    public void setFocused(boolean focused) {
        if (series != null) {
            series.setFocused(focused);
        }
        if (focused && getBorder() == lostFocusBorder) {
            setBorder(focusBorder);
        } else if (!focused && getBorder() == focusBorder) {
            setBorder(lostFocusBorder);
        }
    }

    protected int getImageSize(E img, TagW tag1, TagW tag2) {
        Integer size = (Integer) img.getTagValue(tag1);
        if (size == null) {
            size = (Integer) img.getTagValue(tag2);
        }
        return (size == null) ? ImageFiler.TILESIZE : size;
    }

    protected Rectangle getImageBounds(E img) {
        if (img != null) {
            RenderedImage source = img.getImage((OpManager) actionsInView.get(ActionW.PREPROCESSING.cmd()));
            // Get the displayed width (adapted in case of the aspect ratio is not 1/1)
            int width =
                source == null || img.getRescaleX() != img.getRescaleY() ? img.getRescaleWidth(getImageSize(img,
                    TagW.ImageWidth, TagW.Columns)) : source.getWidth();
            int height =
                source == null || img.getRescaleX() != img.getRescaleY() ? img.getRescaleHeight(getImageSize(img,
                    TagW.ImageHeight, TagW.Rows)) : source.getHeight();
            return new Rectangle(0, 0, width, height);
        }
        return new Rectangle(0, 0, 512, 512);
    }

    protected void setImage(E img) {
        imageLayer.getDisplayOpManager().setEnabled(false);
        if (img == null) {
            actionsInView.put(ActionW.SPATIAL_UNIT.cmd(), Unit.PIXEL);
            imageLayer.setImage(null, null);
            getLayerModel().deleteAllGraphics();
            closeLens();
        } else {
            E oldImage = imageLayer.getSourceImage();
            if (img != null && !img.equals(oldImage)) {
                actionsInView.put(ActionW.SPATIAL_UNIT.cmd(), img.getPixelSpacingUnit());
                actionsInView.put(ActionW.PREPROCESSING.cmd(), null);
                final Rectangle modelArea = getImageBounds(img);
                AbstractLayer layer = getLayerModel().getLayer(AbstractLayer.MEASURE);
                if (layer != null) {
                    synchronized (this) {
                        // TODO Handle several layers
                        GraphicList gl = (GraphicList) img.getTagValue(TagW.MeasurementGraphics);
                        if (gl != null) {
                            // TODO handle graphics without shape, exclude them!
                            layer.setGraphics(gl);
                            synchronized (gl.list) {
                                for (Graphic graphic : gl.list) {
                                    graphic.updateLabel(img, this);
                                }
                            }
                        } else {
                            GraphicList graphics = new GraphicList();
                            img.setTag(TagW.MeasurementGraphics, graphics);
                            layer.setGraphics(graphics);
                        }
                    }
                }

                Rectangle2D area = getViewModel().getModelArea();
                if (!modelArea.equals(area)) {
                    ((DefaultViewModel) getViewModel()).adjustMinViewScaleFromImage(modelArea.width, modelArea.height);
                    getViewModel().setModelArea(modelArea);
                }

                imageLayer.fireOpEvent(new ImageOpEvent(ImageOpEvent.OpEvent.ImageChange, series, img, null));
                resetZoom();
                imageLayer.getDisplayOpManager().setEnabled(true);
                imageLayer.setImage(img, (OpManager) actionsInView.get(ActionW.PREPROCESSING.cmd()));
                if (panner != null) {
                    panner.updateImage();
                }
                if (lens != null) {
                    lens.updateZoom();
                }

                if (AuditLog.LOGGER.isInfoEnabled()) {
                    PlanarImage image = img.getImage();
                    if (image != null) {
                        StringBuffer pixSize = new StringBuffer();
                        SampleModel sm = image.getSampleModel();
                        if (sm != null) {
                            int[] spsize = sm.getSampleSize();
                            if (spsize != null && spsize.length > 0) {
                                pixSize.append(spsize[0]);
                                for (int i = 1; i < spsize.length; i++) {
                                    pixSize.append(',');
                                    pixSize.append(spsize[i]);
                                }
                            }
                        }
                        AuditLog.LOGGER.info("open:image size:{},{} depth:{}", //$NON-NLS-1$
                            new Object[] { image.getWidth(), image.getHeight(), pixSize.toString() });
                    }
                }
            }
        }

        ActionState spUnitAction = eventManager.getAction(ActionW.SPATIAL_UNIT);
        if (spUnitAction instanceof ComboItemListener) {
            ((ComboItemListener) spUnitAction).setSelectedItemWithoutTriggerAction(actionsInView
                .get(ActionW.SPATIAL_UNIT.cmd()));
        }
    }

    @Override
    public double getBestFitViewScale() {
        double viewScale = super.getBestFitViewScale();
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

    @Override
    public E getImage() {
        return imageLayer.getSourceImage();
    }

    @Override
    public RenderedImage getSourceImage() {
        E image = getImage();
        if (image == null) {
            return null;
        }
        return image.getImage((OpManager) actionsInView.get(ActionW.PREPROCESSING.cmd()));
    }

    public final void center() {
        Rectangle2D bound = getViewModel().getModelArea();
        setCenter(bound.getWidth() / 2.0, bound.getHeight() / 2.0);
    }

    public final void setCenter(double x, double y) {
        int w = getWidth();
        int h = getHeight();
        // Only apply when the panel size is not zero.
        if (w != 0 && h != 0) {
            double scale = getViewModel().getViewScale();
            setOrigin(x - (w - 1) / (2.0 * scale), y - (h - 1) / (2.0 * scale));
        }
    }

    /** Provides panning */
    public final void setOrigin(double x, double y) {
        getViewModel().setModelOffset(x, y);
        if (panner != null) {
            panner.updateImageSize();
        }
    }

    /** Provides panning */
    public final void moveOrigin(double x, double y) {
        setOrigin(getViewModel().getModelOffsetX() + x, getViewModel().getModelOffsetY() + y);
    }

    public final void moveOrigin(PanPoint point) {
        if (point != null) {
            if (PanPoint.STATE.Center.equals(point.getState())) {
                highlightedPosition.setHighlightedPosition(point.isHighlightedPosition());
                highlightedPosition.setLocation(point);
                setCenter(point.getX(), point.getY());
            } else if (PanPoint.STATE.Move.equals(point.getState())) {
                moveOrigin(point.getX(), point.getY());
            } else if (PanPoint.STATE.DragStart.equals(point.getState())) {
                startedDragPoint.setLocation(getViewModel().getModelOffsetX(), getViewModel().getModelOffsetY());
            } else if (PanPoint.STATE.Dragging.equals(point.getState())) {
                setOrigin(startedDragPoint.getX() + point.getX(), startedDragPoint.getY() + point.getY());
            }
        }
    }

    public Comparator<E> getCurrentSortComparator() {
        SeriesComparator<E> sort = (SeriesComparator<E>) actionsInView.get(ActionW.SORTSTACK.cmd());
        Boolean reverse = (Boolean) actionsInView.get(ActionW.INVERSESTACK.cmd());
        return (reverse != null && reverse) ? sort.getReversOrderComparator() : sort;
    }

    @Override
    public int getFrameIndex() {
        if (series instanceof Series) {
            return ((Series<E>) series).getImageIndex(imageLayer.getSourceImage(),
                (Filter<E>) actionsInView.get(ActionW.FILTERED_SERIES.cmd()), getCurrentSortComparator());
        }
        return -1;
    }

    public void setActionsInView(String action, Object value) {
        setActionsInView(action, value, false);
    }

    public void setActionsInView(String action, Object value, boolean repaint) {
        if (action != null) {
            actionsInView.put(action, value);
            if (repaint) {
                repaint();
            }
        }
    }

    public void setSelected(boolean selected) {
        setBorder(selected ? focusBorder : normalBorder);
        // Remove the selection of graphics
        getLayerModel().setSelectedGraphics(null);
        // Throws to the tool listener the current graphic selection.
        getLayerModel().fireGraphicsSelectionChanged(imageLayer);

        if (selected && series != null) {
            AuditLog.LOGGER.info("select:series nb:{}", series.getSeriesNumber()); //$NON-NLS-1$
        }
    }

    @Override
    public Font getFont() {
        // required when used getGraphics().getFont() in GraphicLabel
        return MeasureTool.viewSetting.getFont();
    }

    public Font getLayerFont() {
        int fontSize =
            // Set font size according to the view size
            (int) Math
                .ceil(10 / ((this.getGraphics().getFontMetrics(FontTools.getFont12()).stringWidth("0123456789") * 7.0) / getWidth())); //$NON-NLS-1$
        fontSize = fontSize < 6 ? 6 : fontSize > 16 ? 16 : fontSize;
        return new Font("SansSerif", 0, fontSize);
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
        Font defaultFont = getFont();
        g2d.setFont(defaultFont);

        imageLayer.drawImage(g2d);
        drawLayers(g2d, affineTransform, inverseTransform);

        g2d.translate(offsetX, offsetY);

        drawPointer(g2d);
        if (infoLayer != null) {
            g2d.setFont(getLayerFont());
            infoLayer.paint(g2d);
        }
        drawOnTop(g2d);

        g2d.setFont(defaultFont);
        g2d.setPaint(oldColor);
        g2d.setStroke(oldStroke);
    }

    protected void drawOnTop(Graphics2D g2d) {
    }

    @Override
    public void drawLayers(Graphics2D g2d, AffineTransform transform, AffineTransform inverseTransform) {
        if ((Boolean) actionsInView.get(ActionW.DRAW.cmd())) {
            getLayerModel().draw(
                g2d,
                transform,
                inverseTransform,
                new Rectangle2D.Double(modelToViewLength(getViewModel().getModelOffsetX()),
                    modelToViewLength(getViewModel().getModelOffsetY()), getWidth(), getHeight()));
        }
    }

    @Override
    public void zoom(double viewScale) {
        if (viewScale == 0.0) {
            viewScale = -getBestFitViewScale();
        }
        ImageOpNode node = imageLayer.getDisplayOpManager().getNode(ZoomOp.OP_NAME);
        E img = getImage();
        if (img != null && node != null) {
            node.setParam(ZoomOp.P_RATIO_X, viewScale * img.getRescaleX());
            node.setParam(ZoomOp.P_RATIO_Y, viewScale * img.getRescaleY());

            actionsInView.put(ActionW.ZOOM.cmd(), viewScale);
            super.zoom(Math.abs(viewScale));
            if (JMVUtils.getNULLtoTrue(actionsInView.get("op.update"))) {
                imageLayer.updateAllImageOperations();
            }
            updateAffineTransform();
            if (panner != null) {
                panner.updateImageSize();
            }
        }
    }

    protected void updateAffineTransform() {
        double viewScale = getViewModel().getViewScale();
        affineTransform.setToScale(viewScale, viewScale);

        OpManager dispOp = getDisplayOpManager();
        Boolean flip = JMVUtils.getNULLtoFalse(dispOp.getParamValue(FlipOp.OP_NAME, FlipOp.P_FLIP));
        Integer rotationAngle = (Integer) dispOp.getParamValue(RotationOp.OP_NAME, RotationOp.P_ROTATE);

        if (rotationAngle != null && rotationAngle > 0) {
            if (flip != null && flip) {
                rotationAngle = 360 - rotationAngle;
            }
            Rectangle2D imageCanvas = getViewModel().getModelArea();
            affineTransform.rotate(Math.toRadians(rotationAngle), imageCanvas.getWidth() / 2.0,
                imageCanvas.getHeight() / 2.0);
        }
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
            affineTransform.scale(-1.0, 1.0);
            affineTransform.translate(-getViewModel().getModelArea().getWidth(), 0.0);
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

    public Object getLensActionValue(String action) {
        if (lens == null) {
            return null;
        }
        return lens.getActionValue(action);
    }

    public void changeZoomInterpolation(int interpolation) {
        Integer val = (Integer) getDisplayOpManager().getParamValue(ZoomOp.OP_NAME, ZoomOp.P_INTERPOLATION);
        boolean update = val == null || val != interpolation;
        if (update) {
            getDisplayOpManager().setParamValue(ZoomOp.OP_NAME, ZoomOp.P_INTERPOLATION, interpolation);
            if (lens != null) {
                lens.getDisplayOpManager().setParamValue(ZoomOp.OP_NAME, ZoomOp.P_INTERPOLATION, interpolation);
                lens.updateZoom();
            }
            imageLayer.updateAllImageOperations();
        }
    }

    public OpManager getDisplayOpManager() {
        return imageLayer.getDisplayOpManager();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (series == null) {
            return;
        }
        OpManager manager = imageLayer.getDisplayOpManager();
        final String command = evt.getPropertyName();
        if (command.equals(ActionW.SYNCH.cmd())) {
            SynchEvent synch = (SynchEvent) evt.getNewValue();
            if (synch instanceof SynchCineEvent) {
                SynchCineEvent value = (SynchCineEvent) synch;
                AbstractLayer layer = getLayerModel().getLayer(AbstractLayer.CROSSLINES);
                if (layer != null) {
                    layer.deleteAllGraphic();
                }

                E imgElement = getImage();
                if (value != null) {
                    if (value.getView() == this) {
                        if (tileOffset != 0) {
                            // Index could have changed when loading series.
                            imgElement =
                                series.getMedia(value.getSeriesIndex() + tileOffset,
                                    (Filter<E>) actionsInView.get(ActionW.FILTERED_SERIES.cmd()),
                                    getCurrentSortComparator());
                        } else if (value.getMedia() instanceof ImageElement) {
                            imgElement = (E) value.getMedia();
                        }
                    } else if (value.getLocation() != null) {
                        Boolean cutlines = (Boolean) actionsInView.get(ActionW.SYNCH_CROSSLINE.cmd());
                        if (cutlines != null && cutlines) {
                            // Compute cutlines from the location of selected image
                            computeCrosslines(value.getLocation().doubleValue());
                        } else {
                            double location = value.getLocation().doubleValue();
                            // TODO add a way in GUI to resynchronize series. Offset should be in Series tag and related
                            // to
                            // a specific series
                            // Double offset = (Double) actionsInView.get(ActionW.STACK_OFFSET.cmd());
                            // if (offset != null) {
                            // location += offset;
                            // }
                            imgElement =
                                series.getNearestImage(location, tileOffset,
                                    (Filter<E>) actionsInView.get(ActionW.FILTERED_SERIES.cmd()),
                                    getCurrentSortComparator());

                            AuditLog.LOGGER.info("synch:series nb:{}", series.getSeriesNumber()); //$NON-NLS-1$
                        }
                    }

                }

                Double zoomFactor = (Double) actionsInView.get(ActionW.ZOOM.cmd());
                // Avoid to reset zoom when the mode is not best fit
                if (zoomFactor != null && zoomFactor >= 0.0) {
                    Object zoomType = actionsInView.get(DefaultView2d.zoomTypeCmd);
                    actionsInView.put(DefaultView2d.zoomTypeCmd, ZoomType.CURRENT);
                    setImage(imgElement);
                    actionsInView.put(DefaultView2d.zoomTypeCmd, zoomType);
                } else {
                    setImage(imgElement);
                }
            } else {
                propertyChange(synch);
            }
        } else if (command.equals(ActionW.IMAGE_PIX_PADDING.cmd())) {
            if (manager.setParamValue(WindowOp.OP_NAME, command, evt.getNewValue())) {
                imageLayer.updateAllImageOperations();
            }
        } else if (command.equals(ActionW.PROGRESSION.cmd())) {
            actionsInView.put(command, evt.getNewValue());
            imageLayer.updateAllImageOperations();
        }
    }

    private void propertyChange(final SynchEvent synch) {
        SynchData synchData = (SynchData) actionsInView.get(ActionW.SYNCH_LINK.cmd());
        if (synchData != null && Mode.None.equals(synchData.getMode())) {
            return;
        }

        OpManager manager = imageLayer.getDisplayOpManager();

        for (Entry<String, Object> entry : synch.getEvents().entrySet()) {
            String command = entry.getKey();
            if (synchData != null && !synchData.isActionEnable(command)) {
                continue;
            }
            if (command.equals(ActionW.WINDOW.cmd()) || command.equals(ActionW.LEVEL.cmd())) {
                if (manager.setParamValue(WindowOp.OP_NAME, command, ((Integer) entry.getValue()).floatValue())) {
                    imageLayer.updateAllImageOperations();
                }
            } else if (command.equals(ActionW.ROTATION.cmd())) {
                if (manager.setParamValue(RotationOp.OP_NAME, RotationOp.P_ROTATE, entry.getValue())) {
                    imageLayer.updateAllImageOperations();
                    updateAffineTransform();
                }
            } else if (command.equals(ActionW.RESET.cmd())) {
                reset();
            } else if (command.equals(ActionW.ZOOM.cmd())) {
                zoom((Double) entry.getValue());
            } else if (command.equals(ActionW.LENSZOOM.cmd())) {
                if (lens != null) {
                    lens.setActionInView(ActionW.ZOOM.cmd(), entry.getValue());
                }
            } else if (command.equals(ActionW.LENS.cmd())) {
                Boolean showLens = (Boolean) entry.getValue();
                actionsInView.put(command, showLens);
                if (showLens) {
                    if (lens == null) {
                        lens = new ZoomWin<E>(this);
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
                Object point = entry.getValue();
                // ImageViewerPlugin<E> view = eventManager.getSelectedView2dContainer();
                // if (view != null) {
                // if(!view.getSynchView().isActionEnable(ActionW.ROTATION)){
                //
                // }
                // }
                if (point instanceof PanPoint) {
                    moveOrigin((PanPoint) entry.getValue());
                }

            } else if (command.equals(ActionW.FLIP.cmd())) {
                // Horizontal flip is applied after rotation (To be compliant with DICOM PR)
                if (manager.setParamValue(FlipOp.OP_NAME, FlipOp.P_FLIP, entry.getValue())) {
                    imageLayer.updateAllImageOperations();
                    updateAffineTransform();
                }
            } else if (command.equals(ActionW.LUT.cmd())) {
                if (manager.setParamValue(PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT, entry.getValue())) {
                    imageLayer.updateAllImageOperations();
                }
            } else if (command.equals(ActionW.INVERSELUT.cmd())) {
                if (manager.setParamValue(WindowOp.OP_NAME, command, entry.getValue())) {
                    manager.setParamValue(PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT_INVERSE, entry.getValue());
                    // Update VOI LUT if pixel padding
                    imageLayer.updateAllImageOperations();
                }
            } else if (command.equals(ActionW.FILTER.cmd())) {
                if (manager.setParamValue(FilterOp.OP_NAME, FilterOp.P_KERNEL_DATA, entry.getValue())) {
                    imageLayer.updateAllImageOperations();
                }
            } else if (command.equals(ActionW.SPATIAL_UNIT.cmd())) {
                actionsInView.put(command, entry.getValue());

                // TODO update only measure and limit when selected view share graphics
                List<Graphic> list = this.getLayerModel().getAllGraphics();
                for (Graphic graphic : list) {
                    graphic.updateLabel(true, this);
                }

            }
            if (lens != null) {
                // Transmit to the lens the command in case the source image has been freeze (for updating rotation and
                // flip
                // => will keep consistent display)
                lens.setCommandFromParentView(command, entry.getValue());
                lens.updateZoom();
            }
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
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {

            // TODO - should be handled in EventManager !!!
            if (e.isControlDown()) {
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
                eventManager.fireSeriesViewerListeners(new SeriesViewerEvent(eventManager.getSelectedView2dContainer(),
                    null, null, EVENT.TOOGLE_INFO));
            }
        } else {
            ActionW action = eventManager.getActionFromkeyEvent(e.getKeyCode(), e.getModifiers());
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
        if ((pointerType & CENTER_POINTER) == CENTER_POINTER) {
            drawPointer(g, (getWidth() - 1) * 0.5, (getHeight() - 1) * 0.5);
        }
        if ((pointerType & HIGHLIGHTED_POINTER) == HIGHLIGHTED_POINTER && highlightedPosition.isHighlightedPosition()) {
            // Display the position on the center of the pixel (constant position even with a high zoom factor)
            drawPointer(g, modelToViewX(highlightedPosition.getX() + 0.5),
                modelToViewY(highlightedPosition.getY() + 0.5));
        }
    }

    public int getPointerType() {
        return pointerType;
    }

    public void setPointerType(int pointerType) {
        this.pointerType = pointerType;
    }

    public void addPointerType(int i) {
        this.pointerType |= i;
    }

    public void resetPointerType(int i) {
        this.pointerType &= ~i;
    }

    public Point2D getHighlightedPosition() {
        return highlightedPosition;
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
                    this.getGraphics().getFontMetrics(getLayerFont())
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
            if (e.isConsumed() || (e.getModifiersEx() & buttonMask) == 0) {
                return;
            }

            // Convert mouse event point to real image coordinate point (without geometric transformation)
            MouseEventDouble mouseEvt = new MouseEventDouble(e);
            mouseEvt.setImageCoordinates(getImageCoordinatesFromMouse(e.getX(), e.getY()));

            // Do nothing and return if current dragSequence is not completed
            if (ds != null && !ds.completeDrag(mouseEvt)) {
                return;
            }

            Cursor newCursor = AbstractLayerModel.DEFAULT_CURSOR;

            // Avoid any dragging on selection when Shift Button is Down
            if (!mouseEvt.isShiftDown()) {
                // Evaluates if mouse is on a dragging position, creates a DragSequence and changes cursor consequently
                Graphic firstGraphicIntersecting = getLayerModel().getFirstGraphicIntersecting(mouseEvt);

                if (firstGraphicIntersecting instanceof AbstractDragGraphic) {
                    AbstractDragGraphic dragGraph = (AbstractDragGraphic) firstGraphicIntersecting;
                    List<AbstractDragGraphic> selectedDragGraphList = getLayerModel().getSelectedDragableGraphics();

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
            getLayerModel().fireGraphicsSelectionChanged(imageLayer);

        }

        @Override
        public void mouseReleased(MouseEvent e) {
            int buttonMask = getButtonMask();

            // Check if extended modifier of mouse event equals the current buttonMask
            // Note that extended modifiers are not triggered in mouse released
            // Also asserts that Mouse adapter is not disable
            if ((e.getModifiers() & buttonMask) == 0) {
                return;
            }

            // Do nothing and return if no dragSequence exist
            if (ds == null) {
                return;
            }

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
            getLayerModel().fireGraphicsSelectionChanged(imageLayer);

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
            if (e.isConsumed() || (e.getModifiersEx() & buttonMask) == 0) {
                return;
            }

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
                    Graphic firstGraphicIntersecting = getLayerModel().getFirstGraphicIntersecting(mouseEvt);

                    if (firstGraphicIntersecting instanceof AbstractDragGraphic) {
                        AbstractDragGraphic dragGraph = (AbstractDragGraphic) firstGraphicIntersecting;
                        List<AbstractDragGraphic> selectedDragGraphList = getLayerModel().getSelectedDragableGraphics();

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
        public void mousePressed(MouseEvent evt) {
            ImageViewerPlugin<E> pane = eventManager.getSelectedView2dContainer();
            if (pane == null) {
                return;
            }
            if (evt.getClickCount() == 2) {
                pane.maximizedSelectedImagePane(DefaultView2d.this, evt);
                return;
            }

            // Do select the view when pressing on a view button
            for (ViewButton b : getViewButtons()) {
                if (b.isVisible() && b.contains(evt.getPoint())) {
                    DefaultView2d.this.setCursor(AbstractLayerModel.DEFAULT_CURSOR);
                    evt.consume();
                    b.showPopup(evt.getComponent(), evt.getX(), evt.getY());
                    return;
                }
            }

            if (pane.isContainingView(DefaultView2d.this) && pane.getSelectedImagePane() != DefaultView2d.this) {
                // register all actions of the EventManager with this view waiting the focus gained in some cases is not
                // enough, because others mouseListeners are triggered before the focus event (that means before
                // registering the view in the EventManager)
                pane.setSelectedImagePane(DefaultView2d.this);
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
                    final ViewTransferHandler imageTransferHandler = new ViewTransferHandler();
                    imageTransferHandler.exportToClipboard(DefaultView2d.this, Toolkit.getDefaultToolkit()
                        .getSystemClipboard(), TransferHandler.COPY);
                }
            };
        exportToClipboardAction.putValue(Action.ACCELERATOR_KEY,
            KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_MASK));
        list.add(exportToClipboardAction);

        // TODO exclude big images?
        exportToClipboardAction = new AbstractAction(Messages.getString("DefaultView2d.clipboard_real")) { //$NON-NLS-1$

                @Override
                public void actionPerformed(ActionEvent e) {
                    final ImageTransferHandler imageTransferHandler = new ImageTransferHandler();
                    imageTransferHandler.exportToClipboard(DefaultView2d.this, Toolkit.getDefaultToolkit()
                        .getSystemClipboard(), TransferHandler.COPY);
                }
            };
        list.add(exportToClipboardAction);

        return list;
    }

    public abstract void enableMouseAndKeyListener(MouseActions mouseActions);

    public static final AffineTransform getAffineTransform(MouseEvent mouseevent) {
        if (mouseevent != null && mouseevent.getSource() instanceof Image2DViewer) {
            return ((Image2DViewer) mouseevent.getSource()).getAffineTransform();
        }
        return null;
    }

    public void resetZoom() {
        ZoomType type = (ZoomType) actionsInView.get(zoomTypeCmd);
        if (!ZoomType.CURRENT.equals(type)) {
            if (ZoomType.BEST_FIT.equals(type)) {
                zoom(-getBestFitViewScale());
                center();
            } else {
                zoom(1.0);
            }
        }
    }

    public void resetPan() {
        ZoomType type = (ZoomType) actionsInView.get(zoomTypeCmd);
        if (ZoomType.BEST_FIT.equals(type)) {
            center();
        } else {
            setOrigin(0, 0);
        }
    }

    public void reset() {
        ImageViewerPlugin<E> pane = eventManager.getSelectedView2dContainer();
        if (pane != null) {
            pane.resetMaximizedSelectedImagePane(this);
        }

        initActionWState();
        imageLayer.fireOpEvent(new ImageOpEvent(ImageOpEvent.OpEvent.ResetDisplay, series, getImage(), null));
        imageLayer.updateAllImageOperations();
        // TODO should throw only image process
        // imageLayer.getDisplayOpManager().setEnabled(true);
        resetZoom();
        resetPan();
        eventManager.updateComponentsListener(this);
    }

    public List<ViewButton> getViewButtons() {
        return viewButtons;
    }

}
