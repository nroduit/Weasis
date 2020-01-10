/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.explorer.pref.download;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

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

@SuppressWarnings("serial")
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
        GridBagLayout gblPanel = new GridBagLayout();
        panel.setLayout(gblPanel);

        Box verticalBox = Box.createVerticalBox();
        GridBagConstraints gbcVerticalBox = new GridBagConstraints();
        gbcVerticalBox.weighty = 10.0;
        gbcVerticalBox.weightx = 1.0;
        gbcVerticalBox.insets = new Insets(0, 0, 5, 0);
        gbcVerticalBox.fill = GridBagConstraints.BOTH;
        gbcVerticalBox.anchor = GridBagConstraints.NORTHWEST;
        gbcVerticalBox.gridx = 0;
        gbcVerticalBox.gridy = 1;
        panel.add(verticalBox, gbcVerticalBox);
        GridBagConstraints gbcDownloadImmediatelyCheckbox = new GridBagConstraints();
        gbcDownloadImmediatelyCheckbox.anchor = GridBagConstraints.LINE_START;
        gbcDownloadImmediatelyCheckbox.insets = new Insets(0, 2, 5, 5);
        gbcDownloadImmediatelyCheckbox.gridx = 0;
        gbcDownloadImmediatelyCheckbox.gridy = 0;
        panel.add(downloadImmediatelyCheckbox, gbcDownloadImmediatelyCheckbox);

        downloadImmediatelyCheckbox
            .setSelected(BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(DOWNLOAD_IMMEDIATELY, true));

        JPanel panel2 = new JPanel();
        FlowLayout flowLayout1 = (FlowLayout) panel2.getLayout();
        flowLayout1.setHgap(10);
        flowLayout1.setAlignment(FlowLayout.RIGHT);
        flowLayout1.setVgap(7);
        add(panel2);

        JButton btnNewButton = new JButton(org.weasis.core.ui.Messages.getString("restore.values")); //$NON-NLS-1$
        panel2.add(btnNewButton);
        btnNewButton.addActionListener(e -> resetoDefaultValues());
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
