package org.weasis.dicom.viewer2d;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ImageIcon;
import javax.swing.JButton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
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

    public Basic3DToolBar() {
        super("Basic 3D Tools", TYPE.tool);

        final JButton mprButton = new JButton("MPR");
        mprButton.setToolTipText("Build Orthogonal MPR from the selected view");
        mprButton.setIcon(new ImageIcon(MediaSeries.class.getResource("/icon/22x22/dicom-3d.png"))); //$NON-NLS-1$
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

        final JButton mipButton = new JButton("MIP");
        mipButton.setToolTipText("Build MIP from the selected view");
        mipButton.setIcon(new ImageIcon(MediaSeries.class.getResource("/icon/22x22/dicom-3d.png"))); //$NON-NLS-1$
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
                            newView2d.setSeries(s);
                            container.replaceView(selView, newView2d);
                            newView2d.applyMipParameters();
                        }
                    }
                }
            }
        });
        add(mipButton);

    }

}
