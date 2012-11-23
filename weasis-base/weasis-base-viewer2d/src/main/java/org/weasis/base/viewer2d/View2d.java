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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.FilterOperation;
import org.weasis.core.api.image.FlipOperation;
import org.weasis.core.api.image.OperationsManager;
import org.weasis.core.api.image.PseudoColorOperation;
import org.weasis.core.api.image.RotationOperation;
import org.weasis.core.api.image.WindowLevelOperation;
import org.weasis.core.api.image.ZoomOperation;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.image.CalibrationView;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.PannerListener;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.graphic.AbstractDragGraphic;
import org.weasis.core.ui.graphic.DragLayer;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.LineGraphic;
import org.weasis.core.ui.graphic.MeasureDialog;
import org.weasis.core.ui.graphic.TempLayer;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.graphic.model.Tools;
import org.weasis.core.ui.util.MouseEventDouble;
import org.weasis.core.ui.util.TitleMenuItem;

public class View2d extends DefaultView2d<ImageElement> {
    private final ContextMenuHandler contextMenuHandler = new ContextMenuHandler();

    public View2d(ImageViewerEventManager<ImageElement> eventManager) {
        super(eventManager);
        OperationsManager manager = imageLayer.getOperationsManager();
        manager.addImageOperationAction(new WindowLevelOperation());
        manager.addImageOperationAction(new FilterOperation());
        manager.addImageOperationAction(new PseudoColorOperation());
        // Zoom and Rotation must be the last operations for the lens
        manager.addImageOperationAction(new ZoomOperation());
        manager.addImageOperationAction(new RotationOperation());
        manager.addImageOperationAction(new FlipOperation());

        infoLayer = new InfoLayer(this);
        DragLayer layer = new DragLayer(getLayerModel(), Tools.MEASURE.getId());
        getLayerModel().addLayer(layer);
        TempLayer layerTmp = new TempLayer(getLayerModel());
        getLayerModel().addLayer(layerTmp);

    }

    @Override
    public void registerDefaultListeners() {
        super.registerDefaultListeners();
        // setTransferHandler(new SequenceHandler());

        addComponentListener(new ComponentAdapter() {

            @Override
            public void componentResized(ComponentEvent e) {
                // TODO add preference keep image in best fit when resize
                Double currentZoom = (Double) actionsInView.get(ActionW.ZOOM.cmd());
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
    public void setSeries(MediaSeries<ImageElement> series, ImageElement selectedImage) {
        MediaSeries<ImageElement> oldsequence = this.series;
        this.series = series;
        if (oldsequence != null && oldsequence != series) {
            closingSeries(oldsequence);
            // All the action values are initialized again with the series changing
            initActionWState();
        }
        if (series == null) {
            imageLayer.setImage(null, null);
            getLayerModel().deleteAllGraphics();
        } else {
            ImageElement media = selectedImage;
            if (selectedImage == null) {
                media =
                    series.getMedia(tileOffset < 0 ? 0 : tileOffset,
                        (Filter<ImageElement>) actionsInView.get(ActionW.FILTERED_SERIES.cmd()),
                        getCurrentSortComparator());
            }
            setDefautWindowLevel(media);
            setImage(media, true);
            Double val = (Double) actionsInView.get(ActionW.ZOOM.cmd());
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
    protected void setDefautWindowLevel(ImageElement img) {
        super.setDefautWindowLevel(img);
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
                        } else if (ds != null && absgraph.getHandlePointTotalNumber() == AbstractDragGraphic.UNDEFINED) {
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
                popupMenu.add(new JSeparator());

                if (graphicComplete && graph instanceof LineGraphic) {

                    final JMenuItem calibMenu = new JMenuItem(Messages.getString("View2d.calib")); //$NON-NLS-1$
                    calibMenu.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent e) {
                            String title = Messages.getString("View2d.man_calib"); //$NON-NLS-1$
                            CalibrationView calibrationDialog = new CalibrationView((LineGraphic) graph, View2d.this);
                            int res =
                                JOptionPane.showConfirmDialog(calibMenu, calibrationDialog, title,
                                    JOptionPane.OK_CANCEL_OPTION);
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
                JMenuItem properties = new JMenuItem(Messages.getString("View2d.prop")); //$NON-NLS-1$
                properties.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        JDialog dialog = new MeasureDialog(WinUtil.getParentWindow(View2d.this), list);
                        WinUtil.adjustLocationToFitScreen(dialog, evt.getLocationOnScreen());
                        dialog.setVisible(true);
                    }
                });
                popupMenu.add(properties);
            }
            return popupMenu;
        } else if (View2d.this.getSourceImage() != null) {
            JPopupMenu popupMenu = new JPopupMenu();
            TitleMenuItem itemTitle = new TitleMenuItem(Messages.getString("View2d.left_mouse"), popupMenu.getInsets()); //$NON-NLS-1$
            popupMenu.add(itemTitle);
            popupMenu.setLabel(MouseActions.LEFT);
            String action = eventManager.getMouseActions().getLeft();
            ActionW[] actionsButtons = ViewerToolBar.actionsButtons;
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
                    for (int i = 0; i < ViewerToolBar.actionsButtons.length; i++) {
                        JRadioButtonMenuItem radio =
                            new JRadioButtonMenuItem(actionsButtons[i].getTitle(), actionsButtons[i].getIcon(),
                                actionsButtons[i].cmd().equals(action));

                        radio.setActionCommand(actionsButtons[i].cmd());
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
            if (AbstractLayerModel.GraphicClipboard.getGraphics() != null) {
                popupMenu.add(new JSeparator());
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
            ActionState rotateAction = eventManager.getAction(ActionW.ROTATION);
            if (rotateAction instanceof SliderChangeListener) {
                popupMenu.add(new JSeparator());
                JMenu menu = new JMenu(Messages.getString("View2dContainer.orientation")); //$NON-NLS-1$
                JMenuItem menuItem = new JMenuItem(Messages.getString("View2dContainer.reset")); //$NON-NLS-1$
                final SliderChangeListener rotation = (SliderChangeListener) rotateAction;
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue(0);
                    }
                });
                menu.add(menuItem);
                menuItem = new JMenuItem(Messages.getString("View2dContainer.-90")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue((rotation.getValue() - 90 + 360) % 360);
                    }
                });
                menu.add(menuItem);
                menuItem = new JMenuItem(Messages.getString("View2dContainer.+90")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue((rotation.getValue() + 90) % 360);
                    }
                });
                menu.add(menuItem);
                menuItem = new JMenuItem(Messages.getString("View2dContainer.180")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue((rotation.getValue() + 180) % 360);
                    }
                });
                menu.add(menuItem);
                ActionState flipAction = eventManager.getAction(ActionW.FLIP);
                if (flipAction instanceof ToggleButtonListener) {
                    menu.add(new JSeparator());
                    menu.add(((ToggleButtonListener) flipAction).createUnregiteredJCheckBoxMenuItem(Messages
                        .getString("View2d.flip"))); //$NON-NLS-1$
                    popupMenu.add(menu);
                }
            }

            popupMenu.add(new JSeparator());
            popupMenu.add(ResetTools.createUnregisteredJMenu());
            JMenuItem close = new JMenuItem(Messages.getString("View2d.close")); //$NON-NLS-1$
            close.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    View2d.this.setSeries(null, null);
                }
            });
            popupMenu.add(close);
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

}
