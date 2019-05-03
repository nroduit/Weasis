/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.ui.editor.image.dockable;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.table.TableModel;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.JToogleButtonGroup;
import org.weasis.core.api.gui.util.TableHeaderRenderer;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicSelectionListener;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.ImageStatistics;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.ui.pref.ViewSetting;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.SimpleTableModel;
import org.weasis.core.ui.util.TableColumnAdjuster;
import org.weasis.core.ui.util.TableNumberRenderer;

import bibliothek.gui.dock.common.CLocation;

public class MeasureTool extends PluginTool implements GraphicSelectionListener {
    private static final long serialVersionUID = 1117961156637401550L;

    public static final String BUTTON_NAME = ActionW.DRAW + " & " + ActionW.MEASURE; //$NON-NLS-1$
    public static final String LABEL_PREF_NAME = Messages.getString("MeasureTool.lab_img"); //$NON-NLS-1$
    public static final ViewSetting viewSetting = new ViewSetting();

    protected final ImageViewerEventManager<? extends ImageElement> eventManager;
    private final JScrollPane rootPane;
    private JPanel tableContainer;
    private JTable jtable;

    private List<DragGraphic> selectedGraphic;

    public MeasureTool(ImageViewerEventManager<? extends ImageElement> eventManager) {
        super(BUTTON_NAME, BUTTON_NAME, PluginTool.Type.TOOL, 30);
        this.eventManager = eventManager;
        this.rootPane = new JScrollPane();
        dockable.setTitleIcon(new ImageIcon(MeasureTool.class.getResource("/icon/16x16/measure.png"))); //$NON-NLS-1$
        setDockableWidth(javax.swing.UIManager.getLookAndFeel() != null
            ? javax.swing.UIManager.getLookAndFeel().getClass().getName().startsWith("org.pushingpixels") ? 190 : 205 //$NON-NLS-1$
            : 205);
        jbInit();
    }

    private final void jbInit() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        add(getIconsPanel());
        add(getSelectedMeasurePanel());
    }

    public final JPanel getIconsPanel() {
        final JPanel transform = new JPanel();
        transform.setAlignmentX(Component.LEFT_ALIGNMENT);
        transform.setAlignmentY(Component.TOP_ALIGNMENT);
        transform.setLayout(new BoxLayout(transform, BoxLayout.Y_AXIS));

        MeasureTool.buildIconPanel(transform, eventManager, ActionW.MEASURE, ActionW.DRAW_MEASURE, 4);
        MeasureTool.buildIconPanel(transform, eventManager, ActionW.DRAW, ActionW.DRAW_GRAPHICS, 4);

        transform.add(Box.createVerticalStrut(5));

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        transform.add(panel);

        JLabel label = new JLabel(Messages.getString("MeasureToolBar.line") + StringUtil.COLON); //$NON-NLS-1$
        panel.add(label);

        JButton button = new JButton(Messages.getString("MeasureTool.pick")); //$NON-NLS-1$
        button.setBackground(viewSetting.getLineColor());
        button.addActionListener(e -> {
            JButton btn = (JButton) e.getSource();
            Color newColor = JColorChooser.showDialog(SwingUtilities.getWindowAncestor(MeasureTool.this),
                Messages.getString("MeasureTool.pick_color"), //$NON-NLS-1$
                btn.getBackground());
            if (newColor != null) {
                btn.setBackground(newColor);
                viewSetting.setLineColor(newColor);
                updateMeasureProperties(viewSetting);
            }
        });
        panel.add(button);

        JSpinner spinner = new JSpinner();
        JMVUtils.setNumberModel(spinner, viewSetting.getLineWidth(), 1, 8, 1);
        spinner.addChangeListener(e -> {
            Object val = ((JSpinner) e.getSource()).getValue();

            if (val instanceof Integer) {
                viewSetting.setLineWidth((Integer) val);
                updateMeasureProperties(viewSetting);
            }

        });
        panel.add(spinner);

        ActionState drawOnceAction = eventManager.getAction(ActionW.DRAW_ONLY_ONCE);
        if (drawOnceAction instanceof ToggleButtonListener) {
            transform.add(Box.createVerticalStrut(5));
            JPanel panel1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            transform.add(panel1);
            JCheckBox checkDraw =
                ((ToggleButtonListener) drawOnceAction).createCheckBox(ActionW.DRAW_ONLY_ONCE.getTitle());
            checkDraw.setSelected(viewSetting.isDrawOnlyOnce());
            checkDraw.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel1.add(checkDraw);
        }
        JPanel panel1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        transform.add(panel1);
        JCheckBox chckbxBasicImageStatistics =
            new JCheckBox(Messages.getString("MeasureTool.pix_stats"), viewSetting.isBasicStatistics()); //$NON-NLS-1$
        chckbxBasicImageStatistics.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel1.add(chckbxBasicImageStatistics);
        chckbxBasicImageStatistics.addActionListener(e -> {
            JCheckBox box = (JCheckBox) e.getSource();
            boolean sel = box.isSelected();
            viewSetting.setBasicStatistics(sel);
            // Force also advanced statistics
            viewSetting.setMoreStatistics(sel);
            for (Measurement m : ImageStatistics.ALL_MEASUREMENTS) {
                m.setComputed(sel);
            }
            synchronized (UIManager.VIEWER_PLUGINS) {
                for (int i = UIManager.VIEWER_PLUGINS.size() - 1; i >= 0; i--) {
                    ViewerPlugin<?> p = UIManager.VIEWER_PLUGINS.get(i);
                    if (p instanceof ImageViewerPlugin) {
                        for (Object v : ((ImageViewerPlugin<?>) p).getImagePanels()) {
                            if (v instanceof ViewCanvas) {
                                ViewCanvas<?> view = (ViewCanvas<?>) v;
                                view.getGraphicManager().updateLabels(true, view);
                            }
                        }
                    }
                }
            }
        });
        panel1.add(chckbxBasicImageStatistics);

        ActionState spUnitAction = eventManager.getAction(ActionW.SPATIAL_UNIT);
        if (spUnitAction instanceof ComboItemListener) {
            final JPanel panel4 = new JPanel(new FlowLayout(FlowLayout.LEADING, 2, 3));
            final JLabel lutLabel = new JLabel();
            lutLabel.setText(Messages.getString("MeasureTool.unit") + StringUtil.COLON); //$NON-NLS-1$
            panel4.add(lutLabel);
            final JComboBox unitComboBox = ((ComboItemListener) spUnitAction).createCombo(120);
            unitComboBox.setSelectedItem(Unit.PIXEL);
            panel4.add(unitComboBox);
            transform.add(panel4);
        }

        transform.add(Box.createVerticalStrut(5));
        JPanel panel2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        transform.add(panel2);
        final JButton btnGerenralOptions = new JButton(Messages.getString("MeasureTool.more_options")); //$NON-NLS-1$
        btnGerenralOptions.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel2.add(btnGerenralOptions);
        btnGerenralOptions.addActionListener(e -> {
            ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(MeasureTool.this);
            PreferenceDialog dialog = new PreferenceDialog(SwingUtilities.getWindowAncestor(MeasureTool.this));
            dialog.showPage(LABEL_PREF_NAME);
            ColorLayerUI.showCenterScreen(dialog, layer);
        });
        transform.add(panel2);
        transform.add(Box.createVerticalStrut(5));

        return transform;
    }

    private static void updateMeasureProperties(final ViewSetting setting) {
        if (setting != null) {
            MeasureToolBar.measureGraphicList.forEach(g -> MeasureToolBar.applyDefaultSetting(setting, g));
            MeasureToolBar.drawGraphicList.forEach(g -> MeasureToolBar.applyDefaultSetting(setting, g));
        }
    }

    public JPanel getSelectedMeasurePanel() {
        final JPanel transform = new JPanel();
        transform.setAlignmentY(Component.TOP_ALIGNMENT);
        transform.setAlignmentX(Component.LEFT_ALIGNMENT);
        transform.setLayout(new BoxLayout(transform, BoxLayout.Y_AXIS));
        transform.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(10, 3, 0, 3),
            new TitledBorder(null, Messages.getString("MeasureTool.sel"), //$NON-NLS-1$
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, FontTools.getFont12Bold(),
                Color.GRAY)));

        JPanel panel1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        transform.add(panel1);
        transform.add(Box.createVerticalStrut(5));
        jtable = createMultipleRenderingTable(new SimpleTableModel(new String[] {}, new Object[][] {}));
        jtable.setFont(FontTools.getFont10());

        jtable.getTableHeader().setReorderingAllowed(false);

        tableContainer = new JPanel();
        tableContainer.setBorder(BorderFactory.createEtchedBorder());
        tableContainer.setPreferredSize(new Dimension(50, 80));
        tableContainer.setLayout(new BorderLayout());
        transform.add(tableContainer);

        return transform;
    }

    @Override
    public Component getToolComponent() {
        JViewport viewPort = rootPane.getViewport();
        if (viewPort == null) {
            viewPort = new JViewport();
            rootPane.setViewport(viewPort);
        }
        if (viewPort.getView() != this) {
            viewPort.setView(this);
        }
        return rootPane;
    }

    @Override
    protected void changeToolWindowAnchor(CLocation clocation) {
        // Do nothing
    }

    public static JTable createMultipleRenderingTable(TableModel model) {
        JTable table = new JTable(model);
        table.getColumnModel().setColumnMargin(3);
        return table;
    }

    public static void createTableHeaders(JTable table) {
        table.getColumnModel().getColumn(0).setHeaderRenderer(new TableHeaderRenderer());
        table.getColumnModel().getColumn(1).setHeaderRenderer(new TableHeaderRenderer());
    }

    public void setSelectedGraphic(Graphic graph, MeasurableLayer layer) {
        List<MeasureItem> measList = null;

        if (graph != null && layer != null && graph.getLayerType() == LayerType.MEASURE) {
            Unit unit = null;
            ActionState spUnitAction = eventManager.getAction(ActionW.SPATIAL_UNIT);
            if (spUnitAction instanceof ComboItemListener) {
                unit = (Unit) ((ComboItemListener) spUnitAction).getSelectedItem();
            }
            measList = graph.computeMeasurements(layer, true, unit);
        }
        updateMeasuredItems(measList);
    }

    @Override
    public void updateMeasuredItems(List<MeasureItem> measList) {
        tableContainer.removeAll();

        // just clear tableContainer if measList is null
        if (measList != null) {
            String[] headers = { Messages.getString("MeasureTool.param"), Messages.getString("MeasureTool.val") }; //$NON-NLS-1$ //$NON-NLS-2$
            jtable.setModel(new SimpleTableModel(headers, getLabels(measList)));
            jtable.getColumnModel().getColumn(1).setCellRenderer(new TableNumberRenderer());
            createTableHeaders(jtable);
            int height = (jtable.getRowHeight() + jtable.getRowMargin()) * jtable.getRowCount()
                + jtable.getTableHeader().getHeight() + 5;
            tableContainer.setPreferredSize(new Dimension(jtable.getColumnModel().getTotalColumnWidth(), height));
            tableContainer.add(jtable.getTableHeader(), BorderLayout.PAGE_START);
            tableContainer.add(jtable, BorderLayout.CENTER);
            TableColumnAdjuster.pack(jtable);
        } else {
            tableContainer.setPreferredSize(new Dimension(50, 50));
        }
        tableContainer.revalidate();
        tableContainer.repaint();
    }

    public static Object[][] getLabels(List<MeasureItem> measList) {
        if (measList != null) {
            Object[][] labels = new Object[measList.size()][];
            for (int i = 0; i < labels.length; i++) {
                MeasureItem m = measList.get(i);
                Object[] row = new Object[2];
                StringBuilder buffer = new StringBuilder(m.getMeasurement().getName());
                if (m.getLabelExtension() != null) {
                    buffer.append(m.getLabelExtension());
                }
                if (m.getUnit() != null) {
                    buffer.append(" ["); //$NON-NLS-1$
                    buffer.append(m.getUnit());
                    buffer.append("]"); //$NON-NLS-1$
                }
                row[0] = buffer.toString();
                row[1] = m.getValue();
                labels[i] = row;
            }
            return labels;
        }
        return null;
    }

    public static int getNumberOfMeasures(boolean[] select) {
        int k = 0;
        for (int i = 0; i < select.length; i++) {
            if (select[i]) {
                k++;
            }
        }
        return k;
    }

    @Override
    public void handle(List<Graphic> selectedGraphicList, MeasurableLayer layer) {
        Graphic g = null;
        List<DragGraphic> list = null;

        if (selectedGraphicList != null) {
            if (selectedGraphicList.size() == 1) {
                g = selectedGraphicList.get(0);
            }

            list = new ArrayList<>();

            for (Graphic graphic : selectedGraphicList) {
                if (graphic instanceof DragGraphic) {
                    list.add((DragGraphic) graphic);
                }
            }
        }

        boolean computeAllMeasures = true;
        if (selectedGraphic != null) {
            if (g != null && selectedGraphic.size() == 1) {
                // Warning only comparing if it is the same instance, cannot compare handle points.
                // Update of the list of measures is performed in the drag sequence (move, complete). Here only the
                // change of selection will compute the measurements
                if (g == selectedGraphic.get(0)) {
                    computeAllMeasures = false;
                }
            }
            selectedGraphic.clear();
        }

        this.selectedGraphic = list;
        if (computeAllMeasures) {
            // if g equals null means graphic is not single or no graphic is selected
            setSelectedGraphic(g, layer);
        }
    }

    public static void buildIconPanel(JPanel rootPanel, ImageViewerEventManager<?> eventManager, ActionW action,
        ActionW graphicAction, int lineLength) {
        Optional<ComboItemListener> actionState = eventManager.getAction(graphicAction, ComboItemListener.class);
        if (!actionState.isPresent()) {
            return;
        }

        final JPanel pIcons = new JPanel();
        pIcons.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(10, 3, 0, 3),
            new TitledBorder(null, graphicAction.getTitle(), TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION, FontTools.getFont12Bold(), Color.GRAY)));

        JToogleButtonGroup measures = actionState.get().createButtonGroup();
        JToggleButton[] items = measures.getJToggleButtonList();

        pIcons.setLayout(new GridBagLayout());
        for (int i = 0; i < items.length; i++) {
            items[i].addActionListener(e -> {
                ImageViewerPlugin<? extends ImageElement> view = eventManager.getSelectedView2dContainer();
                if (view != null) {
                    final ViewerToolBar toolBar = view.getViewerToolBar();
                    if (toolBar != null) {
                        String cmd = action.cmd();
                        if (!toolBar.isCommandActive(cmd)) {
                            MouseActions mouseActions = eventManager.getMouseActions();
                            mouseActions.setAction(MouseActions.T_LEFT, cmd);
                            view.setMouseActions(mouseActions);
                            toolBar.changeButtonState(MouseActions.T_LEFT, cmd);
                        }
                    }
                }

            });
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(0, 0, 5, 5);
            constraints.gridx = i % lineLength;
            constraints.gridy = i / lineLength;
            Dimension size = items[i].getPreferredSize();
            if (size != null && size.width > size.height) {
                items[i].setPreferredSize(new Dimension(size.height + 2, size.height));
            }
            pIcons.add(items[i], constraints);
        }
        JPanel panelLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panelLeft.add(pIcons);
        rootPanel.add(panelLeft);
    }

}
