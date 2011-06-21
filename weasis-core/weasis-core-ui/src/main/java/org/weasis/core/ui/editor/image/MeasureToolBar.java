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
import java.util.ArrayList;

import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPopupMenu;
import javax.swing.event.ListDataEvent;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DropButtonIcon;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.graphic.AbstractDragGraphic;
import org.weasis.core.ui.graphic.AngleToolGraphic;
import org.weasis.core.ui.graphic.CobbAngleToolGraphic;
import org.weasis.core.ui.graphic.EllipseGraphic;
import org.weasis.core.ui.graphic.FourPointsAngleToolGraphic;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.LineGraphic;
import org.weasis.core.ui.graphic.OpenAngleToolGraphic;
import org.weasis.core.ui.graphic.ParallelLineGraphic;
import org.weasis.core.ui.graphic.PerpendicularLineGraphic;
import org.weasis.core.ui.graphic.PolygonGraphic;
import org.weasis.core.ui.graphic.RectangleGraphic;
import org.weasis.core.ui.graphic.SelectGraphic;
import org.weasis.core.ui.graphic.ThreePointsCircleGraphic;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.util.ViewSetting;
import org.weasis.core.ui.util.WtoolBar;

public class MeasureToolBar<E extends ImageElement> extends WtoolBar {

    public static final SelectGraphic selectionGraphic = new SelectGraphic(1.0f, Color.WHITE);
    public static final LineGraphic lineGraphic = new LineGraphic(1.0f, Color.YELLOW, true);
    public static final AngleToolGraphic angleToolGraphic = new AngleToolGraphic(1.0f, Color.YELLOW, true);

    public static final RectangleGraphic rectangleGraphic = new RectangleGraphic(1.0f, Color.YELLOW, true);
    public static final EllipseGraphic ellipseGraphic = new EllipseGraphic(1.0f, Color.YELLOW, true);
    public static final ThreePointsCircleGraphic threePtCircleGraphic = new ThreePointsCircleGraphic(1.0f,
        Color.YELLOW, true);
    public static final PolygonGraphic polygonGraphic = new PolygonGraphic(1.0f, Color.YELLOW, true);
    public static final PerpendicularLineGraphic perpendicularToolGraphic = new PerpendicularLineGraphic(1.0f,
        Color.YELLOW, true);
    public static final ParallelLineGraphic parallelLineGraphic = new ParallelLineGraphic(1.0f, Color.YELLOW, true);
    public static final OpenAngleToolGraphic openAngleToolGraphic = new OpenAngleToolGraphic(1.0f, Color.YELLOW, true);
    public static final FourPointsAngleToolGraphic fourPointsAngleToolGraphic = new FourPointsAngleToolGraphic(1.0f,
        Color.YELLOW, true);
    public static final CobbAngleToolGraphic cobbAngleToolGraphic = new CobbAngleToolGraphic(1.0f, Color.YELLOW, true);

    public static final Icon MeasureIcon = new ImageIcon(MouseActions.class.getResource("/icon/32x32/measure.png")); //$NON-NLS-1$
    public static final ArrayList<Graphic> graphicList = new ArrayList<Graphic>();
    static {
        graphicList.add(selectionGraphic);
        graphicList.add(lineGraphic);
        graphicList.add(rectangleGraphic);
        graphicList.add(ellipseGraphic);
        graphicList.add(threePtCircleGraphic);
        graphicList.add(polygonGraphic);
        graphicList.add(perpendicularToolGraphic);
        graphicList.add(parallelLineGraphic);
        graphicList.add(angleToolGraphic);
        graphicList.add(openAngleToolGraphic);
        graphicList.add(fourPointsAngleToolGraphic);
        graphicList.add(cobbAngleToolGraphic);
    }
    protected final JButton jButtondelete = new JButton();
    protected final Component measureButtonGap = Box.createRigidArea(new Dimension(10, 0));
    protected final MeasureButton measureButton;
    protected final ImageViewerEventManager<E> eventManager;

    public MeasureToolBar(final ImageViewerEventManager<E> eventManager) {
        super("measure2dBar", TYPE.tool); //$NON-NLS-1$
        if (eventManager == null)
            throw new IllegalArgumentException("EventManager cannot be null"); //$NON-NLS-1$
        this.eventManager = eventManager;

        ViewSetting setting = eventManager.getViewSetting();
        for (int i = 1; i < graphicList.size(); i++) {
            applyDefaultSetting(setting, graphicList.get(i));
        }

        GroupRadioMenu menu = null;
        ActionState measure = eventManager.getAction(ActionW.DRAW_MEASURE);
        if (measure instanceof ComboItemListener) {
            ComboItemListener m = (ComboItemListener) measure;
            menu = new MeasureGroupMenu();
            m.registerComponent(menu);
        }
        measureButton = new MeasureButton(ActionW.DRAW_MEASURE.cmd(), buildIcon(selectionGraphic), menu);
        measureButton.setToolTipText(Messages.getString("MeasureToolBar.tools")); //$NON-NLS-1$

        add(measureButton);

        // add(measureButtonGap);
        jButtondelete.setToolTipText(Messages.getString("MeasureToolBar.del")); //$NON-NLS-1$
        jButtondelete.setIcon(new ImageIcon(MouseActions.class.getResource("/icon/32x32/draw-delete.png"))); //$NON-NLS-1$
        jButtondelete.addActionListener(new java.awt.event.ActionListener() {

            @Override
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

    public static void applyDefaultSetting(ViewSetting setting, Graphic graphic) {
        if (graphic instanceof AbstractDragGraphic) {
            AbstractDragGraphic g = (AbstractDragGraphic) graphic;
            g.setLineThickness(setting.getLineWidth());
            g.setPaint(setting.getLineColor());
        }
    }

    protected AbstractLayerModel getCurrentLayerModel() {
        DefaultView2d view = eventManager.getSelectedViewPane();
        if (view != null)
            return view.getLayerModel();
        return null;
    }

    public static Icon buildIcon(final Graphic graphic) {

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

    public static Graphic getGraphic(String action) {
        if (action != null) {
            for (Graphic g : graphicList) {
                if (action.equals(g.toString()))
                    return g;
            }
        }
        return null;
    }

    static class MeasureButton extends DropDownButton {
        private final GroupRadioMenu model;

        public MeasureButton(String type, Icon icon, GroupRadioMenu model) {
            super(type, icon);
            this.model = model;
        }

        @Override
        protected JPopupMenu getPopupMenu() {
            JPopupMenu menu = (model == null) ? new JPopupMenu() : model.createJPopupMenu();
            menu.setInvoker(this);
            return menu;
        }

    }

    class MeasureGroupMenu extends GroupRadioMenu {

        public MeasureGroupMenu() {
            super();
        }

        @Override
        public void contentsChanged(ListDataEvent e) {
            super.contentsChanged(e);
            changeButtonState();
        }

        public void changeButtonState() {
            Object sel = dataModel.getSelectedItem();
            if (sel instanceof Graphic && measureButton != null) {
                Icon icon = buildIcon((Graphic) sel);
                measureButton.setIcon(icon);
                measureButton.setActionCommand(sel.toString());
            }
        }
    }
}
