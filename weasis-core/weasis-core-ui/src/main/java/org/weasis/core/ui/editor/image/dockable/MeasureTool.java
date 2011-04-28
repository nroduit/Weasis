package org.weasis.core.ui.editor.image.dockable;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

import org.noos.xing.mydoggy.ToolWindowAnchor;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.JToogleButtonGroup;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;

public class MeasureTool extends PluginTool {

    public final static String BUTTON_NAME = "Measure";

    public static final Font TITLE_FONT = FontTools.getFont12Bold();
    public static final Color TITLE_COLOR = Color.GRAY;

    private final Border spaceY = BorderFactory.createEmptyBorder(10, 3, 0, 3);
    protected final ImageViewerEventManager eventManager;

    public MeasureTool(String pluginName, Icon icon, ImageViewerEventManager eventManager) {
        super(BUTTON_NAME, pluginName, ToolWindowAnchor.RIGHT);
        this.eventManager = eventManager;
        setDockableWidth(290);
        jbInit();

    }

    private void jbInit() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        add(getIconsPanel());

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

    @Override
    public Component getToolComponent() {
        return new JScrollPane(this);
    }

    @Override
    protected void changeToolWindowAnchor(ToolWindowAnchor anchor) {
        // TODO Auto-generated method stub

    }
}
