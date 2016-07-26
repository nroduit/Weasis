package org.weasis.acquire.dockable.components.actions.annotate.comp;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.JToogleButtonGroup;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.ViewerToolBar;

@SuppressWarnings("serial")
public class AnnotationIconsPanel extends JPanel {

    public AnnotationIconsPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
       createButtons();
    }

    private void createButtons() {
        final JPanel p_icons = new JPanel();
        p_icons.setBorder(new TitledBorder(new EmptyBorder(5, 5, 5, 5), "Graphics", TitledBorder.LEADING, TitledBorder.TOP, null, null));
        
        JToogleButtonGroup measures = AnnotationActionState.getInstance().createButtonGroup();
        JToggleButton[] items = measures.getJToggleButtonList();

        p_icons.setLayout(new GridBagLayout());
        for (int i = 0; i < items.length; i++) {
            items[i].addActionListener(e -> {
                ImageViewerEventManager<ImageElement> eventManager = EventManager.getInstance();
                ImageViewerPlugin<? extends ImageElement> view = eventManager.getSelectedView2dContainer();
                if (view != null) {
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

            });
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(0, 0, 5, 5);
            constraints.gridx = i % 7;
            constraints.gridy = i / 7;
            Dimension size = items[i].getPreferredSize();
            if (size != null && size.width > size.height) {
                items[i].setPreferredSize(new Dimension(size.height + 2, size.height));
            }
            p_icons.add(items[i], constraints);
        }
        add(p_icons);
    }
}
