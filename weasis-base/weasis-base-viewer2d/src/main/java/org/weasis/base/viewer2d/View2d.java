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

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.util.Collections;
import java.util.Comparator;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;

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
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.PannerListener;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.graphic.DragLayer;
import org.weasis.core.ui.graphic.TempLayer;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.graphic.model.Tools;

public class View2d extends DefaultView2d<ImageElement> {
    private final ContextMenuHandler contextMenuHandler = new ContextMenuHandler();

    public View2d(ImageViewerEventManager<ImageElement> eventManager) {
        super(eventManager);
        OperationsManager manager = imageLayer.getOperationsManager();
        manager.addImageOperationAction(new WindowLevelOperation());
        manager.addImageOperationAction(new FlipOperation());
        manager.addImageOperationAction(new FilterOperation());
        manager.addImageOperationAction(new PseudoColorOperation());
        // Zoom and Rotation must be the last operations for the lens
        manager.addImageOperationAction(new ZoomOperation());
        manager.addImageOperationAction(new RotationOperation());

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
        if (evt.getPropertyName().equals(ActionW.INVERSESTACK.cmd())) {
            actionsInView.put(ActionW.INVERSESTACK.cmd(), evt.getNewValue());
            sortStack();
        }
    }

    @Override
    public void setSeries(MediaSeries<ImageElement> series, int defaultIndex) {
        MediaSeries<ImageElement> oldsequence = this.series;
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
            setImage(series.getMedia(frameIndex), true);
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
    protected void setWindowLevel(ImageElement img) {
        float min = img.getMinValue();
        float max = img.getMaxValue();
        actionsInView.put(ActionW.WINDOW.cmd(), max - min);
        actionsInView.put(ActionW.LEVEL.cmd(), (max - min) / 2.0f + min);
    }

    protected void sortStack() {
        Comparator<ImageElement> sortComparator = (Comparator<ImageElement>) actionsInView.get(ActionW.SORTSTACK.cmd());
        if (sortComparator != null) {
            series.sort((Boolean) actionsInView.get(ActionW.INVERSESTACK.cmd()) ? Collections
                .reverseOrder(sortComparator) : sortComparator);
            Double val = (Double) actionsInView.get(ActionW.ZOOM.cmd());
            // If zoom has not been defined or was besfit, set image in bestfit zoom mode
            boolean rescaleView = (val == null || val <= 0.0);
            setImage(series.getMedia(frameIndex), rescaleView);
            if (rescaleView) {
                val = (Double) actionsInView.get(ActionW.ZOOM.cmd());
                zoom(val == null ? 1.0 : val);
                center();
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

    class ContextMenuHandler extends MouseActionAdapter {

        @Override
        public void mousePressed(final MouseEvent mouseevent) {
            int buttonMask = getButtonMaskEx();
            if ((mouseevent.getModifiersEx() & buttonMask) != 0) {
                if (View2d.this.getSourceImage() != null) {
                    JPopupMenu popupMenu = new JPopupMenu();
                    JMenuItem item = new JMenuItem("Left mouse actions:");
                    Font font = item.getFont();
                    item.setFont(new Font(font.getFamily(), Font.BOLD, font.getSize()));
                    item.setFocusable(false);
                    popupMenu.add(item);
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
                    popupMenu.add(new JSeparator());
                    ActionState viewingAction = eventManager.getAction(ActionW.VIEWINGPROTOCOL);
                    if (viewingAction instanceof ComboItemListener) {
                        popupMenu.add(((ComboItemListener) viewingAction)
                            .createUnregisteredRadioMenu("Viewing Protocols"));
                    }
                    ActionState presetAction = eventManager.getAction(ActionW.PRESET);
                    if (presetAction instanceof ComboItemListener) {
                        popupMenu.add(((ComboItemListener) presetAction).createUnregisteredRadioMenu("Presets"));
                    }
                    ActionState stackAction = EventManager.getInstance().getAction(ActionW.SORTSTACK);
                    if (stackAction instanceof ComboItemListener) {
                        JMenu menu = ((ComboItemListener) stackAction).createUnregisteredRadioMenu("Sort Stack by");
                        ActionState invstackAction = eventManager.getAction(ActionW.INVERSESTACK);
                        if (invstackAction instanceof ToggleButtonListener) {
                            menu.add(new JSeparator());
                            menu.add(((ToggleButtonListener) invstackAction)
                                .createUnregiteredJCheckBoxMenuItem("Inverse Stack"));
                        }
                        popupMenu.add(menu);
                    }

                    ActionState rotateAction = eventManager.getAction(ActionW.ROTATION);
                    if (rotateAction instanceof SliderChangeListener) {
                        popupMenu.add(new JSeparator());
                        JMenu menu = new JMenu("Orientation");
                        JMenuItem menuItem = new JMenuItem("Reset");
                        final SliderChangeListener rotation = (SliderChangeListener) rotateAction;
                        menuItem.addActionListener(new ActionListener() {

                            @Override
                            public void actionPerformed(ActionEvent e) {
                                rotation.setValue(0);
                            }
                        });
                        menu.add(menuItem);
                        menuItem = new JMenuItem("- 90");
                        menuItem.addActionListener(new ActionListener() {

                            @Override
                            public void actionPerformed(ActionEvent e) {
                                rotation.setValue((rotation.getValue() - 90 + 360) % 360);
                            }
                        });
                        menu.add(menuItem);
                        menuItem = new JMenuItem("+ 90");
                        menuItem.addActionListener(new ActionListener() {

                            @Override
                            public void actionPerformed(ActionEvent e) {
                                rotation.setValue((rotation.getValue() + 90) % 360);
                            }
                        });
                        menu.add(menuItem);
                        menuItem = new JMenuItem("+ 180");
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
                            menu.add(((ToggleButtonListener) flipAction)
                                .createUnregiteredJCheckBoxMenuItem("Flip Horizontally"));
                            popupMenu.add(menu);
                        }
                    }

                    popupMenu.add(new JSeparator());
                    popupMenu.add(ResetTools.createUnregisteredJMenu());
                    JMenuItem close = new JMenuItem("Close");
                    close.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent e) {
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
