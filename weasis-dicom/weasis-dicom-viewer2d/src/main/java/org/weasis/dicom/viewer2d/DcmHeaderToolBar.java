package org.weasis.dicom.viewer2d;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.explorer.DicomFieldsView;

public class DcmHeaderToolBar<DicomImageElement> extends WtoolBar {

    public DcmHeaderToolBar(int index) {
        super(Messages.getString("DcmHeaderToolBar.title"), index); //$NON-NLS-1$

        final JButton metaButton =
            new JButton(new ImageIcon(ImageViewerPlugin.class.getResource("/icon/32x32/dcm-header.png"))); //$NON-NLS-1$
        metaButton.setToolTipText(ActionW.SHOW_HEADER.getTitle());
        metaButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                ImageViewerPlugin<?> container = EventManager.getInstance().getSelectedView2dContainer();
                displayHeader(container);
            }
        });
        add(metaButton);
        ActionState headerAction = EventManager.getInstance().getAction(ActionW.SHOW_HEADER);
        if (headerAction != null) {
            headerAction.registerActionState(metaButton);
        }
    }

    public static void displayHeader(ImageViewerPlugin<?> container) {
        if (container != null) {
            DefaultView2d<?> selView = container.getSelectedImagePane();
            if (selView != null) {
                ImageElement img = selView.getImage();
                if (img != null) {
                    JFrame frame = new JFrame(org.weasis.dicom.explorer.Messages.getString("DicomExplorer.dcmInfo")); //$NON-NLS-1$
                    frame.setSize(500, 630);
                    DicomFieldsView view = new DicomFieldsView();
                    view.changingViewContentEvent(new SeriesViewerEvent(container, selView.getSeries(), img,
                        EVENT.SELECT));
                    JPanel panel = new JPanel();
                    panel.setLayout(new BorderLayout());
                    panel.add(view);
                    frame.getContentPane().add(panel);
                    frame.setAlwaysOnTop(true);
                    JMVUtils.showCenterScreen(frame, container);
                }
            }
        }
    }
}
