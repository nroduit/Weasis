package org.weasis.dicom.explorer.pref.download;

import java.awt.FlowLayout;

import javax.swing.JCheckBox;

import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.service.BundleTools;
import org.weasis.dicom.explorer.Messages;

public class SeriesDownloadPrefView extends AbstractItemDialogPage {

    private JCheckBox downloadImmediatelyCheckbox = new JCheckBox(
        Messages.getString("SeriesDownloadPrefView.downloadImmediatelyCheckbox"));

    public SeriesDownloadPrefView() {
        super(Messages.getString("SeriesDownloadPrefView.title"));
        setLayout(new FlowLayout(FlowLayout.LEFT));
        add(downloadImmediatelyCheckbox);
        resetoDefaultValues();
    }

    @Override
    public void resetoDefaultValues() {
        boolean downloadImmediately = SeriesDownloadPrefUtils.downloadImmediately();
        downloadImmediatelyCheckbox.setSelected(downloadImmediately);
    }

    @Override
    public void closeAdditionalWindow() {
        boolean downloadImmediately = downloadImmediatelyCheckbox.isSelected();
        SeriesDownloadPrefUtils.downloadImmediately(downloadImmediately);
        BundleTools.saveSystemPreferences();
    }

}
