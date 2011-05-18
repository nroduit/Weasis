package org.weasis.core.ui.editor.image.dockable;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.media.jai.PlanarImage;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.table.TableModel;

import org.noos.xing.mydoggy.ToolWindowAnchor;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.JToogleButtonGroup;
import org.weasis.core.api.gui.util.TableHaederRenderer;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.graphic.AbstractDragGraphic;
import org.weasis.core.ui.graphic.EllipseGraphic;
import org.weasis.core.ui.graphic.FreeHandGraphic;
import org.weasis.core.ui.graphic.FreeHandLineGraphic;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.LineGraphic;
import org.weasis.core.ui.graphic.Measure1DAnalyse;
import org.weasis.core.ui.graphic.Measure2DAnalyse;
import org.weasis.core.ui.graphic.MeasureDialog;
import org.weasis.core.ui.graphic.PolygonGraphic;
import org.weasis.core.ui.graphic.RectangleGraphic;
import org.weasis.core.ui.graphic.model.GraphicsListener;
import org.weasis.core.ui.util.SimpleTableModel;
import org.weasis.core.ui.util.TableNumberRenderer;

public class MeasureTool extends PluginTool implements GraphicsListener {

    public final static String BUTTON_NAME = "Measure";

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

    private Graphic selectedGraphic;

    public MeasureTool(String pluginName, Icon icon, ImageViewerEventManager eventManager) {
        super(BUTTON_NAME, pluginName, ToolWindowAnchor.RIGHT);
        this.eventManager = eventManager;
        setDockableWidth(290);
        jbInit();

    }

    private void jbInit() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        add(getIconsPanel());
        add(getSelectedMeasurePanel());

        final JPanel panel_1 = new JPanel();
        panel_1.setAlignmentY(Component.TOP_ALIGNMENT);
        panel_1.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel_1.setLayout(new GridBagLayout());
        add(panel_1);
    }

    public JPanel getIconsPanel() {
        final JPanel transform = new JPanel();
        transform.setAlignmentY(Component.TOP_ALIGNMENT);
        transform.setAlignmentX(Component.LEFT_ALIGNMENT);
        transform.setLayout(new BoxLayout(transform, BoxLayout.Y_AXIS));
        transform.setBorder(BorderFactory.createCompoundBorder(spaceY, new TitledBorder(null, "Measurements",
            TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, TITLE_FONT, TITLE_COLOR)));

        ActionState drawOnceAction = eventManager.getAction(ActionW.DRAW_ONLY_ONCE);
        if (drawOnceAction instanceof ToggleButtonListener) {
            // JPanel pane = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 3));
            transform.add(((ToggleButtonListener) drawOnceAction).createCheckBox(ActionW.DRAW_ONLY_ONCE.getTitle()));
            // transform.add(pane);
        }
        transform.add(Box.createVerticalStrut(7));

        ActionState measure = eventManager.getAction(ActionW.DRAW_MEASURE);
        if (measure instanceof ComboItemListener) {
            final JPanel p_icons = new JPanel();
            p_icons.setAlignmentY(Component.TOP_ALIGNMENT);
            p_icons.setAlignmentX(Component.LEFT_ALIGNMENT);
            JToogleButtonGroup measures = ((ComboItemListener) measure).createButtonGroup();
            JToggleButton[] items = measures.getJToggleButtonList();
            p_icons.setLayout(new GridLayout(items.length / 5 + 1, 5));
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
        transform.add(Box.createVerticalStrut(7));
        // JButton btnGerenralOptions = new JButton("General Options");
        // transform.add(btnGerenralOptions);
        return transform;
    }

    public JPanel getSelectedMeasurePanel() {
        final JPanel transform = new JPanel();
        transform.setAlignmentY(Component.TOP_ALIGNMENT);
        transform.setAlignmentX(Component.LEFT_ALIGNMENT);
        transform.setLayout(new BoxLayout(transform, BoxLayout.Y_AXIS));
        transform.setBorder(BorderFactory.createCompoundBorder(spaceY, new TitledBorder(null, "Selected Measurement",
            TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, TITLE_FONT, TITLE_COLOR)));

        final JButton btnGerenralOptions = new JButton("Change Properties");
        btnGerenralOptions.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (selectedGraphic instanceof AbstractDragGraphic) {
                    AbstractDragGraphic graph = (AbstractDragGraphic) selectedGraphic;
                    JDialog dialog = new MeasureDialog(WinUtil.getParentWindow(MeasureTool.this), graph);
                    Point location = btnGerenralOptions.getLocation();
                    SwingUtilities.convertPointToScreen(location, btnGerenralOptions);
                    WinUtil.adjustLocationToFitScreen(dialog, location);
                    dialog.setVisible(true);
                    graph.showProperties();
                }
            }
        });
        transform.add(btnGerenralOptions);
        transform.add(Box.createVerticalStrut(5));

        jtable = createMultipleRenderingTable(new SimpleTableModel(new String[] {}, new Object[][] {}));
        jtable.setPreferredSize(new Dimension(180, 120));
        jtable.getTableHeader().setReorderingAllowed(false);
        tableContainer = new JPanel();
        tableContainer.setPreferredSize(new Dimension(180, 120));
        tableContainer.setLayout(new BorderLayout());
        transform.add(tableContainer);

        // transform.add(Box.createVerticalStrut(7));
        // JButton btnStats = new JButton("Show Statistics");
        // transform.add(btnStats);
        transform.add(Box.createVerticalStrut(7));
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
        table.getColumnModel().setColumnMargin(5);
        return table;
    }

    public static void createTableHeaders(JTable table) {
        TableHaederRenderer renderer = new TableHaederRenderer();
        renderer.setHorizontalAlignment(SwingConstants.LEFT);
        table.getColumnModel().getColumn(0).setHeaderRenderer(renderer);
        table.getColumnModel().getColumn(1).setHeaderRenderer(new TableHaederRenderer());
    }

    public Object[][] createRows(java.util.List tableResult, java.util.List<String> measures) {

        Object[][] data = new Object[measures.size()][];
        for (int j = 0; j < data.length; j++) {
            Object[] row = new Object[2];
            row[0] = measures.get(j);
            row[1] = tableResult.get(j);
            data[j] = row;
        }
        return data;
    }

    public void setSelectedGraphic(Graphic graph, ImageElement imageElement) {
        tableContainer.removeAll();
        this.selectedGraphic = graph;
        if (graph != null && imageElement != null) {
            Unit unit = imageElement.getPixelSpacingUnit();
            PlanarImage image = imageElement.getImage();
            int height = 0;
            if (image != null) {
                height = image.getHeight();
            }
            MeasurementsAdapter measurementsAdapter =
                new MeasurementsAdapter(imageElement.getPixelSizeX(), imageElement.getPixelSizeY(), 0, 0, false,
                    height, unit.getAbbreviation());

            if (graph instanceof RectangleGraphic || graph instanceof EllipseGraphic || graph instanceof PolygonGraphic
                || graph instanceof FreeHandGraphic) {

                Measure2DAnalyse blob =
                    new Measure2DAnalyse(graph.getShape(), measurementsAdapter,
                        (Color) ((AbstractDragGraphic) graph).getColorPaint());
                // boolean[] selected = imageFrame.getSettingsData().getMeas2D_SelectMeasures();
                // if (selected == null) {
                // return;
                // }
                int nbMeasures = getNumberOfMeasures(MEAS2D_SELECTMEASURES);

                String[] headers = { "Parameter", "Value [" + unit.getAbbreviation() + "]" };
                java.util.List<String> measures = Measure2DAnalyse.getObjectMeasureList(MEAS2D_SELECTMEASURES);
                jtable.setModel(new SimpleTableModel(headers, createRows(
                    blob.getAnalyse(MEAS2D_SELECTMEASURES, nbMeasures), measures)));
                jtable.getColumnModel().getColumn(1).setCellRenderer(new TableNumberRenderer());
                createTableHeaders(jtable);
                tableContainer.add(jtable.getTableHeader(), BorderLayout.PAGE_START);
                tableContainer.add(jtable, BorderLayout.CENTER);

                // if (histo != null) {
                // histo.updateHistogram(new ROIShape(graph.getShape()));
                // }
            } else if (graph instanceof LineGraphic || graph instanceof FreeHandLineGraphic) {
                Measure1DAnalyse blob = new Measure1DAnalyse((AbstractDragGraphic) graph, measurementsAdapter);
                // boolean[] selected = imageFrame.getSettingsData().getMeas2D_SelectMeasures();
                // if (selected == null) {
                // return;
                // }
                int nbMeasures = getNumberOfMeasures(MEAS1D_SELECTMEASURES);

                String[] headers = { "Parameter", "Value [" + unit.getAbbreviation() + "]" };
                java.util.List<String> measures = Measure1DAnalyse.getObjectMeasureList(MEAS1D_SELECTMEASURES);
                jtable.setModel(new SimpleTableModel(headers, createRows(
                    blob.getMeasure1DAnalyse(MEAS1D_SELECTMEASURES, nbMeasures), measures)));
                jtable.getColumnModel().getColumn(1).setCellRenderer(new TableNumberRenderer());
                createTableHeaders(jtable);
                tableContainer.add(jtable.getTableHeader(), BorderLayout.PAGE_START);
                tableContainer.add(jtable, BorderLayout.CENTER);
            }
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
    public void handle(List<Graphic> selectedGraphics, ImageElement img) {
        Graphic g = null;
        if (selectedGraphics != null && selectedGraphics.size() == 1) {
            g = selectedGraphics.get(0);
        }
        setSelectedGraphic(g, img);
    }

}
