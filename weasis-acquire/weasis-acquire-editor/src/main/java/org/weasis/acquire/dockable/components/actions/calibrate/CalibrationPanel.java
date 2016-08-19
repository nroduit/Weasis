package org.weasis.acquire.dockable.components.actions.calibrate;

import javax.swing.JLabel;

import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireImageValues;
import org.weasis.acquire.graphics.CalibrationGraphic;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerToolBar;

public class CalibrationPanel extends AbstractAcquireActionPanel {
    private static final long serialVersionUID = 3956795043244254606L;

    public static final CalibrationGraphic CALIBRATION_LINE_GRAPHIC = new CalibrationGraphic();

    public CalibrationPanel() {
        add(new JLabel("Draw a line on the image"));
    }

    @Override
    public void initValues(AcquireImageInfo info, AcquireImageValues values) {
        ViewCanvas<ImageElement> view = EventManager.getInstance().getSelectedViewPane();
        if (view != null) {
            view.getGraphicManager().setCreateGraphic(CALIBRATION_LINE_GRAPHIC);
            ImageViewerPlugin container = WinUtil.getParentOfClass(view.getJComponent(), ImageViewerPlugin.class);
            if (container != null) {
                final ViewerToolBar toolBar = container.getViewerToolBar();
                if (toolBar != null) {
                    String cmd = ActionW.MEASURE.cmd();
                    if (!toolBar.isCommandActive(cmd)) {
                        MouseActions mouseActions = EventManager.getInstance().getMouseActions();
                        mouseActions.setAction(MouseActions.LEFT, cmd);
                        container.setMouseActions(mouseActions);
                        toolBar.changeButtonState(MouseActions.LEFT, cmd);
                    }
                }
            }
        }
    }
}
