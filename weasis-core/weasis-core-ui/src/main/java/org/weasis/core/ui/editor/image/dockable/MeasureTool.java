package org.weasis.core.ui.editor.image.dockable;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.TableModel;

import org.noos.xing.mydoggy.ToolWindowAnchor;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.JToogleButtonGroup;
import org.weasis.core.api.gui.util.TableHeaderRenderer;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.graphic.AbstractDragGraphic;
import org.weasis.core.ui.graphic.AbstractDragGraphicArea;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.MeasureDialog;
import org.weasis.core.ui.graphic.MeasureItem;
import org.weasis.core.ui.graphic.model.GraphicsListener;
import org.weasis.core.ui.util.PreferenceDialog;
import org.weasis.core.ui.util.SimpleTableModel;
import org.weasis.core.ui.util.TableColumnAdjuster;
import org.weasis.core.ui.util.TableNumberRenderer;
import org.weasis.core.ui.util.ViewSetting;

public class MeasureTool extends PluginTool implements GraphicsListener {

    public static final String BUTTON_NAME = "Measure";
    public static final String LABEL_PREF_NAME = "Labels on image";
    public static final int DockableWidth = 210;
    public static final Font TITLE_FONT = FontTools.getFont12Bold();
    public static final Color TITLE_COLOR = Color.GRAY;
    public static final boolean[] MEAS2D_SELECTMEASURES = { true, true, true, true, true, true, true, true, true, true,
        true, true, false };
    public static final boolean[] MEAS1D_SELECTMEASURES = { true, true, true, true, true, true, false, false, false,
        true, true, false };

    private final Border spaceY = BorderFactory.createEmptyBorder(10, 3, 0, 3);
    protected final ImageViewerEventManager eventManager;
    private JPanel tableContainer;
    private JTable jtable;

    private List<AbstractDragGraphic> selectedGraphic;

    public MeasureTool(ImageViewerEventManager eventManager) {
        super(BUTTON_NAME, BUTTON_NAME, ToolWindowAnchor.RIGHT);
        this.eventManager = eventManager;
        setIcon(new ImageIcon(MeasureTool.class.getResource("/icon/16x16/measure.png")));
        setDockableWidth(DockableWidth);
        jbInit();

    }

    private final void jbInit() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        add(getGeneralOptions());
        add(getIconsPanel());
        add(getSelectedMeasurePanel());
        final JPanel panel_1 = new JPanel();
        panel_1.setAlignmentY(Component.TOP_ALIGNMENT);
        panel_1.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel_1.setLayout(new GridBagLayout());
        add(panel_1);

    }

    public final JPanel getIconsPanel() {
        final JPanel transform = new JPanel();
        transform.setAlignmentX(Component.LEFT_ALIGNMENT);
        transform.setAlignmentY(Component.TOP_ALIGNMENT);
        transform.setLayout(new BoxLayout(transform, BoxLayout.Y_AXIS));
        transform.setBorder(BorderFactory.createCompoundBorder(spaceY, new TitledBorder(null, "Draw Measurement",
            TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, TITLE_FONT, TITLE_COLOR)));

        final ViewSetting setting = eventManager.getViewSetting();

        // transform.add(Box.createVerticalStrut(7));

        ActionState measure = eventManager.getAction(ActionW.DRAW_MEASURE);
        if (measure instanceof ComboItemListener) {
            final JPanel p_icons = new JPanel();
            JToogleButtonGroup measures = ((ComboItemListener) measure).createButtonGroup();
            JToggleButton[] items = measures.getJToggleButtonList();
            p_icons.setLayout(new GridLayout((int) (items.length / 3 + 0.5), 3));
            for (int i = 0; i < items.length; i++) {
                items[i].addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        ImageViewerPlugin<ImageElement> view = eventManager.getSelectedView2dContainer();
                        if (view != null) {
                            final ViewerToolBar toolBar = view.getViewerToolBar();
                            if (toolBar != null) {
                                String cmd = ActionW.MEASURE.cmd();
                                if (!toolBar.isCommandActive(cmd)) {
                                    MouseActions mouseActions = eventManager.getMouseActions();
                                    mouseActions.setAction(MouseActions.LEFT, cmd);
                                    if (view != null) {
                                        view.setMouseActions(mouseActions);
                                    }
                                    toolBar.changeButtonState(MouseActions.LEFT, cmd);
                                }
                            }
                        }

                    }
                });
                p_icons.add(items[i]);
            }
            transform.add(p_icons);
        }
        transform.add(Box.createVerticalStrut(5));

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        transform.add(panel);

        JLabel label = new JLabel(MeasureToolBar.lineGraphic.getUIName() + ":");
        panel.add(label);

        JButton button = new JButton("Pick");
        button.setBackground(setting.getLineColor());
        button.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JButton button = (JButton) e.getSource();
                Color newColor =
                    JColorChooser.showDialog(WinUtil.getParentDialogOrFrame(MeasureTool.this), "Pick a color",
                        button.getBackground());
                if (newColor != null) {
                    button.setBackground(newColor);
                    setting.setLineColor(newColor);
                    updateMeasureProperties(setting);
                }
            }
        });
        panel.add(button);

        JSpinner spinner = new JSpinner();
        JMVUtils.setNumberModel(spinner, setting.getLineWidth(), 1, 8, 1);
        spinner.addChangeListener(new ChangeListener() {

            @Override
            public void stateChanged(ChangeEvent e) {
                Object val = ((JSpinner) e.getSource()).getValue();

                if (val instanceof Integer) {
                    setting.setLineWidth((Integer) val);
                    updateMeasureProperties(setting);
                }
            }
        });

        panel.add(spinner);

        ActionState drawOnceAction = eventManager.getAction(ActionW.DRAW_ONLY_ONCE);
        if (drawOnceAction instanceof ToggleButtonListener) {
            transform.add(Box.createVerticalStrut(5));
            JPanel panel_1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            transform.add(panel_1);
            JCheckBox checkDraw =
                ((ToggleButtonListener) drawOnceAction).createCheckBox(ActionW.DRAW_ONLY_ONCE.getTitle());
            checkDraw.setSelected(setting.isDrawOnlyOnce());
            checkDraw.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel_1.add(checkDraw);
        }
        return transform;
    }

    private void updateMeasureProperties(final ViewSetting setting) {
        if (setting != null) {
            for (Graphic graphic : MeasureToolBar.graphicList) {
                MeasureToolBar.applyDefaultSetting(setting, graphic);
            }
        }
    }

    public JPanel getSelectedMeasurePanel() {
        final JPanel transform = new JPanel();
        transform.setAlignmentY(Component.TOP_ALIGNMENT);
        transform.setAlignmentX(Component.LEFT_ALIGNMENT);
        transform.setLayout(new BoxLayout(transform, BoxLayout.Y_AXIS));
        transform.setBorder(BorderFactory.createCompoundBorder(spaceY, new TitledBorder(null, "Selected",
            TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, TITLE_FONT, TITLE_COLOR)));

        JPanel panel_1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        final JButton btnGerenralOptions = new JButton("Change Properties");
        btnGerenralOptions.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (selectedGraphic != null && selectedGraphic.size() > 0) {
                    JDialog dialog = new MeasureDialog(WinUtil.getParentWindow(MeasureTool.this), selectedGraphic);
                    Point location = btnGerenralOptions.getLocation();
                    SwingUtilities.convertPointToScreen(location, btnGerenralOptions);
                    WinUtil.adjustLocationToFitScreen(dialog, location);
                    dialog.setVisible(true);
                }
            }
        });
        panel_1.add(btnGerenralOptions);
        transform.add(panel_1);
        transform.add(Box.createVerticalStrut(5));

        jtable = createMultipleRenderingTable(new SimpleTableModel(new String[] {}, new Object[][] {}));

        // jtable.setPreferredSize(new Dimension(180, 120));
        jtable.getTableHeader().setReorderingAllowed(false);

        tableContainer = new JPanel();
        tableContainer.setBorder(BorderFactory.createEtchedBorder());
        tableContainer.setPreferredSize(new Dimension(50, 80));
        tableContainer.setLayout(new BorderLayout());
        transform.add(tableContainer);

        // transform.add(Box.createVerticalStrut(7));
        // JButton btnStats = new JButton("Show Statistics");
        // transform.add(btnStats);

        return transform;
    }

    public JPanel getGeneralOptions() {
        final JPanel transform = new JPanel();
        transform.setAlignmentY(Component.TOP_ALIGNMENT);
        transform.setAlignmentX(Component.LEFT_ALIGNMENT);
        transform.setLayout(new BoxLayout(transform, BoxLayout.Y_AXIS));
        transform.setBorder(BorderFactory.createCompoundBorder(spaceY, new TitledBorder(null, "Display Options",
            TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, TITLE_FONT, TITLE_COLOR)));

        final ViewSetting setting = eventManager.getViewSetting();
        final JCheckBox chckbxAdvStats = new JCheckBox("More Pixel Statistics", setting.isMoreStatistics());
        chckbxAdvStats.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JCheckBox box = (JCheckBox) e.getSource();
                boolean sel = box.isSelected();
                setting.setMoreStatistics(sel);
                AbstractDragGraphicArea.IMAGE_STD.setComputed(sel);
                AbstractDragGraphicArea.IMAGE_SKEW.setComputed(sel);
                AbstractDragGraphicArea.IMAGE_KURTOSIS.setComputed(sel);
                synchronized (UIManager.VIEWER_PLUGINS) {
                    for (int i = UIManager.VIEWER_PLUGINS.size() - 1; i >= 0; i--) {
                        ViewerPlugin p = UIManager.VIEWER_PLUGINS.get(i);
                        if (p instanceof ImageViewerPlugin) {
                            for (Object v : ((ImageViewerPlugin) p).getImagePanels()) {
                                if (v instanceof DefaultView2d) {
                                    DefaultView2d view = (DefaultView2d) v;
                                    List<Graphic> list = view.getLayerModel().getAllGraphics();
                                    for (Graphic graphic : list) {
                                        graphic.updateLabel(view.getImage(), view);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
        JCheckBox chckbxBasicImageStatistics = new JCheckBox("Basic Pixel Statistics", setting.isBasicStatistics());
        chckbxBasicImageStatistics.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JCheckBox box = (JCheckBox) e.getSource();
                boolean sel = box.isSelected();
                setting.setBasicStatistics(sel);
                chckbxAdvStats.setEnabled(sel);
                AbstractDragGraphicArea.IMAGE_MIN.setComputed(sel);
                AbstractDragGraphicArea.IMAGE_MAX.setComputed(sel);
                AbstractDragGraphicArea.IMAGE_MEAN.setComputed(sel);
                synchronized (UIManager.VIEWER_PLUGINS) {
                    for (int i = UIManager.VIEWER_PLUGINS.size() - 1; i >= 0; i--) {
                        ViewerPlugin p = UIManager.VIEWER_PLUGINS.get(i);
                        if (p instanceof ImageViewerPlugin) {
                            for (Object v : ((ImageViewerPlugin) p).getImagePanels()) {
                                if (v instanceof DefaultView2d) {
                                    DefaultView2d view = (DefaultView2d) v;
                                    List<Graphic> list = view.getLayerModel().getAllGraphics();
                                    for (Graphic graphic : list) {
                                        graphic.updateLabel(view.getImage(), view);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
        transform.add(chckbxBasicImageStatistics);
        transform.add(chckbxAdvStats);
        final JButton btnGerenralOptions = new JButton("Label Option");
        btnGerenralOptions.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PreferenceDialog dialog = new PreferenceDialog(WinUtil.getParentWindow(MeasureTool.this));
                dialog.showPage(LABEL_PREF_NAME);
                JMVUtils.showCenterScreen(dialog);
            }
        });
        transform.add(btnGerenralOptions);
        transform.add(Box.createVerticalStrut(5));
        return transform;
    }

    @Override
    public Component getToolComponent() {
        return new JScrollPane(this);
    }

    @Override
    protected void changeToolWindowAnchor(ToolWindowAnchor anchor) {
        // TODO Auto-generated method stub
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

    public void setSelectedGraphic(Graphic graph, ImageElement imageElement) {
        List<MeasureItem> measList = null;

        if (graph != null && imageElement != null) {
            measList = graph.computeMeasurements(imageElement, true);
        }
        updateMeasuredItems(measList);
    }

    public void updateMeasuredItems(List<MeasureItem> measList) {
        tableContainer.removeAll();

        // just clear tableContainer if measList is null
        if (measList != null) {
            Object[][] labels = new Object[measList.size()][];
            for (int i = 0; i < labels.length; i++) {
                MeasureItem m = measList.get(i);
                Object[] row = new Object[2];
                StringBuffer buffer = new StringBuffer(m.getMeasurement().getName());
                if (m.getUnit() != null) {
                    buffer.append(" [");
                    buffer.append(m.getUnit());
                    buffer.append("]");
                }
                row[0] = buffer.toString();
                row[1] = m.getValue();
                labels[i] = row;
            }
            String[] headers = { "Parameter", "Value" };
            jtable.setModel(new SimpleTableModel(headers, labels));
            jtable.getColumnModel().getColumn(1).setCellRenderer(new TableNumberRenderer());
            createTableHeaders(jtable);
            int height =
                (jtable.getRowHeight() + jtable.getRowMargin()) * jtable.getRowCount()
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

    public static int getNumberOfMeasures(boolean[] select) {
        int k = 0;
        for (int i = 0; i < select.length; i++) {
            if (select[i]) {
                k++;
            }
        }
        return k;
    }

    void jButtonOpenHisto_actionPerformed(ActionEvent e) {
        // if (histo == null) {
        // histo = new ImageHistogramPlot(this, "Histogram");
        // JMVUtils.showCenterScreen(histo);
        // }
    }

    @Override
    public void handle(List<Graphic> selectedGraphicList, ImageElement img) {
        Graphic g = null;
        List<AbstractDragGraphic> list = null;

        if (selectedGraphicList != null) {
            if (selectedGraphicList.size() == 1) {
                g = selectedGraphicList.get(0);
            }

            list = new ArrayList<AbstractDragGraphic>();

            for (Graphic graphic : selectedGraphicList) {
                if (graphic instanceof AbstractDragGraphic) {
                    list.add((AbstractDragGraphic) graphic);
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
            setSelectedGraphic(g, img);
        }
    }

}
