package org.weasis.acquire.dockable.components.actions.meta;

import java.awt.BorderLayout;
import java.awt.GridLayout;

import javax.swing.JPanel;

import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireImageValues;
import org.weasis.acquire.explorer.gui.central.meta.panel.AcquireMetadataPanel;
import org.weasis.acquire.explorer.gui.central.meta.panel.imp.AcquireGlobalMetaPanel;

public class MetadataPanel extends AbstractAcquireActionPanel {
    private static final long serialVersionUID = -1474114784513035056L;

    private AcquireMetadataPanel globalInfoPanel = new AcquireGlobalMetaPanel("Global");
    private org.weasis.acquire.explorer.gui.central.meta.panel.imp.AcquireSerieMetaPanel serieInfoPanel =
        new org.weasis.acquire.explorer.gui.central.meta.panel.imp.AcquireSerieMetaPanel(null);
    private AcquireMetadataPanel imageInfoPanel =
        new org.weasis.acquire.explorer.gui.central.meta.panel.imp.AcquireImageMetaPanel("Image");

    private JPanel content = new JPanel(new GridLayout(3, 1));

    public MetadataPanel() {
        super();
        setLayout(new BorderLayout());

        add(content, BorderLayout.NORTH);

        content.add(globalInfoPanel);
        content.add(serieInfoPanel);
        content.add(imageInfoPanel);
    }

    @Override
    public boolean needValidationPanel() {
        return false;
    }

    @Override
    public void initValues(AcquireImageInfo info, AcquireImageValues values) {
        serieInfoPanel.setSerie(info.getSerie());
        imageInfoPanel.setImageInfo(info);

        repaint();
    }
}
