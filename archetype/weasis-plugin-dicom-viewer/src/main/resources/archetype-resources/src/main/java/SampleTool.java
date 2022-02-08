#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )

package ${package};

import bibliothek.gui.dock.common.CLocation;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Component;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import org.weasis.core.ui.docking.PluginTool;

public class SampleTool extends PluginTool {

    public static final String BUTTON_NAME = "Tool Sample";

    private final JScrollPane rootPane = new JScrollPane();

    public SampleTool(Type type) {
        super(BUTTON_NAME, "Sample Tool", type, 120);
        dockable.setTitleIcon(
            new FlatSVGIcon(
                (Objects.requireNonNull(SampleTool.class.getResource("/icon/svg/tools.svg")))));
        setDockableWidth(290);
        rootPane.setBorder(BorderFactory.createEmptyBorder()); // remove default line
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
        // TODO Auto-generated method stub
    }
}
