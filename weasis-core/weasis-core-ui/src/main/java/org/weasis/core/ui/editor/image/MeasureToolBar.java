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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.event.ListDataEvent;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DropButtonIcon;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.AnnotationGraphic;
import org.weasis.core.ui.model.graphic.imp.PixelInfoGraphic;
import org.weasis.core.ui.model.graphic.imp.angle.AngleToolGraphic;
import org.weasis.core.ui.model.graphic.imp.angle.CobbAngleToolGraphic;
import org.weasis.core.ui.model.graphic.imp.angle.FourPointsAngleToolGraphic;
import org.weasis.core.ui.model.graphic.imp.angle.OpenAngleToolGraphic;
import org.weasis.core.ui.model.graphic.imp.area.EllipseGraphic;
import org.weasis.core.ui.model.graphic.imp.area.PolygonGraphic;
import org.weasis.core.ui.model.graphic.imp.area.RectangleGraphic;
import org.weasis.core.ui.model.graphic.imp.area.SelectGraphic;
import org.weasis.core.ui.model.graphic.imp.area.ThreePointsCircleGraphic;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.graphic.imp.line.ParallelLineGraphic;
import org.weasis.core.ui.model.graphic.imp.line.PerpendicularLineGraphic;
import org.weasis.core.ui.model.graphic.imp.line.PolylineGraphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.pref.ViewSetting;
import org.weasis.core.ui.util.WtoolBar;

public class MeasureToolBar<E extends ImageElement> extends WtoolBar {
    private static final long serialVersionUID = -6672565963157176685L;

    public static final SelectGraphic selectionGraphic = new SelectGraphic();

    public static final Icon MeasureIcon = new ImageIcon(MouseActions.class.getResource("/icon/32x32/measure.png")); //$NON-NLS-1$
    public static final List<Graphic> measureGraphicList = new ArrayList<>();
    public static final List<Graphic> drawGraphicList = new ArrayList<>();

    static {
        selectionGraphic.setLayerType(LayerType.TEMP_DRAW);
        WProperties p = BundleTools.SYSTEM_PREFERENCES;
        if (p.getBooleanProperty("weasis.measure.selection", true)) { //$NON-NLS-1$
            measureGraphicList.add(selectionGraphic);
        }
        if (p.getBooleanProperty("weasis.measure.line", true)) { //$NON-NLS-1$
            measureGraphicList.add(new LineGraphic());
        }
        if (p.getBooleanProperty("weasis.measure.polyline", true)) { //$NON-NLS-1$
            measureGraphicList.add(new PolylineGraphic());
        }
        if (p.getBooleanProperty("weasis.measure.rectangle", true)) { //$NON-NLS-1$
            measureGraphicList.add(new RectangleGraphic());
        }
        if (p.getBooleanProperty("weasis.measure.ellipse", true)) { //$NON-NLS-1$
            measureGraphicList.add(new EllipseGraphic());
        }
        if (p.getBooleanProperty("weasis.measure.threeptcircle", true)) { //$NON-NLS-1$
            measureGraphicList.add(new ThreePointsCircleGraphic());
        }
        if (p.getBooleanProperty("weasis.measure.polygon", true)) { //$NON-NLS-1$
            measureGraphicList.add(new PolygonGraphic());
        }
        if (p.getBooleanProperty("weasis.measure.perpendicular", true)) { //$NON-NLS-1$
            measureGraphicList.add(new PerpendicularLineGraphic());
        }
        if (p.getBooleanProperty("weasis.measure.parallele", true)) { //$NON-NLS-1$
            measureGraphicList.add(new ParallelLineGraphic());
        }
        if (p.getBooleanProperty("weasis.measure.angle", true)) { //$NON-NLS-1$
            measureGraphicList.add(new AngleToolGraphic());
        }
        if (p.getBooleanProperty("weasis.measure.openangle", true)) { //$NON-NLS-1$
            measureGraphicList.add(new OpenAngleToolGraphic());
        }
        if (p.getBooleanProperty("weasis.measure.fourptangle", true)) { //$NON-NLS-1$
            measureGraphicList.add(new FourPointsAngleToolGraphic());
        }
        if (p.getBooleanProperty("weasis.measure.cobbangle", true)) { //$NON-NLS-1$
            measureGraphicList.add(new CobbAngleToolGraphic());
        }
        if (p.getBooleanProperty("weasis.measure.pixelinfo", true)) { //$NON-NLS-1$
            measureGraphicList.add(new PixelInfoGraphic());
        }
        measureGraphicList.stream().filter(g -> Objects.isNull(g.getLayerType())).forEach(g -> g.setLayerType(LayerType.MEASURE));
        
        
        if (p.getBooleanProperty("weasis.draw.selection", true)) { //$NON-NLS-1$
            drawGraphicList.add(selectionGraphic);
        }
        if (p.getBooleanProperty("weasis.draw.line", true)) { //$NON-NLS-1$
            drawGraphicList.add(new LineGraphic());
        }
        if (p.getBooleanProperty("weasis.draw.polyline", true)) { //$NON-NLS-1$
            drawGraphicList.add(new PolylineGraphic());
        }
        if (p.getBooleanProperty("weasis.draw.rectangle", true)) { //$NON-NLS-1$
            drawGraphicList.add(new RectangleGraphic());
        }
        if (p.getBooleanProperty("weasis.draw.ellipse", true)) { //$NON-NLS-1$
            drawGraphicList.add(new EllipseGraphic());
        }
        if (p.getBooleanProperty("weasis.draw.threeptcircle", true)) { //$NON-NLS-1$
            drawGraphicList.add(new ThreePointsCircleGraphic());
        }
        if (p.getBooleanProperty("weasis.draw.polygon", true)) { //$NON-NLS-1$
            drawGraphicList.add(new PolygonGraphic());
        }
        if (p.getBooleanProperty("weasis.draw.textGrahic", true)) { //$NON-NLS-1$
            Graphic graphic = new AnnotationGraphic();
            graphic.setLayerType(LayerType.ANNOTATION);
            drawGraphicList.add(graphic);
        }
        drawGraphicList.forEach(g -> g.setLabelVisible(false));
        drawGraphicList.stream().filter(g -> Objects.isNull(g.getLayerType())).forEach(g -> g.setLayerType(LayerType.DRAW));
    }

    protected final JButton jButtondelete = new JButton();
    protected final JButton jButtonText = new JButton();
    protected final Component measureButtonGap = Box.createRigidArea(new Dimension(10, 0));
    protected final DropDownButton measureButton;
    protected final ImageViewerEventManager<E> eventManager;

    public MeasureToolBar(final ImageViewerEventManager<E> eventManager, int index) {
        super(Messages.getString("MeasureToolBar.title"), index); //$NON-NLS-1$
        if (eventManager == null) {
            throw new IllegalArgumentException("EventManager cannot be null"); //$NON-NLS-1$
        }
        this.eventManager = eventManager;

        // Do not apply to textGrahic
        for (int i = 1; i < measureGraphicList.size() - 1; i++) {
            applyDefaultSetting(MeasureTool.viewSetting, measureGraphicList.get(i));
        }

        GroupRadioMenu menu = null;
        ActionState measure = eventManager.getAction(ActionW.DRAW_MEASURE);
        if (measure instanceof ComboItemListener) {
            ComboItemListener m = (ComboItemListener) measure;
            menu = new MeasureGroupMenu();
            m.registerActionState(menu);

            for (Component mitem : menu.getRadioMenuItemListCopy()) {
                RadioMenuItem ritem = (RadioMenuItem) mitem;
                if (ritem.getUserObject() instanceof Graphic) {
                    Graphic g = (Graphic) ritem.getUserObject();
                    if (g.getKeyCode() != 0) {
                        ritem.setAccelerator(KeyStroke.getKeyStroke(g.getKeyCode(), g.getModifier()));
                    }
                }
            }
        }
        measureButton = new DropDownButton(ActionW.DRAW_MEASURE.cmd(), buildIcon(selectionGraphic), menu) {
            @Override
            protected JPopupMenu getPopupMenu() {
                JPopupMenu m = (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
                m.setInvoker(this);
                return m;
            }
        };
        if (measure != null) {
            measure.registerActionState(measureButton);
        }
        measureButton.setToolTipText(Messages.getString("MeasureToolBar.tools")); //$NON-NLS-1$

        // when user press the measure icon, set the action to measure
        measureButton.addActionListener(new java.awt.event.ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                ImageViewerPlugin<E> view = eventManager.getSelectedView2dContainer();
                if (view != null) {
                    @SuppressWarnings("rawtypes")
                    final ViewerToolBar toolBar = view.getViewerToolBar();
                    if (toolBar != null) {
                        String cmd = ActionW.MEASURE.cmd();
                        if (!toolBar.isCommandActive(cmd)) {
                            MouseActions mouseActions = eventManager.getMouseActions();
                            mouseActions.setAction(MouseActions.LEFT, cmd);
                            view.setMouseActions(mouseActions);
                            toolBar.changeButtonState(MouseActions.LEFT, cmd);
                        }
                    }
                }
            }
        });

        add(measureButton);

        jButtondelete.setToolTipText(Messages.getString("MeasureToolBar.del")); //$NON-NLS-1$
        jButtondelete.setIcon(new ImageIcon(MouseActions.class.getResource("/icon/32x32/draw-delete.png"))); //$NON-NLS-1$
        jButtondelete.addActionListener(e -> {
            GraphicModel gm = eventManager.getSelectedViewPane().getGraphicManager();
            if (gm.getSelectedGraphics().isEmpty()) {
                gm.setSelectedAllGraphics();
            }
            gm.deleteSelectedGraphics(eventManager.getSelectedViewPane(), Boolean.TRUE);
        });
        if (measure != null) {
            measure.registerActionState(jButtondelete);
        }
        add(jButtondelete);
    }

    public static void applyDefaultSetting(ViewSetting setting, Graphic graphic) {
        if (graphic instanceof DragGraphic) {
            DragGraphic g = (DragGraphic) graphic;
            g.setLineThickness((float) setting.getLineWidth());
            g.setPaint(setting.getLineColor());
        }
    }

    public static Icon buildIcon(final Graphic graphic) {

        return new DropButtonIcon(new Icon() {

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                if (c instanceof AbstractButton) {
                    AbstractButton model = (AbstractButton) c;
                    Icon icon = null;

                    if (!model.isEnabled()) {
                        icon = UIManager.getLookAndFeel().getDisabledIcon(model, MeasureIcon);
                    }
                    if (icon == null) {
                        icon = MeasureIcon;
                    }
                    icon.paintIcon(c, g, x, y);
                    if (graphic != null) {
                        Icon smallIcon = null;
                        if (!model.isEnabled()) {
                            smallIcon = UIManager.getLookAndFeel().getDisabledIcon(model, graphic.getIcon());
                        }
                        if (smallIcon == null) {
                            smallIcon = graphic.getIcon();
                        }
                        if (smallIcon != null) {
                            x += MeasureIcon.getIconWidth() - smallIcon.getIconWidth() - 1;
                            y += MeasureIcon.getIconHeight() - smallIcon.getIconHeight() - 1;
                            smallIcon.paintIcon(c, g, x, y);
                        }
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
        return Optional.ofNullable(action).flatMap(firstSameAction(measureGraphicList)).orElse(null);
    }

    private static Function<String, Optional<Graphic>> firstSameAction(List<Graphic> list) {
        return action -> list.stream().filter(graphic -> Objects.equals(action, graphic.toString())).findFirst();
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
