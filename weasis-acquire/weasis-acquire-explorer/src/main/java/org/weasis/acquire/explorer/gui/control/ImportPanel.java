package org.weasis.acquire.explorer.gui.control;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JPanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.gui.central.ImageGroupPane;
import org.weasis.acquire.explorer.gui.dialog.AcquireImportDialog;
import org.weasis.acquire.explorer.gui.list.AcquireThumbnailListPane;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.util.FontTools;

public class ImportPanel extends JPanel {
    private static final long serialVersionUID = -8658686020451614960L;

    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    private JButton importBtn = new JButton("Import");

    public ImportPanel(AcquireThumbnailListPane<MediaElement<?>> mainPanel, ImageGroupPane centralPane) {
        importBtn.setPreferredSize(new Dimension(150, 40));
        importBtn.setFont(FontTools.getFont12Bold());

        importBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                List<ImageElement> selected = AcquireManager.toImageElement(mainPanel.getSelectedValuesList());
                if (!selected.isEmpty()) {
                    AcquireImportDialog dialog = new AcquireImportDialog(mainPanel, selected);
                    JMVUtils.showCenterScreen(dialog, WinUtil.getParentWindow(mainPanel));
                }
            }
        });
        add(importBtn);
    }

}
