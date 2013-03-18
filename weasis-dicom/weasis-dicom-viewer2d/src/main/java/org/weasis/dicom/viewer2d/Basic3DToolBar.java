package org.weasis.dicom.viewer2d;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.mpr.MPRFactory;

public class Basic3DToolBar<DicomImageElement> extends WtoolBar {
    private final Logger LOGGER = LoggerFactory.getLogger(Basic3DToolBar.class);

    public Basic3DToolBar(int index) {
        super("Basic 3D Tools", TYPE.tool, index);

        final JButton mprButton = new JButton(new ImageIcon(Basic3DToolBar.class.getResource("/icon/32x32/mpr.png")));//$NON-NLS-1$
        mprButton.setToolTipText("Build Orthogonal MPR from the selected view");
        mprButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                EventManager eventManager = EventManager.getInstance();
                ImageViewerPlugin container = eventManager.getSelectedView2dContainer();
                if (container instanceof View2dContainer) {
                    DefaultView2d selView = container.getSelectedImagePane();
                    if (selView != null) {
                        MediaSeries s = selView.getSeries();
                        if (s != null && s.size(null) >= 5) {
                            DataExplorerModel model = (DataExplorerModel) s.getTagValue(TagW.ExplorerModel);
                            if (model instanceof DicomModel) {
                                ViewerPluginBuilder.openSequenceInPlugin(new MPRFactory(), s, model, false, false);
                            }
                        }
                    }
                }
            }
        });
        add(mprButton);

        final JButton mipButton = new JButton(new ImageIcon(Basic3DToolBar.class.getResource("/icon/32x32/mip.png"))); //$NON-NLS-1$
        mipButton.setToolTipText("Build MIP from the selected view");
        mipButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                EventManager eventManager = EventManager.getInstance();
                ImageViewerPlugin container = eventManager.getSelectedView2dContainer();
                if (container instanceof View2dContainer) {
                    DefaultView2d selView = container.getSelectedImagePane();
                    if (selView != null) {
                        MediaSeries s = selView.getSeries();
                        if (s != null && s.size(null) > 2) {
                            container.setSelectedAndGetFocus();
                            MipView newView2d = new MipView(eventManager);
                            newView2d.registerDefaultListeners();
                            newView2d.setMIPSeries(s, null);
                            container.replaceView(selView, newView2d);
                            newView2d.applyMipParameters();
                            JDialog dialog = MipPopup.buildDialog(newView2d);
                            dialog.pack();
                            JMVUtils.showCenterScreen(dialog);
                        }
                    }
                }
            }
        });
        add(mipButton);

        // Attach 3D functions to the SCROLL_SERIES actions
        ActionState scrollAction = EventManager.getInstance().getAction(ActionW.SCROLL_SERIES);
        if (scrollAction != null) {
            scrollAction.registerActionState(mprButton);
            scrollAction.registerActionState(mipButton);
        }
    }
}
