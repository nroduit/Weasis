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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DropButtonIcon;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.graphic.AngleToolGraphic;
import org.weasis.core.ui.graphic.CircleGraphic;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.LineGraphic;
import org.weasis.core.ui.graphic.RectangleGraphic;
import org.weasis.core.ui.graphic.SelectGraphic;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.util.WtoolBar;

public class MeasureToolBar<E extends ImageElement> extends WtoolBar implements ChangeListener {

    public static final SelectGraphic selectionGraphic = new SelectGraphic();
    public static final LineGraphic lineGraphic = new LineGraphic(2.0f, Color.YELLOW);
    public static final AngleToolGraphic angleToolGraphic = new AngleToolGraphic(2.0f, Color.YELLOW);
    public static final CircleGraphic circleGraphic = new CircleGraphic(1.0f, Color.YELLOW, false);
    public static final RectangleGraphic rectangleGraphic = new RectangleGraphic(1.0f, Color.YELLOW, false);
    public static final Icon MeasureIcon = new ImageIcon(MouseActions.class.getResource("/icon/32x32/measure.png")); //$NON-NLS-1$
    public static final ArrayList<Graphic> graphicList = new ArrayList<Graphic>();
    static {
        graphicList.add(selectionGraphic);
        graphicList.add(lineGraphic);
        graphicList.add(angleToolGraphic);
        graphicList.add(circleGraphic);
        graphicList.add(rectangleGraphic);
    }
    protected final JButton jButtondelete = new JButton();
    protected final Component measureButtonGap = Box.createRigidArea(new Dimension(10, 0));
    protected final DropDownButton measureButton;
    protected final ImageViewerEventManager<E> eventManager;

    public MeasureToolBar(final ImageViewerEventManager<E> eventManager) {
        super("measure2dBar", TYPE.tool); //$NON-NLS-1$
        if (eventManager == null) {
            throw new IllegalArgumentException("EventManager cannot be null"); //$NON-NLS-1$
        }
        this.eventManager = eventManager;
        measureButton = new DropDownButton(ActionW.DRAW_MEASURE.cmd(), buildIcon(selectionGraphic)) { //$NON-NLS-1$

                @Override
                protected JPopupMenu getPopupMenu() {
                    return getMeasureToolPopupMenuButton(this);
                }
            };
        measureButton.setToolTipText(Messages.getString("MeasureToolBar.tools")); //$NON-NLS-1$
        ActionState measure = eventManager.getAction(ActionW.DRAW_MEASURE);
        if (measure instanceof ComboItemListener) {
            // ((ComboItemListener) measure).registerComponent(measureButton);
            // measureButton.addChangeListener(this);
        }

        add(measureButton);

        // add(measureButtonGap);
        jButtondelete.setToolTipText(Messages.getString("MeasureToolBar.del")); //$NON-NLS-1$
        jButtondelete.setIcon(new ImageIcon(MouseActions.class.getResource("/icon/32x32/draw-delete.png"))); //$NON-NLS-1$
        jButtondelete.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(ActionEvent e) {
                AbstractLayerModel model = getCurrentLayerModel();
                if (model != null) {
                    if (model.getSelectedGraphics().size() == 0) {
                        model.setSelectedGraphics(model.getdAllGraphics());
                    }
                    model.deleteSelectedGraphics();
                }
            }
        });
        add(jButtondelete);
    }

    private JPopupMenu getMeasureToolPopupMenuButton(final DropDownButton dropDownButton) {
        ActionState measure = eventManager.getAction(ActionW.DRAW_MEASURE);
        JPopupMenu popupMouseButtons = new JPopupMenu();
        if (measure instanceof ComboItemListener) {
            // Create dropbutton with the model GroupRadioMenu
            // GroupRadioMenu menu = ((ComboItemListener) measure).createGroupRadioMenu();
            JMenu menu = ((ComboItemListener) measure).createMenu(ActionW.DRAW_MEASURE.cmd()); //$NON-NLS-1$
            popupMouseButtons.setInvoker(dropDownButton);
            Component[] cps = menu.getMenuComponents();
            for (int i = 0; i < cps.length; i++) {
                if (cps[i] instanceof JRadioButtonMenuItem) {
                    JRadioButtonMenuItem item = (JRadioButtonMenuItem) cps[i];
                    item.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent e) {
                            if (e.getSource() instanceof JRadioButtonMenuItem) {
                                JRadioButtonMenuItem item = (JRadioButtonMenuItem) e.getSource();
                                if (item.getParent() instanceof JPopupMenu) {
                                    JPopupMenu pop = (JPopupMenu) item.getParent();
                                    if (pop.getInvoker() instanceof DropDownButton) {
                                        changeButtonState(item.getActionCommand());
                                    }
                                }
                            }
                        }
                    });
                    popupMouseButtons.add(item);
                }
            }
        }
        return popupMouseButtons;
    }

    protected AbstractLayerModel getCurrentLayerModel() {
        DefaultView2d view = eventManager.getSelectedViewPane();
        if (view != null) {
            return view.getLayerModel();
        }
        return null;
    }

    public void changeButtonState(String action) {
        String cmd = measureButton.getActionCommand();
        if (!(action == null && cmd == null) && (action == null || !action.equals(measureButton.getActionCommand()))) {
            final Graphic graphic = getGraphic(action);
            Icon icon = buildIcon(graphic);
            measureButton.setIcon(icon);
            measureButton.setActionCommand(action);
        }
    }

    private Icon buildIcon(final Graphic graphic) {

        return new DropButtonIcon(new Icon() {

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                MeasureIcon.paintIcon(c, g, x, y);
                if (graphic != null) {
                    Icon smallIcon = graphic.getIcon();
                    if (smallIcon != null) {
                        x += MeasureIcon.getIconWidth() - smallIcon.getIconWidth() - 1;
                        y += MeasureIcon.getIconHeight() - smallIcon.getIconHeight() - 1;
                        smallIcon.paintIcon(c, g, x, y);
                    }
                }
            }

            @Override
            public int getIconWidth() {
                return MeasureIcon.getIconWidth();
            }

            @Override
            public int getIconHeight() {
                return MeasureIcon.getIconHeight();
            }
        });
    }

    public Graphic getGraphic(String action) {
        if (action != null) {
            for (Graphic g : graphicList) {
                if (action.equals(g.toString())) {
                    return g;
                }
            }
        }
        return null;
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (e.getSource() instanceof JRadioButtonMenuItem) {
            JRadioButtonMenuItem item = (JRadioButtonMenuItem) e.getSource();
            if (item.getParent() instanceof JPopupMenu) {
                JPopupMenu pop = (JPopupMenu) item.getParent();
                if (pop.getInvoker() instanceof DropDownButton) {
                    changeButtonState(item.getActionCommand());
                }
            }
        }
    }

}
