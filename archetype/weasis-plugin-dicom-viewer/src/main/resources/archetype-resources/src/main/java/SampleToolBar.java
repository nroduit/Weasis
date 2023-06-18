#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package};

import java.awt.Component;
import java.net.MalformedURLException;
import java.net.URL;
import javax.swing.JButton;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.util.WtoolBar;

public class SampleToolBar<DicomImageElement> extends WtoolBar {

    protected SampleToolBar() {
        super("Sample Toolbar", 400);

        final JButton helpButton = new JButton();
        helpButton.setToolTipText("User Guide");
        helpButton.putClientProperty("JButton.buttonType", "help");
        helpButton.addActionListener(
            e -> {
                if (e.getSource() instanceof Component component) {
                    URL url;
                    try {
                        url = new URL("https://nroduit.github.io/en/tutorials/");
                        GuiUtils.openInDefaultBrowser(component, url);
                    } catch (MalformedURLException e1) {
                        e1.printStackTrace();
                    }
                }
            });
        add(helpButton);
    }
}
