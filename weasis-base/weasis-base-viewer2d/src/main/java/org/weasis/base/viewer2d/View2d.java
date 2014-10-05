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
package org.weasis.base.viewer2d;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;

import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.image.FilterOp;
import org.weasis.core.api.image.FlipOp;
import org.weasis.core.api.image.PseudoColorOp;
import org.weasis.core.api.image.RotationOp;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.CalibrationView;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.PannerListener;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.graphic.AbstractDragGraphic;
import org.weasis.core.ui.graphic.BasicGraphic;
import org.weasis.core.ui.graphic.DragLayer;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.LineGraphic;
import org.weasis.core.ui.graphic.MeasureDialog;
import org.weasis.core.ui.graphic.TempLayer;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.MouseEventDouble;
import org.weasis.core.ui.util.TitleMenuItem;
import org.weasis.core.ui.util.UriListFlavor;

public class View2d extends DefaultView2d<ImageElement> {
    private final ContextMenuHandler contextMenuHandler = new ContextMenuHandler();
    private final Dimension oldSize;

    public View2d(ImageViewerEventManager<ImageElement> eventManager) {
        super(eventManager);
        SimpleOpManager manager = imageLayer.getDisplayOpManager();
        manager.addImageOperationAction(new WindowOp());
        manager.addImageOperationAction(new FilterOp());
        manager.addImageOperationAction(new PseudoColorOp());
        // Zoom and Rotation must be the last operations for the lens
        manager.addImageOperationAction(new ZoomOp());
        manager.addImageOperationAction(new RotationOp());
        manager.addImageOperationAction(new FlipOp());

        infoLayer = new InfoLayer(this);
        DragLayer layer = new DragLayer(getLayerModel(), AbstractLayer.MEASURE);
        getLayerModel().addLayer(layer);
        TempLayer layerTmp = new TempLayer(getLayerModel());
        getLayerModel().addLayer(layerTmp);

        oldSize = new Dimension(0, 0);
    }

    @Override
    public void registerDefaultListeners() {
        buildPanner();
        super.registerDefaultListeners();
        setTransferHandler(new SequenceHandler());

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
                if (panner != null) {
                    panner.updateImageSize();
                }
                if (lens != null) {
                    int w = getWidth();
                    int h = getHeight();
                    if (w != 0 && h != 0) {
                        Rectangle bound = lens.getBounds();
                        if (oldSize.width != 0 && oldSize.height != 0) {
                            int centerx = bound.width / 2;
                            int centery = bound.height / 2;
                            bound.x = (bound.x + centerx) * w / oldSize.width - centerx;
                            bound.y = (bound.y + centery) * h / oldSize.height - centery;
                            lens.setLocation(bound.x, bound.y);
                        }
                        oldSize.width = w;
                        oldSize.height = h;
                    }
                    lens.updateZoom();
                }
            }
        });
        // enableMouseAndKeyListener(EventManager.getInstance().getMouseActions());
    }

    @Override
    protected void initActionWState() {
        super.initActionWState();
        actionsInView.put(ActionW.ZOOM.cmd(), -1.0);
        actionsInView.put(DefaultView2d.zoomTypeCmd, ZoomType.PIXEL_SIZE);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        if (series == null) {
            return;
        }
        // if (evt.getPropertyName().equals(ActionW.INVERSESTACK.cmd())) {
        // actionsInView.put(ActionW.INVERSESTACK.cmd(), evt.getNewValue());
        // sortStack();
        // }
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

    private MouseActionAdapter getMouseAdapter(String action) {
        if (action.equals(ActionW.MEASURE.cmd())) {
            return mouseClickHandler;
        } else if (action.equals(ActionW.PAN.cmd())) {
            return getAction(ActionW.PAN);
        } else if (action.equals(ActionW.CONTEXTMENU.cmd())) {
            return contextMenuHandler;
        } else if (action.equals(ActionW.WINDOW.cmd())) {
            return getAction(ActionW.WINDOW);
        } else if (action.equals(ActionW.LEVEL.cmd())) {
            return getAction(ActionW.LEVEL);
        }
        // Tricky action, see in addMouseAdapter()
        else if (action.equals(ActionW.WINLEVEL.cmd())) {
            return getAction(ActionW.LEVEL);
        } else if (action.equals(ActionW.SCROLL_SERIES.cmd())) {
            return getAction(ActionW.SCROLL_SERIES);
        } else if (action.equals(ActionW.ZOOM.cmd())) {
            return getAction(ActionW.ZOOM);
        } else if (action.equals(ActionW.ROTATION.cmd())) {
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
    public void handleLayerChanged(ImageLayer layer) {
        repaint();
    }

    @Override
    public void focusGained(FocusEvent e) {
        if (!e.isTemporary()) {
            ImageViewerPlugin<ImageElement> pane = eventManager.getSelectedView2dContainer();
            if (pane != null && pane.isContainingView(this)) {
                pane.setSelectedImagePaneFromFocus(this);
            }
        }
    }

    protected JPopupMenu buidContexMenu(final MouseEvent evt) {
        final ArrayList<Graphic> selected = new ArrayList<Graphic>(View2d.this.getLayerModel().getSelectedGraphics());
        if (selected.size() > 0) {

            JPopupMenu popupMenu = new JPopupMenu();
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
                            final MouseEventDouble mouseEvt =
                                new MouseEventDouble(View2d.this, MouseEvent.MOUSE_RELEASED, evt.getWhen(), 16, 0, 0,
                                    0, 0, 1, true, 1);
                            mouseEvt.setSource(View2d.this);
                            mouseEvt.setImageCoordinates(getImageCoordinatesFromMouse(evt.getX(), evt.getY()));
                            final int ptIndex = absgraph.getHandlePointIndex(mouseEvt);
                            if (ptIndex >= 0) {
                                JMenuItem menuItem = new JMenuItem(Messages.getString("View2d.rem_point")); //$NON-NLS-1$
                                menuItem.addActionListener(new ActionListener() {

                                    @Override
                                    public void actionPerformed(ActionEvent e) {
                                        absgraph.removeHandlePoint(ptIndex, mouseEvt);
                                    }
                                });
                                popupMenu.add(menuItem);

                                menuItem = new JMenuItem(Messages.getString("View2d.add_point")); //$NON-NLS-1$
                                menuItem.addActionListener(new ActionListener() {

                                    @Override
                                    public void actionPerformed(ActionEvent e) {
                                        absgraph.forceToAddPoints(ptIndex);
                                        MouseEventDouble evt2 =
                                            new MouseEventDouble(View2d.this, MouseEvent.MOUSE_PRESSED, evt.getWhen(),
                                                16, evt.getX(), evt.getY(), evt.getXOnScreen(), evt.getYOnScreen(), 1,
                                                true, 1);
                                        mouseClickHandler.mousePressed(evt2);
                                    }
                                });
                                popupMenu.add(menuItem);
                                popupMenu.add(new JSeparator());
                            }
                        } else if (ds != null && absgraph.getHandlePointTotalNumber() == BasicGraphic.UNDEFINED) {
                            final JMenuItem item2 = new JMenuItem(Messages.getString("View2d.stop_draw")); //$NON-NLS-1$
                            item2.addActionListener(new ActionListener() {

                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    MouseEventDouble event =
                                        new MouseEventDouble(View2d.this, 0, 0, 16, 0, 0, 0, 0, 2, true, 1);
                                    ds.completeDrag(event);
                                    mouseClickHandler.mouseReleased(event);
                                }
                            });
                            popupMenu.add(item2);
                            popupMenu.add(new JSeparator());
                        }
                    }
                }
            }

            int count = popupMenu.getComponentCount();
            if (graphicComplete) {
                JMenuItem menuItem = new JMenuItem(Messages.getString("View2d.delete_selec")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        View2d.this.getLayerModel().deleteSelectedGraphics(true);
                    }
                });
                popupMenu.add(menuItem);

                menuItem = new JMenuItem(Messages.getString("View2d.cut")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        AbstractLayerModel.GraphicClipboard.setGraphics(selected);
                        View2d.this.getLayerModel().deleteSelectedGraphics(false);
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
            }

            if (count < popupMenu.getComponentCount()) {
                popupMenu.add(new JSeparator());
                count = popupMenu.getComponentCount();
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
                JMenuItem item = new JMenuItem(Messages.getString("View2d.front")); //$NON-NLS-1$
                item.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        graph.toFront();
                    }
                });
                popupMenu.add(item);
                item = new JMenuItem(Messages.getString("View2d.back")); //$NON-NLS-1$
                item.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        graph.toBack();
                    }
                });
                popupMenu.add(item);
                if (count < popupMenu.getComponentCount()) {
                    popupMenu.add(new JSeparator());
                    count = popupMenu.getComponentCount();
                }

                if (graphicComplete && graph instanceof LineGraphic) {

                    final JMenuItem calibMenu = new JMenuItem(Messages.getString("View2d.calib")); //$NON-NLS-1$
                    calibMenu.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent e) {
                            ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(View2d.this);
                            String title = Messages.getString("View2d.man_calib"); //$NON-NLS-1$
                            CalibrationView calibrationDialog = new CalibrationView((LineGraphic) graph, View2d.this);
                            int res =
                                JOptionPane.showConfirmDialog(ColorLayerUI.getContentPane(layer), calibrationDialog,
                                    title, JOptionPane.OK_CANCEL_OPTION);
                            if (layer != null) {
                                layer.hideUI();
                            }
                            if (res == JOptionPane.OK_OPTION) {
                                calibrationDialog.applyNewCalibration();
                            }
                        }
                    });
                    popupMenu.add(calibMenu);
                }
            }

            if (count < popupMenu.getComponentCount()) {
                popupMenu.add(new JSeparator());
                count = popupMenu.getComponentCount();
            }

            if (list.size() > 0) {
                JMenuItem properties = new JMenuItem(Messages.getString("View2d.prop")); //$NON-NLS-1$
                properties.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(View2d.this);
                        JDialog dialog = new MeasureDialog(View2d.this, list);
                        ColorLayerUI.showCenterScreen(dialog, layer);
                    }
                });
                popupMenu.add(properties);
            }
            return popupMenu;
        } else if (View2d.this.getSourceImage() != null) {
            JPopupMenu popupMenu = new JPopupMenu();
            TitleMenuItem itemTitle =
                new TitleMenuItem(Messages.getString("View2d.left_mouse") + StringUtil.COLON, popupMenu.getInsets()); //$NON-NLS-1$
            popupMenu.add(itemTitle);
            popupMenu.setLabel(MouseActions.LEFT);
            String action = eventManager.getMouseActions().getLeft();
            int count = popupMenu.getComponentCount();

            ButtonGroup groupButtons = new ButtonGroup();
            ImageViewerPlugin<ImageElement> view = eventManager.getSelectedView2dContainer();
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
                            Rectangle2D area = View2d.this.getViewModel().getModelArea();
                            for (Graphic g : graphs) {
                                if (!g.getBounds(null).intersects(area)) {
                                    int option =
                                        JOptionPane.showConfirmDialog(View2d.this,
                                            "At least one graphic is outside the image.\n Do you want to continue?"); //$NON-NLS-1$
                                    if (option == JOptionPane.YES_OPTION) {
                                        break;
                                    } else {
                                        return;
                                    }
                                }
                            }
                            for (Graphic g : graphs) {
                                AbstractLayer layer = View2d.this.getLayerModel().getLayer(g.getLayerID());
                                if (layer == null) {
                                    layer = View2d.this.getLayerModel().getLayer(AbstractLayer.MEASURE);
                                }
                                if (layer != null) {
                                    Graphic graph = g.deepCopy();
                                    if (graph != null) {
                                        graph.updateLabel(true, View2d.this);
                                        layer.addGraphic(graph);
                                    }
                                }
                            }
                            // Repaint all because labels are not drawn
                            View2d.this.getLayerModel().repaint();
                        }
                    }
                });
                popupMenu.add(menuItem);
            }

            if (count < popupMenu.getComponentCount()) {
                popupMenu.add(new JSeparator());
                count = popupMenu.getComponentCount();
            }

            if (eventManager instanceof EventManager) {
                EventManager manager = (EventManager) eventManager;
                JMVUtils.addItemToMenu(popupMenu, manager.getLutMenu("weasis.contextmenu.lut"));
                JMVUtils.addItemToMenu(popupMenu, manager.getLutInverseMenu("weasis.contextmenu.invertLut"));
                JMVUtils.addItemToMenu(popupMenu, manager.getFilterMenu("weasis.contextmenu.filter"));

                if (count < popupMenu.getComponentCount()) {
                    popupMenu.add(new JSeparator());
                    count = popupMenu.getComponentCount();
                }

                JMVUtils.addItemToMenu(popupMenu, manager.getZoomMenu("weasis.contextmenu.zoom"));
                JMVUtils.addItemToMenu(popupMenu, manager.getOrientationMenu("weasis.contextmenu.orientation"));
                // JMVUtils.addItemToMenu(popupMenu, manager.getSortStackMenu("weasis.contextmenu.sortstack"));

                if (count < popupMenu.getComponentCount()) {
                    popupMenu.add(new JSeparator());
                    count = popupMenu.getComponentCount();
                }

                JMVUtils.addItemToMenu(popupMenu, manager.getResetMenu("weasis.contextmenu.reset"));
            }

            if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.contextmenu.close", true)) { //$NON-NLS-1$
                JMenuItem close = new JMenuItem(Messages.getString("View2d.close")); //$NON-NLS-1$
                close.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        View2d.this.setSeries(null, null);
                    }
                });
                popupMenu.add(close);
            }

            return popupMenu;
        }
        return null;
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
                JPopupMenu popupMenu = View2d.this.buidContexMenu(evt);
                if (popupMenu != null) {
                    popupMenu.show(evt.getComponent(), evt.getX(), evt.getY());
                }
            }
        }
    }

    private class SequenceHandler extends TransferHandler {

        public SequenceHandler() {
            super("series"); //$NON-NLS-1$
        }

        @Override
        public Transferable createTransferable(JComponent comp) {
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

            Transferable transferable = support.getTransferable();

            List<File> files = null;
            // Not supported on Linux
            if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                try {
                    files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return dropDicomFiles(files);
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
                return dropDicomFiles(files);
            }

            ImageViewerPlugin<ImageElement> selPlugin = eventManager.getSelectedView2dContainer();
            Series seq;
            try {
                seq = (Series) transferable.getTransferData(Series.sequenceDataFlavor);
                // Do not add series without medias. BUG WEA-100
                if (seq == null || seq.size(null) == 0) {
                    return false;
                }
                DataExplorerModel model = (DataExplorerModel) seq.getTagValue(TagW.ExplorerModel);
                if (seq.getMedia(0, null, null) instanceof ImageElement && model instanceof TreeModel) {
                    TreeModel treeModel = (TreeModel) model;

                    MediaSeriesGroup p1 = treeModel.getParent(seq, model.getTreeModelNodeForNewPlugin());
                    MediaSeriesGroup p2 = null;
                    ViewerPlugin openPlugin = null;
                    if (p1 != null) {
                        if (selPlugin instanceof View2dContainer
                            && ((View2dContainer) selPlugin).isContainingView(View2d.this)
                            && p1.equals(selPlugin.getGroupID())) {
                            p2 = p1;
                        } else {
                            synchronized (UIManager.VIEWER_PLUGINS) {
                                plugin: for (final ViewerPlugin<?> p : UIManager.VIEWER_PLUGINS) {
                                    if (p1.equals(p.getGroupID())) {
                                        if (!((View2dContainer) p).isContainingView(View2d.this)) {
                                            openPlugin = p;
                                        }
                                        break plugin;
                                    }
                                }
                            }
                        }
                    }
                    if (openPlugin != null) {
                        openPlugin.setSelectedAndGetFocus();
                        openPlugin.addSeries(seq);
                        // openPlugin.setSelected(true);
                        return false;
                    }
                } else {
                    ViewerPluginBuilder.openSequenceInDefaultPlugin(seq, model == null
                        ? ViewerPluginBuilder.DefaultDataModel : model, true, true);
                    return true;
                }
            } catch (Exception e) {
                return false;
            }

            if (selPlugin != null && SynchData.Mode.Tile.equals(selPlugin.getSynchView().getSynchData().getMode())) {
                selPlugin.addSeries(seq);
                return true;
            }

            setSeries(seq);
            // Getting the focus has a delay and so it will trigger the view selection later
            // requestFocusInWindow();
            if (selPlugin != null && selPlugin.isContainingView(View2d.this)) {
                selPlugin.setSelectedImagePaneFromFocus(View2d.this);
            }
            return true;
        }

        private boolean dropDicomFiles(List<File> files) {
            if (files != null) {
                for (File file : files) {
                    ViewerPluginBuilder.openSequenceInDefaultPlugin(file, true, true);
                }
                return true;
            }
            return false;
        }
    }
}
