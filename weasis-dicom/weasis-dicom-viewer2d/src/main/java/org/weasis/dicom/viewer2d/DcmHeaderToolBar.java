package org.weasis.dicom.viewer2d;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ImageIcon;
import javax.swing.JButton;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
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
                DicomFieldsView.displayHeader(EventManager.getInstance().getSelectedView2dContainer());
            }
        });
        add(metaButton);
        ActionState headerAction = EventManager.getInstance().getAction(ActionW.SHOW_HEADER);
        if (headerAction != null) {
            headerAction.registerActionState(metaButton);
        }
    }

}
