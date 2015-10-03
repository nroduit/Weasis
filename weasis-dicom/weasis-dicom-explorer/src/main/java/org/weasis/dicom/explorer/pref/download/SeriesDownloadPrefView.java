package org.weasis.dicom.explorer.pref.download;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.service.BundleTools;
import org.weasis.dicom.explorer.Messages;

public class SeriesDownloadPrefView extends AbstractItemDialogPage {
    public static final String DOWNLOAD_IMMEDIATELY = "weasis.download.immediately"; //$NON-NLS-1$

    private JCheckBox downloadImmediatelyCheckbox =
        new JCheckBox(Messages.getString("SeriesDownloadPrefView.downloadImmediatelyCheckbox")); //$NON-NLS-1$

    public SeriesDownloadPrefView() {
        super(Messages.getString("SeriesDownloadPrefView.title")); //$NON-NLS-1$
        setBorder(new EmptyBorder(15, 10, 10, 10));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder(null, org.weasis.core.ui.Messages.getString("SeriesDownloadPrefView.download"), //$NON-NLS-1$
            TitledBorder.LEADING, TitledBorder.TOP, null, null));
        add(panel);
        GridBagLayout gbl_panel = new GridBagLayout();
        panel.setLayout(gbl_panel);

        Box verticalBox = Box.createVerticalBox();
        GridBagConstraints gbc_verticalBox = new GridBagConstraints();
        gbc_verticalBox.weighty = 10.0;
        gbc_verticalBox.weightx = 1.0;
        gbc_verticalBox.insets = new Insets(0, 0, 5, 0);
        gbc_verticalBox.fill = GridBagConstraints.BOTH;
        gbc_verticalBox.anchor = GridBagConstraints.NORTHWEST;
        gbc_verticalBox.gridx = 0;
        gbc_verticalBox.gridy = 1;
        panel.add(verticalBox, gbc_verticalBox);
        GridBagConstraints gbc_downloadImmediatelyCheckbox = new GridBagConstraints();
        gbc_downloadImmediatelyCheckbox.anchor = GridBagConstraints.LINE_START;
        gbc_downloadImmediatelyCheckbox.insets = new Insets(0, 2, 5, 5);
        gbc_downloadImmediatelyCheckbox.gridx = 0;
        gbc_downloadImmediatelyCheckbox.gridy = 0;
        panel.add(downloadImmediatelyCheckbox, gbc_downloadImmediatelyCheckbox);

        downloadImmediatelyCheckbox
            .setSelected(BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(DOWNLOAD_IMMEDIATELY, true));

        JPanel panel_2 = new JPanel();
        FlowLayout flowLayout_1 = (FlowLayout) panel_2.getLayout();
        flowLayout_1.setHgap(10);
        flowLayout_1.setAlignment(FlowLayout.RIGHT);
        flowLayout_1.setVgap(7);
        add(panel_2);

        JButton btnNewButton = new JButton(org.weasis.core.ui.Messages.getString("restore.values")); //$NON-NLS-1$
        panel_2.add(btnNewButton);
        btnNewButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetoDefaultValues();
            }
        });
    }

    @Override
    public void resetoDefaultValues() {
        BundleTools.SYSTEM_PREFERENCES.resetProperty(DOWNLOAD_IMMEDIATELY, Boolean.TRUE.toString());

        downloadImmediatelyCheckbox
            .setSelected(BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(DOWNLOAD_IMMEDIATELY, true));
    }

    @Override
    public void closeAdditionalWindow() {

        BundleTools.SYSTEM_PREFERENCES.putBooleanProperty(DOWNLOAD_IMMEDIATELY,
            downloadImmediatelyCheckbox.isSelected());
        BundleTools.saveSystemPreferences();
    }

}
