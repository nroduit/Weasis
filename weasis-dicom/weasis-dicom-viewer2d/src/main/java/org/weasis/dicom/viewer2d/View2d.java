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
package org.weasis.dicom.viewer2d;

import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.media.jai.PlanarImage;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;

import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.FlipOperation;
import org.weasis.core.api.image.OperationsManager;
import org.weasis.core.api.image.PseudoColorOperation;
import org.weasis.core.api.image.RotationOperation;
import org.weasis.core.api.image.WindowLevelOperation;
import org.weasis.core.api.image.ZoomOperation;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagElement;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.PannerListener;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.graphic.DragLayer;
import org.weasis.core.ui.graphic.PolygonGraphic;
import org.weasis.core.ui.graphic.RenderedImageLayer;
import org.weasis.core.ui.graphic.TempLayer;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.graphic.model.Tools;
import org.weasis.core.ui.util.UriListFlavor;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.display.OverlayOperation;
import org.weasis.dicom.codec.display.PresetWindowLevel;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.codec.geometry.IntersectSlice;
import org.weasis.dicom.codec.geometry.LocalizerPoster;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.LoadLocalDicom;
import org.weasis.dicom.explorer.wado.LoadSeries;

public class View2d extends DefaultView2d<DicomImageElement> {
    private final ContextMenuHandler contextMenuHandler = new ContextMenuHandler();

    public View2d(ImageViewerEventManager<DicomImageElement> eventManager) {
        super(eventManager);
        OperationsManager manager = imageLayer.getOperations();
        manager.addImageOperationAction(new WindowLevelOperation());
        manager.addImageOperationAction(new OverlayOperation());
        manager.addImageOperationAction(new FlipOperation());
        manager.addImageOperationAction(new RotationOperation());
        manager.addImageOperationAction(new ZoomOperation());
        manager.addImageOperationAction(new PseudoColorOperation());

        infoLayer = new InfoLayer(this);
        DragLayer layer = new DragLayer(getLayerModel(), Tools.MEASURE.getId());
        getLayerModel().addLayer(layer);
        layer = new DragLayer(getLayerModel(), Tools.CROSSLINES.getId());
        getLayerModel().addLayer(layer);
        TempLayer layerTmp = new TempLayer(getLayerModel());
        getLayerModel().addLayer(layerTmp);

    }

    @Override
    public void registerDefaultListeners() {
        super.registerDefaultListeners();
        setTransferHandler(new SequenceHandler());

        addComponentListener(new ComponentAdapter() {

            @Override
            public void componentResized(ComponentEvent e) {
                // TODO add preference keep image in best fit when resize

                Double currentZoom = (Double) actionsInView.get(ActionW.ZOOM);
                // Resize in best fit window only if the previous value is also a best fit value.
                if (currentZoom <= 0.0) {
                    zoom(0.0);
                }
                center();
            }
        });
        // enableMouseAndKeyListener(EventManager.getInstance().getMouseActions());
    }

    @Override
    protected void initActionWState() {
        super.initActionWState();
        actionsInView.put(ActionW.PRESET, PresetWindowLevel.DEFAULT);
        actionsInView.put(ActionW.SORTSTACK, SortSeriesStack.instanceNumber);
        actionsInView.put(ActionW.IMAGE_OVERLAY, true);
        actionsInView.put(ActionW.VIEWINGPROTOCOL, Modality.ImageModality);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        if (series == null) {
            return;
        }
        if (evt.getPropertyName().equals(ActionW.PRESET.getCommand())) {
            actionsInView.put(ActionW.PRESET, evt.getNewValue());
        } else if (evt.getPropertyName().equals(ActionW.IMAGE_OVERLAY.getCommand())) {
            actionsInView.put(ActionW.IMAGE_OVERLAY, evt.getNewValue());
            imageLayer.updateImageOperation(OverlayOperation.name);
        } else if (evt.getPropertyName().equals(ActionW.SORTSTACK.getCommand())) {
            actionsInView.put(ActionW.SORTSTACK, evt.getNewValue());
            sortStack();
        } else if (evt.getPropertyName().equals(ActionW.VIEWINGPROTOCOL.getCommand())) {
            actionsInView.put(ActionW.VIEWINGPROTOCOL, evt.getNewValue());
            repaint();
        } else if (evt.getPropertyName().equals(ActionW.INVERSESTACK.getCommand())) {
            actionsInView.put(ActionW.INVERSESTACK, evt.getNewValue());
            sortStack();
        }
    }

    @Override
    public void setSeries(MediaSeries<DicomImageElement> series, int defaultIndex) {
        MediaSeries<DicomImageElement> oldsequence = this.series;
        this.series = series;
        if (oldsequence != null && oldsequence != series) {
            closingSeries(oldsequence);

        }
        if (series == null) {
            imageLayer.setImage(null);
            getLayerModel().deleteAllGraphics();
        } else {
            defaultIndex = defaultIndex < 0 || defaultIndex >= series.size() ? 0 : defaultIndex;
            frameIndex = defaultIndex + tileOffset;
            actionsInView.put(ActionW.PRESET, PresetWindowLevel.DEFAULT);
            setImage(series.getMedia(frameIndex), true);
            Double val = (Double) actionsInView.get(ActionW.ZOOM);
            zoom(val == null ? 1.0 : val);
            center();
        }
        eventManager.updateComponentsListener(this);

        // Set the sequence to the state OPEN
        if (series != null && oldsequence != series) {
            series.setOpen(true);
        }
    }

    @Override
    protected void setWindowLevel(DicomImageElement img) {
        if (PresetWindowLevel.DEFAULT.equals(actionsInView.get(ActionW.PRESET))) {
            actionsInView.put(ActionW.WINDOW, img.getDefaultWindow());
            actionsInView.put(ActionW.LEVEL, img.getDefaultLevel());
        } else if (PresetWindowLevel.AUTO.equals(actionsInView.get(ActionW.PRESET))) {
            float min = img.getMinValue();
            float max = img.getMaxValue();
            actionsInView.put(ActionW.WINDOW, max - min);
            actionsInView.put(ActionW.LEVEL, (max - min) / 2.0f + min);
        }
    }

    protected void sortStack() {
        Comparator<DicomImageElement> sortComparator =
            (Comparator<DicomImageElement>) actionsInView.get(ActionW.SORTSTACK);
        if (sortComparator != null) {
            series.sort((Boolean) actionsInView.get(ActionW.INVERSESTACK) ? Collections.reverseOrder(sortComparator)
                : sortComparator);
            Double val = (Double) actionsInView.get(ActionW.ZOOM);
            // If zoom has not been defined or was besfit, set image in bestfit zoom mode
            boolean rescaleView = (val == null || val <= 0.0);
            setImage(series.getMedia(frameIndex), rescaleView);
            if (rescaleView) {
                val = (Double) actionsInView.get(ActionW.ZOOM);
                zoom(val == null ? 1.0 : val);
                center();
            }
        }
    }

    @Override
    protected void computeCrosslines(double location) {

        DefaultView2d view2DPane = eventManager.getSelectedViewPane();
        MediaSeries<DicomImageElement> selSeries = view2DPane == null ? null : view2DPane.getSeries();
        if (selSeries != null) {
            // TODO change slicelocation to
            // Get the current image of the selected Series
            DicomImageElement selImage = selSeries.getMedia(selSeries.getNearestIndex(location));
            // Get the first and the last image of the selected Series according to Slice Location
            ArrayList<DicomImageElement> list = new ArrayList<DicomImageElement>(selSeries.getMedias());
            DicomImageElement firstImage = null;
            DicomImageElement lastImage = null;
            if (list.size() > 2) {
                double min = Double.MAX_VALUE;
                double max = -Double.MAX_VALUE;
                for (DicomImageElement dcm : list) {
                    double[] loc = (double[]) dcm.getTagValue(TagElement.SlicePosition);
                    if (loc != null) {
                        double position = loc[0] + loc[1] + loc[2];
                        if (min > position) {
                            min = position;
                            firstImage = dcm;
                        }
                        if (max < position) {
                            max = position;
                            lastImage = dcm;
                        }
                    }
                }
                if (firstImage == null) {
                    firstImage = list.get(0);
                    lastImage = list.get(list.size() - 1);
                }
            }
            DicomImageElement image = this.getImage();
            if (image != null) {
                GeometryOfSlice sliceGeometry = image.getSliceGeometry();
                if (sliceGeometry != null) {
                    IntersectSlice slice = new IntersectSlice(sliceGeometry);
                    if (firstImage != null) {
                        addCrossline(firstImage, slice, false);
                    }
                    if (lastImage != null) {
                        addCrossline(lastImage, slice, false);
                    }
                    if (selImage != null) {
                        addCrossline(selImage, slice, true);
                    }
                    repaint();
                }
            }
        }

    }

    private void addCrossline(DicomImageElement selImage, LocalizerPoster localizer, boolean fill) {
        GeometryOfSlice sliceGeometry = selImage.getSliceGeometry();
        if (sliceGeometry != null) {
            float[] xyCoord = localizer.getOutlineOnLocalizerForThisGeometry(sliceGeometry);
            if (xyCoord != null) {
                Color color = fill ? Color.blue : Color.cyan;
                PolygonGraphic graphic = new PolygonGraphic(xyCoord, 1.0f, color, fill, true);
                AbstractLayer layer = getLayerModel().getLayer(Tools.CROSSLINES.getId());
                if (layer instanceof DragLayer) {
                    ((DragLayer) layer).addGraphic(graphic);
                }
            }
        }
    }

    @Override
    public synchronized void enableMouseAndKeyListener(MouseActions actions) {
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
            adapter.setButtonMaskEx(adapter.getButtonMaskEx() | InputEvent.BUTTON2_DOWN_MASK);
        } else {
            addMouseAdapter(actions.getMiddle(), InputEvent.BUTTON2_DOWN_MASK);// middle mouse button
        }
        if (actions.getRight().equals(actions.getLeft()) || actions.getRight().equals(actions.getMiddle())) {
            // If mouse action is already registered, only add the modifier mask
            MouseActionAdapter adapter = getMouseAdapter(actions.getRight());
            adapter.setButtonMaskEx(adapter.getButtonMaskEx() | InputEvent.BUTTON3_DOWN_MASK);
        } else {
            addMouseAdapter(actions.getRight(), InputEvent.BUTTON3_DOWN_MASK); // right mouse button
        }
        this.addMouseWheelListener(getMouseAdapter(actions.getWheel()));
    }

    private void addMouseAdapter(String actionName, int buttonMask) {
        MouseActionAdapter adapter = getMouseAdapter(actionName);
        if (adapter == null) {
            return;
        }
        adapter.setButtonMaskEx(adapter.getButtonMaskEx() | buttonMask);
        if (adapter == mouseClickHandler) {
            this.addKeyListener(drawingsKeyListeners);
        } else if (adapter instanceof PannerListener) {
            ((PannerListener) adapter).reset();
            this.addKeyListener((PannerListener) adapter);
        }

        if (actionName.equals(ActionW.WINLEVEL.getCommand())) {
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
        } else if (actionName.equals(ActionW.WINDOW.getCommand())) {
            adapter.setMoveOnX(false);
        } else if (actionName.equals(ActionW.LEVEL.getCommand())) {
            adapter.setInverse(true);
        }
        this.addMouseListener(adapter);
        this.addMouseMotionListener(adapter);
    }

    private MouseActionAdapter getMouseAdapter(String action) {
        if (action.equals(ActionW.MEASURE.getCommand())) {
            return mouseClickHandler;
        } else if (action.equals(ActionW.PAN.getCommand())) {
            return getAction(ActionW.PAN);
        } else if (action.equals(ActionW.CONTEXTMENU.getCommand())) {
            return contextMenuHandler;
        } else if (action.equals(ActionW.WINDOW.getCommand())) {
            return getAction(ActionW.WINDOW);
        } else if (action.equals(ActionW.LEVEL.getCommand())) {
            return getAction(ActionW.LEVEL);
        }
        // Tricky action, see in addMouseAdapter()
        else if (action.equals(ActionW.WINLEVEL.getCommand())) {
            return getAction(ActionW.LEVEL);
        } else if (action.equals(ActionW.SCROLL_SERIES.getCommand())) {
            return getAction(ActionW.SCROLL_SERIES);
        } else if (action.equals(ActionW.ZOOM.getCommand())) {
            return getAction(ActionW.ZOOM);
        } else if (action.equals(ActionW.ROTATION.getCommand())) {
            return getAction(ActionW.ROTATION);
        }
        return null;
    }

    private void resetMouseAdapter() {
        for (ActionState adapter : eventManager.getAllActionValues()) {
            if (adapter instanceof MouseActionAdapter) {
                ((MouseActionAdapter) adapter).setButtonMaskEx(0);
            }
        }
        // reset context menu that is a field of this instance
        contextMenuHandler.setButtonMaskEx(0);
        mouseClickHandler.setButtonMaskEx(0);
    }

    private MouseActionAdapter getAction(ActionW action) {
        ActionState a = eventManager.getAction(action);
        if (a instanceof MouseActionAdapter) {
            return (MouseActionAdapter) a;
        }
        return null;
    }

    @Override
    public String getPixelInfo(Point p, RenderedImageLayer<DicomImageElement> imageLayer) {
        DicomImageElement dicom = imageLayer.getSourceImage();
        StringBuffer message = new StringBuffer();
        if (dicom != null && imageLayer.getReadIterator() != null) {
            PlanarImage image = dicom.getImage();
            if (image != null && p.x >= 0 && p.y >= 0 && p.x < image.getWidth() && p.y < image.getHeight()) {
                try {
                    int[] c = { 0, 0, 0 };
                    imageLayer.getReadIterator().getPixel(p.x, p.y, c); // read the pixel

                    if (image.getSampleModel().getNumBands() == 1) {
                        float val = (dicom).pixel2rescale(c[0]);
                        message.append((int) val);
                        if (dicom.getPixelValueUnit() != null) {
                            message.append(" " + dicom.getPixelValueUnit()); //$NON-NLS-1$
                        }
                    } else {
                        // TODO add preference for Pixel value type (RGB, IHS...) and pixel position (pix, real)
                        message.append("R=" + c[0] + " G=" + c[1] + " B=" + c[2]); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        // float[] ihs = IHSColorSpace.getInstance().fromRGB(new float[]
                        // {
                        // c[0] / 255f, c[1] / 255f, c[2] / 255f
                        // });
                        // c[0] = (int) (ihs[0] * 255f);
                        // c[1] = (int) ((ihs[1] / (Math.PI * 2)) * 255f);
                        // c[2] = (int) (ihs[2] * 255f);
                        //
                        // message.append(" (I = " + c[0] + ", H = " + c[1] + ", S = " + c[2] + ")");
                    }
                    message.append(" - (" + p.x + "," + p.y + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    // double[] pixelSpacing = (double[]) imageElement.getTagValue(TagElement.PixelSpacing);
                    // Unit unit = pixelSpacing == null ? Unit.PIXEL : Unit.MILLIMETER;
                    // if (pixelSpacing != null) {
                    // message.append(" (" + DecFormater.twoDecimal(imageElement.getPixelSizeX() * p.x));
                    // message.append(" " + unit.getAbbreviation() + ",");
                    // message.append(DecFormater.twoDecimal(imageElement.getPixelSizeY() * p.y));
                    // message.append(" " + unit.getAbbreviation() + ")");
                    // }
                } catch (ArrayIndexOutOfBoundsException ex) {
                }
            } else {
                message.append(Messages.getString("View2d.out_img")); //$NON-NLS-1$
            }
        }
        return message.toString();
    }

    @Override
    public void handleLayerChanged(RenderedImageLayer layer) {
        repaint();
    }

    @Override
    public void focusGained(FocusEvent e) {
        if (!e.isTemporary()) {
            ImageViewerPlugin<DicomImageElement> pane = eventManager.getSelectedView2dContainer();
            if (pane != null && pane.isContainingView(this)) {
                pane.setSelectedImagePane(this);
            }
        }
    }

    private class SequenceHandler extends TransferHandler {

        public SequenceHandler() {
            super("series"); //$NON-NLS-1$
        }

        @Override
        public Transferable createTransferable(JComponent comp) {
            if (comp instanceof Thumbnail) {
                MediaSeries t = ((Thumbnail) comp).getSeries();
                if (t instanceof Series) {
                    return t;
                }
            }
            return null;
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop()) {
                return false;
            }
            if (support.isDataFlavorSupported(Series.sequenceDataFlavor)
                || support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                || support.isDataFlavorSupported(UriListFlavor.uriListFlavor)) {
                return true;
            }
            return false;
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
            if (dicomView == null) {
                return false;
            }
            DicomModel model = (DicomModel) dicomView.getDataExplorerModel();
            Transferable transferable = support.getTransferable();

            List<File> files = null;
            // Not supported on Linux
            if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                try {
                    files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return dropDicomFiles(files, model);
            }
            // When dragging a file or group of files from a Gnome or Kde environment
            // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4899516
            else if (support.isDataFlavorSupported(UriListFlavor.uriListFlavor)) {
                try {
                    // Files with spaces in the filename trigger an error
                    // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6936006
                    String val = (String) transferable.getTransferData(UriListFlavor.uriListFlavor);
                    files = UriListFlavor.textURIListToFileList(val);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return dropDicomFiles(files, model);
            }

            DicomSeries seq;
            try {
                seq = (DicomSeries) transferable.getTransferData(Series.sequenceDataFlavor);
                if (seq != null) {
                    MediaSeriesGroup p1 = model.getParent(seq, DicomModel.patient);
                    MediaSeriesGroup p2 = null;
                    ViewerPlugin openPlugin = null;
                    synchronized (UIManager.VIEWER_PLUGINS) {
                        plugin: for (final ViewerPlugin<? extends MediaElement> p : UIManager.VIEWER_PLUGINS) {
                            if (p instanceof View2dContainer) {
                                for (MediaSeries s : p.getOpenSeries()) {
                                    p2 = model.getParent(s, DicomModel.patient);
                                    if (p1.equals(p2)) {
                                        if (!((View2dContainer) p).isContainingView(View2d.this)) {
                                            openPlugin = p;
                                        }
                                        break plugin;
                                    }
                                }
                            }
                        }
                    }

                    if (!p1.equals(p2)) {
                        SeriesViewerFactory plugin =
                            UIManager.getViewerFactory(eventManager.getSelectedView2dContainer());
                        if (plugin != null) {
                            LoadSeries.openSequenceInPlugin(plugin, new Series[] { seq }, model);

                        }
                        return false;
                    } else if (openPlugin != null) {
                        openPlugin.setSelectedAndGetFocus();
                        openPlugin.addSeries(seq);
                        openPlugin.setSelected(true);
                        return false;
                    }
                }
            } catch (Exception e) {
                return false;
            }
            ImageViewerPlugin<DicomImageElement> pane = EventManager.getInstance().getSelectedView2dContainer();
            if (SynchView.Mode.Tile.equals(pane.getSynchView().getMode())) {
                pane.addSeries(seq);
                return true;
            }
            setSeries(seq);
            requestFocusInWindow();
            return true;

        }

        private boolean dropDicomFiles(List<File> files, DicomModel model) {
            if (files != null) {
                LoadLocalDicom dicom = new LoadLocalDicom(files.toArray(new File[files.size()]), true, model);
                DicomModel.loadingExecutor.execute(dicom);
                return true;
            }
            return false;
        }
    }

    class ContextMenuHandler extends MouseActionAdapter {

        @Override
        public void mousePressed(final MouseEvent mouseevent) {
            int buttonMask = getButtonMaskEx();
            if ((mouseevent.getModifiersEx() & buttonMask) != 0) {
                if (eventManager.updateComponentsListener(View2d.this)) {
                    JPopupMenu popupMenu = new JPopupMenu();
                    final EventManager event = EventManager.getInstance();
                    JMenuItem item = new JMenuItem(Messages.getString("View2d.left_mouse")); //$NON-NLS-1$
                    Font font = item.getFont();
                    item.setFont(new Font(font.getFamily(), Font.BOLD, font.getSize()));
                    item.setFocusable(false);
                    popupMenu.add(item);
                    popupMenu.setLabel(MouseActions.LEFT);
                    String action = event.getMouseActions().getLeft();
                    ActionW[] actionsButtons = ViewerToolBar.actionsButtons;
                    ButtonGroup groupButtons = new ButtonGroup();
                    ImageViewerPlugin<DicomImageElement> view = eventManager.getSelectedView2dContainer();
                    if (view != null) {
                        final ViewerToolBar toolBar = view.getViewerToolBar();
                        if (toolBar != null) {
                            ActionListener leftButtonAction = new ActionListener() {

                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    if (e.getSource() instanceof JRadioButtonMenuItem) {
                                        JRadioButtonMenuItem item = (JRadioButtonMenuItem) e.getSource();
                                        toolBar.changeButtonState(toolBar.getMouseLeft(), MouseActions.LEFT, item
                                            .getActionCommand());
                                    }
                                }
                            };

                            for (int i = 0; i < ViewerToolBar.actionsButtons.length; i++) {
                                JRadioButtonMenuItem radio =
                                    new JRadioButtonMenuItem(actionsButtons[i].getTitle(), actionsButtons[i].getIcon(),
                                        actionsButtons[i].getCommand().equals(action));

                                radio.setActionCommand(actionsButtons[i].getCommand());
                                radio.setAccelerator(KeyStroke.getKeyStroke(actionsButtons[i].getKeyCode(),
                                    actionsButtons[i].getModifier()));
                                // Trigger the selected mouse action
                                radio.addActionListener(toolBar);
                                // Update the state of the button in the toolbar
                                radio.addActionListener(leftButtonAction);
                                popupMenu.add(radio);
                                groupButtons.add(radio);
                            }
                        }
                    }
                    popupMenu.add(new JSeparator());
                    ActionState viewingAction = eventManager.getAction(ActionW.VIEWINGPROTOCOL);
                    if (viewingAction instanceof ComboItemListener) {
                        popupMenu.add(((ComboItemListener) viewingAction).createUnregisteredRadioMenu(Messages
                            .getString("View2dContainer.view_protocols"))); //$NON-NLS-1$
                    }
                    ActionState presetAction = eventManager.getAction(ActionW.PRESET);
                    if (presetAction instanceof ComboItemListener) {
                        popupMenu.add(((ComboItemListener) presetAction).createUnregisteredRadioMenu(Messages
                            .getString("View2dContainer.presets"))); //$NON-NLS-1$
                    }
                    ActionState stackAction = eventManager.getAction(ActionW.SORTSTACK);
                    if (stackAction instanceof ComboItemListener) {
                        JMenu menu =
                            ((ComboItemListener) stackAction).createUnregisteredRadioMenu(Messages
                                .getString("View2dContainer.sort_stack")); //$NON-NLS-1$
                        ActionState invstackAction = eventManager.getAction(ActionW.INVERSESTACK);
                        if (invstackAction instanceof ToggleButtonListener) {
                            menu.add(new JSeparator());
                            menu.add(((ToggleButtonListener) invstackAction)
                                .createUnregiteredJCheckBoxMenuItem(Messages.getString("View2dContainer.inv_stack"))); //$NON-NLS-1$
                        }
                        popupMenu.add(menu);
                    }

                    ActionState rotateAction = eventManager.getAction(ActionW.ROTATION);
                    if (rotateAction instanceof SliderChangeListener) {
                        popupMenu.add(new JSeparator());
                        JMenu menu = new JMenu(Messages.getString("View2dContainer.orientation")); //$NON-NLS-1$
                        JMenuItem menuItem = new JMenuItem(Messages.getString("ResetTools.reset")); //$NON-NLS-1$
                        final SliderChangeListener rotation = (SliderChangeListener) rotateAction;
                        menuItem.addActionListener(new ActionListener() {

                            public void actionPerformed(ActionEvent e) {
                                rotation.setValue(0);
                            }
                        });
                        menu.add(menuItem);
                        menuItem = new JMenuItem(Messages.getString("View2dContainer.-90")); //$NON-NLS-1$
                        menuItem.addActionListener(new ActionListener() {

                            public void actionPerformed(ActionEvent e) {
                                rotation.setValue((rotation.getValue() - 90 + 360) % 360);
                            }
                        });
                        menu.add(menuItem);
                        menuItem = new JMenuItem(Messages.getString("View2dContainer.+90")); //$NON-NLS-1$
                        menuItem.addActionListener(new ActionListener() {

                            public void actionPerformed(ActionEvent e) {
                                rotation.setValue((rotation.getValue() + 90) % 360);
                            }
                        });
                        menu.add(menuItem);
                        menuItem = new JMenuItem(Messages.getString("View2dContainer.+180")); //$NON-NLS-1$
                        menuItem.addActionListener(new ActionListener() {

                            public void actionPerformed(ActionEvent e) {
                                rotation.setValue((rotation.getValue() + 180) % 360);
                            }
                        });
                        menu.add(menuItem);
                        ActionState flipAction = eventManager.getAction(ActionW.FLIP);
                        if (flipAction instanceof ToggleButtonListener) {
                            menu.add(new JSeparator());
                            menu.add(((ToggleButtonListener) flipAction).createUnregiteredJCheckBoxMenuItem(Messages
                                .getString("View2dContainer.flip_h"))); //$NON-NLS-1$
                            popupMenu.add(menu);
                        }
                    }

                    popupMenu.add(new JSeparator());
                    popupMenu.add(ResetTools.createUnregisteredJMenu());
                    JMenuItem close = new JMenuItem(Messages.getString("View2d.close")); //$NON-NLS-1$
                    close.addActionListener(new ActionListener() {

                        public void actionPerformed(ActionEvent e) {
                            event.getSelectedView2dContainer();
                            View2d.this.setSeries(null, -1);
                        }
                    });
                    popupMenu.add(close);
                    popupMenu.show(mouseevent.getComponent(), mouseevent.getX() - 5, mouseevent.getY() - 5);
                }
            }
        }
    }

}
