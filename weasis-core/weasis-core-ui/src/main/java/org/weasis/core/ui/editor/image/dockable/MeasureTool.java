package org.weasis.core.ui.editor.image.dockable;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;

import javax.media.jai.PlanarImage;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.table.TableModel;

import org.noos.xing.mydoggy.ToolWindowAnchor;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.JToogleButtonGroup;
import org.weasis.core.api.gui.util.TableHaederRenderer;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.graphic.AbstractDragGraphic;
import org.weasis.core.ui.graphic.Measure2DAnalyse;
import org.weasis.core.ui.util.SimpleTableModel;
import org.weasis.core.ui.util.TableNumberRenderer;

public class MeasureTool extends PluginTool {

    public final static String BUTTON_NAME = "Measure";

    public static final Font TITLE_FONT = FontTools.getFont12Bold();
    public static final Color TITLE_COLOR = Color.GRAY;
    public static final boolean[] MEAS2D_SELECTMEASURES = { true, true, true, true, true, true, true, true, true, true,
        true, true, true };

    private final Border spaceY = BorderFactory.createEmptyBorder(10, 3, 0, 3);
    protected final ImageViewerEventManager eventManager;
    private JTable jtable;
    private final JScrollPane jScrollPane1 = new JScrollPane();

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

        JButton btnGerenralOptions = new JButton("General Options");
        transform.add(btnGerenralOptions);

        JCheckBox chckbxReturnToSelection = new JCheckBox("Return to selection after drawing");
        transform.add(chckbxReturnToSelection);
        transform.add(Box.createVerticalStrut(7));

        ActionState measure = eventManager.getAction(ActionW.DRAW_MEASURE);
        if (measure instanceof ComboItemListener) {
            final JPanel p_icons = new JPanel();
            p_icons.setAlignmentY(Component.TOP_ALIGNMENT);
            p_icons.setAlignmentX(Component.LEFT_ALIGNMENT);
            JToogleButtonGroup measures = ((ComboItemListener) measure).createButtonGroup();
            JToggleButton[] items = measures.getJToggleButtonList();
            p_icons.setLayout(new GridLayout(items.length / 2 + 1, 2));
            for (int i = 0; i < items.length; i++) {
                p_icons.add(items[i]);
            }
            transform.add(p_icons);

        }
        return transform;
    }

    public JPanel getSelectedMeasurePanel() {
        final JPanel transform = new JPanel();
        transform.setAlignmentY(Component.TOP_ALIGNMENT);
        transform.setAlignmentX(Component.LEFT_ALIGNMENT);
        transform.setLayout(new BoxLayout(transform, BoxLayout.Y_AXIS));
        transform.setBorder(BorderFactory.createCompoundBorder(spaceY, new TitledBorder(null, "Selected Measurement",
            TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, TITLE_FONT, TITLE_COLOR)));

        JButton btnGerenralOptions = new JButton("Change Properties");
        transform.add(btnGerenralOptions);
        transform.add(Box.createVerticalStrut(5));

        jScrollPane1.setPreferredSize(new Dimension(220, 80));
        jtable = createMultipleRenderingTable(new SimpleTableModel(new String[] {}, new Object[][] {}));
        jtable.setPreferredScrollableViewportSize(new Dimension(500, 70));
        jtable.getTableHeader().setReorderingAllowed(false);
        jScrollPane1.setViewportView(jtable);
        transform.add(jScrollPane1);

        transform.add(Box.createVerticalStrut(7));
        JButton btnStats = new JButton("Show Statistics");
        transform.add(btnStats);
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

    public Object[][] createRows(java.util.List tableResult) {
        java.util.List measures = Measure2DAnalyse.getObjectMeasureList(MEAS2D_SELECTMEASURES);
        Object[][] data = new Object[measures.size()][];
        for (int j = 0; j < data.length; j++) {
            Object[] row = new Object[2];
            row[0] = measures.get(j);
            row[1] = tableResult.get(j);
            data[j] = row;
        }
        return data;
    }

    public void updateHistogramFrame(AbstractDragGraphic graph, ImageElement imageElement) {
        if (imageElement != null) {
            Unit unit = imageElement.getPixelSpacingUnit();
            PlanarImage image = imageElement.getImage();
            int height = 0;
            if (image != null) {
                height = image.getHeight();
            }
            MeasurementsAdapter measurementsAdapter =
                new MeasurementsAdapter(1.0, false, 0, 0, false, height, unit.getAbbreviation());
            Measure2DAnalyse blob =
                new Measure2DAnalyse(graph.getShape(), measurementsAdapter, (Color) graph.getPaint());
            // boolean[] selected = imageFrame.getSettingsData().getMeas2D_SelectMeasures();
            // if (selected == null) {
            // return;
            // }
            int nbMeasures = getNumberOfMeasures(MEAS2D_SELECTMEASURES);

            String[] headers = { "Parameter", "Value [" + unit.getAbbreviation() + "]" };
            jtable.setModel(new SimpleTableModel(headers,
                createRows(blob.getAnalyse(MEAS2D_SELECTMEASURES, nbMeasures))));
            jtable.getColumnModel().getColumn(1).setCellRenderer(new TableNumberRenderer());
            createTableHeaders(jtable);

            // if (histo != null) {
            // histo.updateHistogram(new ROIShape(graph.getShape()));
            // }
        }
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
}
